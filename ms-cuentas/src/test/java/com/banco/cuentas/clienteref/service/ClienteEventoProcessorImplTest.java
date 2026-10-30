package com.banco.cuentas.clienteref.service;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.entity.EventoProcesado;
import com.banco.cuentas.clienteref.event.ClienteEventoRecibido;
import com.banco.cuentas.clienteref.event.TipoEventoCliente;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.clienteref.repository.EventoProcesadoRepository;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Procesador de eventos de cliente")
class ClienteEventoProcessorImplTest {

    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");
    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @Mock
    private ClienteRefRepository clienteRefRepository;

    @Mock
    private EventoProcesadoRepository eventoProcesadoRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    private ClienteEventoProcessorImpl processor;

    @BeforeEach
    void crearProcessor() {
        Clock reloj = Clock.fixed(T0.plusSeconds(3600), ZoneOffset.UTC);
        processor = new ClienteEventoProcessorImpl(clienteRefRepository, eventoProcesadoRepository,
                cuentaRepository, reloj);
    }

    private static ClienteEventoRecibido evento(TipoEventoCliente tipo, String nombre, boolean estado, Instant fecha) {
        return new ClienteEventoRecibido(UUID.randomUUID(), tipo, fecha, CLIENTE_ID, nombre, estado);
    }

    @Test
    @DisplayName("CLIENTE_CREADO de un cliente nuevo crea la réplica y registra el evento")
    void procesar_cuandoClienteCreadoNuevo_debeCrearReplicaYRegistrarEvento() {
        // Given
        ClienteEventoRecibido evento = evento(TipoEventoCliente.CLIENTE_CREADO, "Jose Lema", true, T0);
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);
        when(clienteRefRepository.findById(CLIENTE_ID)).thenReturn(Optional.empty());

        // When
        processor.procesar(evento);

        // Then
        ArgumentCaptor<ClienteRef> cliente = ArgumentCaptor.forClass(ClienteRef.class);
        verify(clienteRefRepository).save(cliente.capture());
        assertThat(cliente.getValue().getClienteId()).isEqualTo(CLIENTE_ID);
        assertThat(cliente.getValue().getNombre()).isEqualTo("Jose Lema");
        assertThat(cliente.getValue().estaActivo()).isTrue();

        ArgumentCaptor<EventoProcesado> procesado = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(eventoProcesadoRepository).save(procesado.capture());
        assertThat(procesado.getValue().getEventId()).isEqualTo(evento.eventId());
    }

    @Test
    @DisplayName("CLIENTE_ACTUALIZADO más reciente aplica nombre y estado")
    void procesar_cuandoActualizacionReciente_debeAplicarCambios() {
        // Given
        ClienteRef existente = ClienteRef.crear(CLIENTE_ID, "Jose Lema", true, T0);
        ClienteEventoRecibido evento = evento(TipoEventoCliente.CLIENTE_ACTUALIZADO, "Jose A. Lema", false,
                T0.plusSeconds(60));
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);
        when(clienteRefRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(existente));

        // When
        processor.procesar(evento);

        // Then
        assertThat(existente.getNombre()).isEqualTo("Jose A. Lema");
        assertThat(existente.estaActivo()).isFalse();
        verify(eventoProcesadoRepository).save(any(EventoProcesado.class));
    }

    @Test
    @DisplayName("Un evento ya procesado (duplicado) no produce cambios")
    void procesar_cuandoEventoDuplicado_noDebeHacerNada() {
        // Given
        ClienteEventoRecibido evento = evento(TipoEventoCliente.CLIENTE_CREADO, "Jose Lema", true, T0);
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(true);

        // When
        processor.procesar(evento);

        // Then
        verify(clienteRefRepository, never()).findById(any());
        verify(clienteRefRepository, never()).save(any());
        verify(eventoProcesadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un evento fuera de orden no cambia la réplica pero queda registrado")
    void procesar_cuandoEventoFueraDeOrden_noDebeCambiarReplicaPeroSiRegistrarlo() {
        // Given
        ClienteRef existente = ClienteRef.crear(CLIENTE_ID, "Jose Lema", true, T0);
        ClienteEventoRecibido viejo = evento(TipoEventoCliente.CLIENTE_ACTUALIZADO, "Nombre Viejo", false,
                T0.minusSeconds(60));
        when(eventoProcesadoRepository.existsById(viejo.eventId())).thenReturn(false);
        when(clienteRefRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(existente));

        // When
        processor.procesar(viejo);

        // Then
        assertThat(existente.getNombre()).isEqualTo("Jose Lema");
        assertThat(existente.estaActivo()).isTrue();
        verify(eventoProcesadoRepository).save(any(EventoProcesado.class));
    }

    @Test
    @DisplayName("CLIENTE_ELIMINADO deja inactivo al cliente y desactiva sus cuentas")
    void procesar_cuandoClienteEliminado_debeInactivarClienteYDesactivarCuentas() {
        // Given
        ClienteRef existente = ClienteRef.crear(CLIENTE_ID, "Jose Lema", true, T0);
        Cuenta ahorros = Cuenta.abrir("478758", TipoCuenta.AHORROS, new BigDecimal("2000"), true, existente);
        Cuenta corriente = Cuenta.abrir("585545", TipoCuenta.CORRIENTE, new BigDecimal("1000"), true, existente);
        ClienteEventoRecibido evento = evento(TipoEventoCliente.CLIENTE_ELIMINADO, "Jose Lema", false,
                T0.plusSeconds(60));
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);
        when(clienteRefRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(existente));
        when(cuentaRepository.findByClienteClienteId(CLIENTE_ID)).thenReturn(List.of(ahorros, corriente));

        // When
        processor.procesar(evento);

        // Then
        assertThat(existente.estaActivo()).isFalse();
        assertThat(ahorros.isEstado()).isFalse();
        assertThat(corriente.isEstado()).isFalse();
    }

    @Test
    @DisplayName("CLIENTE_ELIMINADO deja inactivo al cliente aunque el evento traiga estado true")
    void procesar_cuandoClienteEliminadoConEstadoTrue_debeQuedarInactivo() {
        // Given
        ClienteRef existente = ClienteRef.crear(CLIENTE_ID, "Jose Lema", true, T0);
        ClienteEventoRecibido evento = evento(TipoEventoCliente.CLIENTE_ELIMINADO, "Jose Lema", true,
                T0.plusSeconds(60));
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);
        when(clienteRefRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(existente));
        when(cuentaRepository.findByClienteClienteId(CLIENTE_ID)).thenReturn(List.of());

        // When
        processor.procesar(evento);

        // Then
        assertThat(existente.estaActivo()).isFalse();
    }
}

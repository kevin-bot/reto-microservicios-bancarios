package com.banco.cuentas.cuenta.service;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.cuenta.dto.CuentaCreateRequest;
import com.banco.cuentas.cuenta.dto.CuentaPatchRequest;
import com.banco.cuentas.cuenta.dto.CuentaResponse;
import com.banco.cuentas.cuenta.dto.CuentaUpdateRequest;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.exception.ClienteNoDisponibleException;
import com.banco.cuentas.cuenta.exception.CuentaNoEncontradaException;
import com.banco.cuentas.cuenta.exception.NumeroCuentaDuplicadoException;
import com.banco.cuentas.cuenta.mapper.CuentaMapper;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.shared.dto.PaginaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de cuentas")
class CuentaServiceImplTest {

    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");
    private static final UUID JOSE = UUID.randomUUID();

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private ClienteRefRepository clienteRefRepository;

    @Spy
    private CuentaMapper cuentaMapper = Mappers.getMapper(CuentaMapper.class);

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    private static ClienteRef jose(boolean activo) {
        return ClienteRef.crear(JOSE, "Jose Lema", activo, T0);
    }

    private static CuentaCreateRequest solicitud478758() {
        return new CuentaCreateRequest("478758", TipoCuenta.AHORROS, new BigDecimal("2000"), null, JOSE);
    }

    private static Cuenta cuenta478758(ClienteRef cliente) {
        return Cuenta.abrir("478758", TipoCuenta.AHORROS, new BigDecimal("2000"), true, cliente);
    }

    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        @DisplayName("Abre la cuenta activa con saldo disponible igual al inicial")
        void crear_cuandoDatosValidos_debeGuardarCuentaActiva() {
            // Given
            when(cuentaRepository.existsByNumeroCuenta("478758")).thenReturn(false);
            when(clienteRefRepository.findById(JOSE)).thenReturn(Optional.of(jose(true)));
            when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

            // When
            CuentaResponse respuesta = cuentaService.crear(solicitud478758());

            // Then
            ArgumentCaptor<Cuenta> guardada = ArgumentCaptor.forClass(Cuenta.class);
            verify(cuentaRepository).save(guardada.capture());
            assertThat(guardada.getValue().isEstado()).isTrue();
            assertThat(respuesta.numeroCuenta()).isEqualTo("478758");
            assertThat(respuesta.saldoDisponible()).isEqualByComparingTo("2000");
            assertThat(respuesta.clienteId()).isEqualTo(JOSE);
            assertThat(respuesta.nombreCliente()).isEqualTo("Jose Lema");
        }

        @Test
        @DisplayName("Rechaza un número de cuenta existente")
        void crear_cuandoNumeroDuplicado_debeLanzarNumeroCuentaDuplicado() {
            // Given
            when(cuentaRepository.existsByNumeroCuenta("478758")).thenReturn(true);

            // When / Then
            assertThatThrownBy(() -> cuentaService.crear(solicitud478758()))
                    .isInstanceOf(NumeroCuentaDuplicadoException.class);
            verify(cuentaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rechaza un cliente que no está en la réplica")
        void crear_cuandoClienteInexistente_debeLanzarClienteNoDisponible() {
            // Given
            when(cuentaRepository.existsByNumeroCuenta("478758")).thenReturn(false);
            when(clienteRefRepository.findById(JOSE)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> cuentaService.crear(solicitud478758()))
                    .isInstanceOf(ClienteNoDisponibleException.class);
            verify(cuentaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rechaza un cliente inactivo")
        void crear_cuandoClienteInactivo_debeLanzarClienteNoDisponible() {
            // Given
            when(cuentaRepository.existsByNumeroCuenta("478758")).thenReturn(false);
            when(clienteRefRepository.findById(JOSE)).thenReturn(Optional.of(jose(false)));

            // When / Then
            assertThatThrownBy(() -> cuentaService.crear(solicitud478758()))
                    .isInstanceOf(ClienteNoDisponibleException.class);
        }
    }

    @Nested
    @DisplayName("consultas")
    class Consultas {

        @Test
        @DisplayName("obtener lanza CuentaNoEncontradaException si no existe")
        void obtener_cuandoNoExiste_debeLanzarCuentaNoEncontrada() {
            // Given
            when(cuentaRepository.findByNumeroCuenta("999999")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> cuentaService.obtener("999999"))
                    .isInstanceOf(CuentaNoEncontradaException.class)
                    .hasMessageContaining("999999");
        }

        @Test
        @DisplayName("listar devuelve una página de cuentas")
        @SuppressWarnings("unchecked")
        void listar_cuandoFiltroPorCliente_debeDevolverPagina() {
            // Given
            PageRequest pagina = PageRequest.of(0, 20);
            when(cuentaRepository.findAll(any(Specification.class), eq(pagina)))
                    .thenReturn(new PageImpl<>(List.of(cuenta478758(jose(true))), pagina, 1));

            // When
            PaginaResponse<CuentaResponse> resultado = cuentaService.listar(JOSE, pagina);

            // Then
            assertThat(resultado.contenido()).extracting(CuentaResponse::numeroCuenta).containsExactly("478758");
            assertThat(resultado.totalElementos()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("actualizar")
    class Actualizar {

        @Test
        @DisplayName("PUT cambia tipo y estado")
        void actualizar_cuandoDatosValidos_debeCambiarTipoYEstado() {
            // Given
            Cuenta cuenta = cuenta478758(jose(true));
            when(cuentaRepository.findByNumeroCuenta("478758")).thenReturn(Optional.of(cuenta));

            // When
            CuentaResponse respuesta = cuentaService.actualizar("478758",
                    new CuentaUpdateRequest(TipoCuenta.CORRIENTE, false));

            // Then
            assertThat(respuesta.tipo()).isEqualTo(TipoCuenta.CORRIENTE);
            assertThat(respuesta.estado()).isFalse();
            assertThat(respuesta.saldoDisponible()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("PATCH solo con estado conserva el tipo")
        void actualizarParcial_cuandoSoloEstado_debeConservarTipo() {
            // Given
            Cuenta cuenta = cuenta478758(jose(true));
            when(cuentaRepository.findByNumeroCuenta("478758")).thenReturn(Optional.of(cuenta));

            // When
            CuentaResponse respuesta = cuentaService.actualizarParcial("478758", new CuentaPatchRequest(null, false));

            // Then
            assertThat(respuesta.estado()).isFalse();
            assertThat(respuesta.tipo()).isEqualTo(TipoCuenta.AHORROS);
        }

        @Test
        @DisplayName("No se puede reactivar una cuenta si el cliente está inactivo")
        void actualizarParcial_cuandoActivarConClienteInactivo_debeLanzarClienteNoDisponible() {
            // Given
            ClienteRef cliente = jose(true);
            Cuenta cuenta = cuenta478758(cliente);
            cuenta.desactivar();
            cliente.aplicar("Jose Lema", false, T0.plusSeconds(1));
            when(cuentaRepository.findByNumeroCuenta("478758")).thenReturn(Optional.of(cuenta));

            // When / Then
            assertThatThrownBy(() -> cuentaService.actualizarParcial("478758", new CuentaPatchRequest(null, true)))
                    .isInstanceOf(ClienteNoDisponibleException.class);
        }

        @Test
        @DisplayName("Actualizar una cuenta inexistente lanza CuentaNoEncontradaException")
        void actualizar_cuandoNoExiste_debeLanzarCuentaNoEncontrada() {
            // Given
            when(cuentaRepository.findByNumeroCuenta("999999")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> cuentaService.actualizar("999999",
                    new CuentaUpdateRequest(TipoCuenta.AHORROS, true)))
                    .isInstanceOf(CuentaNoEncontradaException.class);
        }
    }
}

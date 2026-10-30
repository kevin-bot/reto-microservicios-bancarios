package com.banco.cuentas.clienteref;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.clienteref.repository.EventoProcesadoRepository;
import com.banco.cuentas.clienteref.service.ClienteEventoProcessor;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.shared.config.RabbitConfig;
import com.banco.cuentas.support.ClientesDePrueba;
import com.banco.cuentas.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integración real con RabbitMQ y PostgreSQL: réplica de clientes, idempotencia, orden de
 * eventos, desactivación de cuentas, DLQ y reintentos (US1).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@DisplayName("Consumo de eventos de cliente con RabbitMQ real")
class ClienteEventoListenerIntegrationTest {

    private static final Duration ESPERA_REPLICA = Duration.ofSeconds(5);
    private static final Duration ESPERA_REINTENTOS = Duration.ofSeconds(15);
    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ClienteRefRepository clienteRefRepository;

    @Autowired
    private EventoProcesadoRepository eventoProcesadoRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @MockitoSpyBean
    private ClienteEventoProcessor processor;

    @BeforeEach
    void vaciarDlq() {
        amqpAdmin.purgeQueue(RabbitConfig.DLQ_CLIENTES, false);
    }

    private static String eventoJson(UUID eventId, String tipo, Instant fecha, UUID clienteId, String nombre,
                                     boolean estado) {
        return """
                {"eventId":"%s","tipo":"%s","fechaOcurrencia":"%s","clienteId":"%s","nombre":"%s","estado":%s}
                """.formatted(eventId, tipo, fecha, clienteId, nombre, estado);
    }

    private void publicar(String routingKey, String cuerpo) {
        Message mensaje = MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build();
        rabbitTemplate.send(RabbitConfig.CLIENTES_EXCHANGE, routingKey, mensaje);
    }

    private void publicarCreado(UUID clienteId, String nombre, Instant fecha) {
        publicar("cliente.creado", eventoJson(UUID.randomUUID(), "CLIENTE_CREADO", fecha, clienteId, nombre, true));
    }

    /**
     * Hay un solo consumidor y la cola es FIFO: cuando el centinela se procesa, todo lo publicado
     * antes ya fue procesado.
     */
    private void esperarCentinela() {
        UUID centinela = UUID.randomUUID();
        publicarCreado(centinela, "Centinela", T0);
        await().atMost(ESPERA_REPLICA).until(() -> clienteRefRepository.existsById(centinela));
    }

    @Test
    @DisplayName("cliente.creado crea la réplica en menos de 5 s (SC-003)")
    void recibir_cuandoClienteCreado_debeCrearReplica() {
        // Given
        UUID clienteId = UUID.randomUUID();

        // When
        publicarCreado(clienteId, "Jose Lema", T0);

        // Then
        await().atMost(ESPERA_REPLICA).untilAsserted(() -> {
            ClienteRef cliente = clienteRefRepository.findById(clienteId).orElseThrow();
            assertThat(cliente.getNombre()).isEqualTo("Jose Lema");
            assertThat(cliente.estaActivo()).isTrue();
        });
    }

    @Test
    @DisplayName("Un evento duplicado se procesa una sola vez")
    void recibir_cuandoEventoDuplicado_debeProcesarseUnaSolaVez() {
        // Given
        UUID clienteId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        publicar("cliente.creado", eventoJson(eventId, "CLIENTE_CREADO", T0, clienteId, "Original", true));
        await().atMost(ESPERA_REPLICA).until(() -> clienteRefRepository.existsById(clienteId));

        // When: mismo eventId con otro contenido
        publicar("cliente.actualizado",
                eventoJson(eventId, "CLIENTE_ACTUALIZADO", T0.plusSeconds(60), clienteId, "Duplicado", false));
        esperarCentinela();

        // Then
        ClienteRef cliente = clienteRefRepository.findById(clienteId).orElseThrow();
        assertThat(cliente.getNombre()).isEqualTo("Original");
        assertThat(cliente.estaActivo()).isTrue();
        assertThat(eventoProcesadoRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("Un evento más antiguo que el último aplicado se descarta")
    void recibir_cuandoEventoFueraDeOrden_debeDescartarse() {
        // Given
        UUID clienteId = UUID.randomUUID();
        publicarCreado(clienteId, "Nombre Inicial", T0);
        publicar("cliente.actualizado", eventoJson(UUID.randomUUID(), "CLIENTE_ACTUALIZADO",
                T0.plusSeconds(120), clienteId, "Nombre Nuevo", true));

        // When: llega tarde un evento intermedio
        publicar("cliente.actualizado", eventoJson(UUID.randomUUID(), "CLIENTE_ACTUALIZADO",
                T0.plusSeconds(60), clienteId, "Nombre Intermedio", false));
        esperarCentinela();

        // Then
        ClienteRef cliente = clienteRefRepository.findById(clienteId).orElseThrow();
        assertThat(cliente.getNombre()).isEqualTo("Nombre Nuevo");
        assertThat(cliente.estaActivo()).isTrue();
    }

    @Test
    @DisplayName("cliente.eliminado deja inactivo al cliente y desactiva sus cuentas")
    void recibir_cuandoClienteEliminado_debeDesactivarSusCuentas() {
        // Given
        UUID clienteId = UUID.randomUUID();
        publicarCreado(clienteId, "Juan Osorio", T0);
        await().atMost(ESPERA_REPLICA).until(() -> clienteRefRepository.existsById(clienteId));
        ClienteRef cliente = clienteRefRepository.findById(clienteId).orElseThrow();
        String numeroCuenta = ClientesDePrueba.numeroCuentaAleatorio();
        cuentaRepository.save(Cuenta.abrir(numeroCuenta, TipoCuenta.AHORROS, new BigDecimal("150"), true, cliente));

        // When
        publicar("cliente.eliminado", eventoJson(UUID.randomUUID(), "CLIENTE_ELIMINADO",
                T0.plusSeconds(60), clienteId, "Juan Osorio", false));

        // Then
        await().atMost(ESPERA_REPLICA).untilAsserted(() -> {
            assertThat(clienteRefRepository.findById(clienteId).orElseThrow().estaActivo()).isFalse();
            assertThat(cuentaRepository.findByNumeroCuenta(numeroCuenta).orElseThrow().isEstado()).isFalse();
        });
    }

    @Test
    @DisplayName("Un JSON inválido va a la DLQ al primer intento, sin reintentos")
    void recibir_cuandoJsonInvalido_debeIrALaDlqSinReintentos() {
        // When
        publicar("cliente.creado", "{ esto no es json");

        // Then
        await().atMost(ESPERA_REPLICA).untilAsserted(() ->
                assertThat(rabbitTemplate.receive(RabbitConfig.DLQ_CLIENTES)).isNotNull());
    }

    @Test
    @DisplayName("Un error transitorio se intenta 4 veces y luego el mensaje va a la DLQ (FR-004)")
    void recibir_cuandoErrorTransitorio_debeReintentarYEnviarALaDlq() {
        // Given
        UUID clienteId = UUID.randomUUID();
        doThrow(new IllegalStateException("Base de datos no disponible"))
                .when(processor).procesar(argThat(evento -> evento != null && clienteId.equals(evento.clienteId())));

        // When
        publicarCreado(clienteId, "Cliente con Falla", T0);

        // Then
        await().atMost(ESPERA_REINTENTOS).untilAsserted(() ->
                assertThat(rabbitTemplate.receive(RabbitConfig.DLQ_CLIENTES)).isNotNull());
        verify(processor, times(4)).procesar(argThat(evento -> evento != null && clienteId.equals(evento.clienteId())));
        assertThat(clienteRefRepository.existsById(clienteId)).isFalse();
    }
}

package com.banco.clientes.cliente.event;

import com.banco.clientes.shared.config.RabbitConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Publicador de eventos de cliente")
class ClienteEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ClienteEventPublisher publisher;

    private static ClienteEvento evento(TipoEventoCliente tipo) {
        return new ClienteEvento(UUID.randomUUID(), tipo, Instant.now(), UUID.randomUUID(), "Jose Lema", true);
    }

    @Test
    @DisplayName("Envía el evento al exchange de clientes con la routing key de su tipo")
    void publicar_cuandoEventoCreado_debeEnviarAlExchangeConRoutingKey() {
        // Given
        ClienteEvento evento = evento(TipoEventoCliente.CLIENTE_CREADO);

        // When
        publisher.publicar(new ClienteEventoDominio(evento));

        // Then
        verify(rabbitTemplate).convertAndSend(RabbitConfig.CLIENTES_EXCHANGE, "cliente.creado", evento);
    }

    @Test
    @DisplayName("Usa la routing key de eliminación para eventos CLIENTE_ELIMINADO")
    void publicar_cuandoEventoEliminado_debeUsarRoutingKeyEliminado() {
        // Given
        ClienteEvento evento = evento(TipoEventoCliente.CLIENTE_ELIMINADO);

        // When
        publisher.publicar(new ClienteEventoDominio(evento));

        // Then
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.CLIENTES_EXCHANGE), eq("cliente.eliminado"), eq(evento));
    }

    @Test
    @DisplayName("Si el broker no está disponible, no propaga la excepción")
    void publicar_cuandoBrokerNoDisponible_noDebePropagarExcepcion() {
        // Given
        ClienteEvento evento = evento(TipoEventoCliente.CLIENTE_ACTUALIZADO);
        doThrow(new AmqpConnectException(new ConnectException("broker caído")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // When / Then
        assertThatCode(() -> publisher.publicar(new ClienteEventoDominio(evento))).doesNotThrowAnyException();
    }
}

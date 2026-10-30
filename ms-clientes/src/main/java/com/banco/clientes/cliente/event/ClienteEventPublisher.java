package com.banco.clientes.cliente.event;

import com.banco.clientes.shared.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica en RabbitMQ solo después del commit: un cambio revertido nunca genera evento.
 * Si el broker falla, el cambio del cliente ya está confirmado y el evento se pierde (se registra
 * en log). La mejora futura documentada es el patrón Transactional Outbox.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClienteEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(ClienteEventoDominio eventoDominio) {
        ClienteEvento evento = eventoDominio.evento();
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.CLIENTES_EXCHANGE, evento.tipo().getRoutingKey(), evento);
            log.info("Evento {} publicado (eventId={}, clienteId={})",
                    evento.tipo(), evento.eventId(), evento.clienteId());
        } catch (AmqpException ex) {
            log.error("No se pudo publicar el evento {} (eventId={}, clienteId={})",
                    evento.tipo(), evento.eventId(), evento.clienteId(), ex);
        }
    }
}

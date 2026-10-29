package com.banco.clientes.cliente.event;

/**
 * Evento de aplicación (en memoria) que el service publica dentro de la transacción;
 * {@link ClienteEventPublisher} lo envía a RabbitMQ solo después del commit.
 */
public record ClienteEventoDominio(ClienteEvento evento) {
}

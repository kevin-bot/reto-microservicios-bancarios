package com.banco.clientes.cliente.event;

import com.banco.clientes.cliente.entity.Cliente;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje de integración publicado en RabbitMQ (ver contracts/eventos-cliente.md).
 * Solo lleva los datos que necesitan los consumidores: nunca la contraseña ni datos personales.
 */
public record ClienteEvento(
        UUID eventId,
        TipoEventoCliente tipo,
        Instant fechaOcurrencia,
        UUID clienteId,
        String nombre,
        boolean estado) {

    public static ClienteEvento desde(Cliente cliente, TipoEventoCliente tipo) {
        return new ClienteEvento(UUID.randomUUID(), tipo, Instant.now(),
                cliente.getClienteId(), cliente.getNombre(), cliente.isEstado());
    }
}

package com.banco.cuentas.clienteref.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de cliente publicado por ms-clientes (contrato en
 * specs/001-ms-clientes/contracts/eventos-cliente.md). Es una copia local: ms-cuentas no depende
 * de las clases del productor.
 */
public record ClienteEventoRecibido(
        UUID eventId,
        TipoEventoCliente tipo,
        Instant fechaOcurrencia,
        UUID clienteId,
        String nombre,
        boolean estado) {
}

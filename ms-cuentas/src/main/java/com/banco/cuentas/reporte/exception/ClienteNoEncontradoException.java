package com.banco.cuentas.reporte.exception;

import com.banco.cuentas.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

public class ClienteNoEncontradoException extends RecursoNoEncontradoException {

    public ClienteNoEncontradoException(UUID clienteId) {
        super("Cliente no encontrado", "No existe un cliente con id " + clienteId);
    }
}

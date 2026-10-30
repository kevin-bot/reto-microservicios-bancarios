package com.banco.clientes.cliente.exception;

import com.banco.clientes.shared.exception.ReglaNegocioException;

import java.util.UUID;

public class ClienteEliminadoException extends ReglaNegocioException {

    public ClienteEliminadoException(UUID clienteId) {
        super("El cliente " + clienteId + " está eliminado y no admite cambios");
    }
}

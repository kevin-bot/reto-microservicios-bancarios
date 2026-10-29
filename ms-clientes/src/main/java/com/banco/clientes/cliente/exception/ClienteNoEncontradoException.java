package com.banco.clientes.cliente.exception;

import java.util.UUID;

public class ClienteNoEncontradoException extends RuntimeException {

    public ClienteNoEncontradoException(UUID clienteId) {
        super("No existe un cliente con id " + clienteId);
    }
}

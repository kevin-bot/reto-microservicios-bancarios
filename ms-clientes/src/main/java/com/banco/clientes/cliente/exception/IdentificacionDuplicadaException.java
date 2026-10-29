package com.banco.clientes.cliente.exception;

public class IdentificacionDuplicadaException extends RuntimeException {

    public IdentificacionDuplicadaException(String identificacion) {
        super("Ya existe un cliente con identificación " + identificacion);
    }
}

package com.banco.clientes.shared.exception;

/**
 * Base de las violaciones de reglas de negocio. No conoce HTTP: el mapeo a códigos de estado
 * vive únicamente en {@link GlobalExceptionHandler}.
 */
public abstract class ReglaNegocioException extends RuntimeException {

    protected ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}

package com.banco.clientes.shared.exception;

/**
 * Un dato no cumple una invariante de la entidad de dominio.
 */
public class DomainValidationException extends ReglaNegocioException {

    public DomainValidationException(String mensaje) {
        super(mensaje);
    }
}

package com.banco.cuentas.shared.exception;

/**
 * Un dato no cumple una invariante de una entidad de dominio.
 */
public class DomainValidationException extends ReglaNegocioException {

    public DomainValidationException(String mensaje) {
        super("Datos inválidos", mensaje);
    }
}

package com.banco.cuentas.cuenta.exception;

import com.banco.cuentas.shared.exception.ReglaNegocioException;

/**
 * El cliente no existe en la réplica o está inactivo: no puede abrir cuentas ni operar.
 */
public class ClienteNoDisponibleException extends ReglaNegocioException {

    private static final String MENSAJE = "Cliente no disponible";

    public ClienteNoDisponibleException() {
        super(MENSAJE, MENSAJE);
    }
}

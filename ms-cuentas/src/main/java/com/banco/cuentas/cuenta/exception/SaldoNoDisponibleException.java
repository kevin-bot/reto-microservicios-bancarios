package com.banco.cuentas.cuenta.exception;

import com.banco.cuentas.shared.exception.ReglaNegocioException;

/**
 * F3: un movimiento dejaría el saldo por debajo de 0. El mensaje es el exacto que pide el enunciado.
 */
public class SaldoNoDisponibleException extends ReglaNegocioException {

    private static final String MENSAJE = "Saldo no disponible";

    public SaldoNoDisponibleException() {
        super(MENSAJE, MENSAJE);
    }
}

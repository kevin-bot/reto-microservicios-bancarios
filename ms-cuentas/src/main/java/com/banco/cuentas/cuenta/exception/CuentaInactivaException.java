package com.banco.cuentas.cuenta.exception;

import com.banco.cuentas.shared.exception.ReglaNegocioException;

public class CuentaInactivaException extends ReglaNegocioException {

    public CuentaInactivaException(String numeroCuenta) {
        super("Cuenta inactiva", "La cuenta " + numeroCuenta + " está inactiva y no admite movimientos");
    }
}

package com.banco.cuentas.cuenta.exception;

import com.banco.cuentas.shared.exception.RecursoNoEncontradoException;

public class CuentaNoEncontradaException extends RecursoNoEncontradoException {

    public CuentaNoEncontradaException(String numeroCuenta) {
        super("Cuenta no encontrada", "No existe una cuenta con número " + numeroCuenta);
    }
}

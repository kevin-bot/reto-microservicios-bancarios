package com.banco.cuentas.cuenta.exception;

import com.banco.cuentas.shared.exception.ConflictoException;

public class NumeroCuentaDuplicadoException extends ConflictoException {

    public NumeroCuentaDuplicadoException(String numeroCuenta) {
        super("Número de cuenta duplicado", "Ya existe una cuenta con número " + numeroCuenta);
    }
}

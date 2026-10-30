package com.banco.cuentas.movimiento.exception;

import com.banco.cuentas.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

public class MovimientoNoEncontradoException extends RecursoNoEncontradoException {

    public MovimientoNoEncontradoException(UUID movimientoId) {
        super("Movimiento no encontrado", "No existe un movimiento con id " + movimientoId);
    }
}

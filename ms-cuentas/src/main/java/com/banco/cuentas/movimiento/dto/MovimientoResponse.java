package com.banco.cuentas.movimiento.dto;

import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.entity.TipoMovimiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila plana como el ejemplo del caso de uso 5 del enunciado: movimiento más los datos del
 * cliente y de la cuenta.
 */
public record MovimientoResponse(
        UUID movimientoId,
        Instant fecha,
        UUID clienteId,
        String nombreCliente,
        String numeroCuenta,
        TipoCuenta tipoCuenta,
        BigDecimal saldoInicial,
        boolean estadoCuenta,
        TipoMovimiento tipo,
        BigDecimal valor,
        BigDecimal saldo) {
}

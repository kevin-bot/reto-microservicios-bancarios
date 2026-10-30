package com.banco.cuentas.cuenta.dto;

import com.banco.cuentas.cuenta.entity.TipoCuenta;

import java.math.BigDecimal;
import java.util.UUID;

public record CuentaResponse(
        String numeroCuenta,
        TipoCuenta tipo,
        BigDecimal saldoInicial,
        BigDecimal saldoDisponible,
        boolean estado,
        UUID clienteId,
        String nombreCliente) {
}

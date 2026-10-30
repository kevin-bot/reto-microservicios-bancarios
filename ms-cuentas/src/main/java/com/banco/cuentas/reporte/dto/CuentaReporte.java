package com.banco.cuentas.reporte.dto;

import com.banco.cuentas.cuenta.entity.TipoCuenta;

import java.math.BigDecimal;
import java.util.List;

public record CuentaReporte(
        String numeroCuenta,
        TipoCuenta tipo,
        BigDecimal saldoInicial,
        BigDecimal saldoDisponible,
        boolean estado,
        List<MovimientoReporte> movimientos) {
}

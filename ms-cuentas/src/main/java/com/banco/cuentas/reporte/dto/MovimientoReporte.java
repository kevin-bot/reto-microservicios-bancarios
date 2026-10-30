package com.banco.cuentas.reporte.dto;

import com.banco.cuentas.cuenta.entity.TipoMovimiento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MovimientoReporte(
        UUID movimientoId,
        Instant fecha,
        TipoMovimiento tipo,
        BigDecimal valor,
        BigDecimal saldo) {
}

package com.banco.cuentas.reporte.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Estado de cuenta (F4): cliente, rango consultado y sus cuentas con saldos y movimientos del rango.
 */
public record ReporteResponse(
        ClienteResumen cliente,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        List<CuentaReporte> cuentas) {
}

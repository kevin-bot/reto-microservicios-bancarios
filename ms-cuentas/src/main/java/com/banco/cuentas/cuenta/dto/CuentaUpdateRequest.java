package com.banco.cuentas.cuenta.dto;

import com.banco.cuentas.cuenta.entity.TipoCuenta;
import jakarta.validation.constraints.NotNull;

/**
 * Reemplazo completo (PUT). Solo tipo y estado son editables: número, cliente y saldos no (FR-011).
 */
public record CuentaUpdateRequest(
        @NotNull TipoCuenta tipo,
        @NotNull Boolean estado) {
}

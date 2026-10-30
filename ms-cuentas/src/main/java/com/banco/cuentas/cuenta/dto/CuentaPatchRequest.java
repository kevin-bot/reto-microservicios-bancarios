package com.banco.cuentas.cuenta.dto;

import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.shared.validation.AlMenosUnCampo;

/**
 * Actualización parcial (PATCH): los campos omitidos o nulos no cambian.
 */
@AlMenosUnCampo
public record CuentaPatchRequest(
        TipoCuenta tipo,
        Boolean estado) {
}

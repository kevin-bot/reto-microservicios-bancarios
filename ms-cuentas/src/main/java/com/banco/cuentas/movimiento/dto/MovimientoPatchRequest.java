package com.banco.cuentas.movimiento.dto;

import com.banco.cuentas.shared.validation.AlMenosUnCampo;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

@AlMenosUnCampo
public record MovimientoPatchRequest(
        @Digits(integer = 13, fraction = 2, message = "debe tener como máximo 2 decimales")
        BigDecimal valor) {
}

package com.banco.cuentas.movimiento.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Corrección completa (PUT): el único campo editable de un movimiento es su valor (FR-019).
 */
public record MovimientoUpdateRequest(
        @NotNull
        @Digits(integer = 13, fraction = 2, message = "debe tener como máximo 2 decimales")
        BigDecimal valor) {
}

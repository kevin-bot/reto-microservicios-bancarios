package com.banco.cuentas.movimiento.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Valor positivo = depósito; negativo = retiro. La fecha no se recibe: la asigna el sistema.
 */
public record MovimientoCreateRequest(
        @NotBlank String numeroCuenta,
        @NotNull
        @Digits(integer = 13, fraction = 2, message = "debe tener como máximo 2 decimales")
        BigDecimal valor) {
}

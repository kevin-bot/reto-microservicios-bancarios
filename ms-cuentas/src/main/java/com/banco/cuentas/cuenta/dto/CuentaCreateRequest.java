package com.banco.cuentas.cuenta.dto;

import com.banco.cuentas.cuenta.entity.TipoCuenta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CuentaCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{6,20}$", message = "debe tener solo dígitos, entre 6 y 20")
        String numeroCuenta,
        @NotNull TipoCuenta tipo,
        @NotNull
        @DecimalMin(value = "0.00", message = "debe ser mayor o igual a 0")
        @Digits(integer = 13, fraction = 2, message = "debe tener como máximo 2 decimales")
        BigDecimal saldoInicial,
        Boolean estado,
        @NotNull UUID clienteId) {

    /** Si no se envía, la cuenta se abre activa (FR-006). */
    public boolean estadoOPorDefecto() {
        return estado == null || estado;
    }
}

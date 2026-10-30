package com.banco.cuentas.cuenta.entity;

import java.math.BigDecimal;

public enum TipoMovimiento {
    DEPOSITO,
    RETIRO;

    /** El tipo se deriva del signo del valor (FR-014). */
    public static TipoMovimiento desde(BigDecimal valor) {
        return valor.signum() > 0 ? DEPOSITO : RETIRO;
    }
}

package com.banco.cuentas.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Configuración del banco. La zona horaria define los días completos del reporte y del listado
 * de movimientos (research R10).
 */
@ConfigurationProperties("banco")
public record BancoProperties(String zonaHoraria) {

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }
}

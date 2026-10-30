package com.banco.cuentas.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * La fecha de los movimientos la asigna siempre el sistema. Inyectar el reloj permite fijarlo en
 * las pruebas.
 */
@Configuration
public class TiempoConfig {

    @Bean
    public Clock clock(BancoProperties bancoProperties) {
        return Clock.system(bancoProperties.zona());
    }
}

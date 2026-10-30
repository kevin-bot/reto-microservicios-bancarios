package com.banco.cuentas.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI msCuentasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ms-cuentas API")
                .version("1.0.0")
                .description("Cuentas, movimientos y reporte de estado de cuenta. Los errores siguen RFC 7807."));
    }
}

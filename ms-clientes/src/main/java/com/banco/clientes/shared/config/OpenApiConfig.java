package com.banco.clientes.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI msClientesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ms-clientes API")
                .version("1.0.0")
                .description("Gestión de clientes (Persona → Cliente). La contraseña nunca se devuelve "
                        + "y los errores siguen RFC 7807."));
    }
}

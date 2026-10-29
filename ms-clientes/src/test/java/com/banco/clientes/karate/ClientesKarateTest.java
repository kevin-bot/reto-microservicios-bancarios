package com.banco.clientes.karate;

import com.banco.clientes.support.TestcontainersConfig;
import com.intuit.karate.junit5.Karate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Pruebas de API con Karate contra la aplicación real (PostgreSQL y RabbitMQ en Testcontainers).
 * Se ejecutan junto al resto de pruebas con {@code ./mvnw test}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ClientesKarateTest {

    @LocalServerPort
    private int port;

    @Karate.Test
    Karate clientes() {
        System.setProperty("karate.baseUrl", "http://localhost:" + port + "/api");
        return Karate.run("classpath:karate/clientes/clientes.feature");
    }
}

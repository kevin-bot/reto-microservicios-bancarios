package com.banco.cuentas.karate;

import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.support.ClientesDePrueba;
import com.banco.cuentas.support.TestcontainersConfig;
import com.intuit.karate.junit5.Karate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Pruebas de API con Karate contra la aplicación real (PostgreSQL y RabbitMQ en Testcontainers).
 * Los clientes se siembran directamente en la réplica para no depender del consumidor de eventos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CuentasKarateTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ClienteRefRepository clienteRefRepository;

    @Karate.Test
    Karate cuentas() {
        ClientesDePrueba.sembrar(clienteRefRepository);
        System.setProperty("karate.baseUrl", "http://localhost:" + port + "/api");
        System.setProperty("karate.joseId", ClientesDePrueba.JOSE_LEMA.toString());
        System.setProperty("karate.marianelaId", ClientesDePrueba.MARIANELA_MONTALVO.toString());
        System.setProperty("karate.juanId", ClientesDePrueba.JUAN_OSORIO.toString());
        System.setProperty("karate.pruebasId", ClientesDePrueba.CLIENTE_PRUEBAS.toString());
        return Karate.run("classpath:karate/cuentas");
    }
}

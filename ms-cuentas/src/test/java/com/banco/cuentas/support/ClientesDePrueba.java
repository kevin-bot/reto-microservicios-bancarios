package com.banco.cuentas.support;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Siembra la réplica de clientes directamente (sin eventos), para que las pruebas de cuentas,
 * movimientos y reporte no dependan del consumidor de RabbitMQ. Es idempotente porque las pruebas
 * que comparten contexto de Spring comparten la base de datos.
 */
public final class ClientesDePrueba {

    public static final UUID JOSE_LEMA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID MARIANELA_MONTALVO = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID JUAN_OSORIO = UUID.fromString("00000000-0000-0000-0000-000000000003");
    /** Cliente para escenarios de error: no altera las cuentas ni los saldos de los casos de uso. */
    public static final UUID CLIENTE_PRUEBAS = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static final Instant FECHA_SIEMBRA = Instant.parse("2020-01-01T00:00:00Z");

    private ClientesDePrueba() {
    }

    public static void sembrar(ClienteRefRepository repositorio) {
        guardarSiNoExiste(repositorio, JOSE_LEMA, "Jose Lema");
        guardarSiNoExiste(repositorio, MARIANELA_MONTALVO, "Marianela Montalvo");
        guardarSiNoExiste(repositorio, JUAN_OSORIO, "Juan Osorio");
        guardarSiNoExiste(repositorio, CLIENTE_PRUEBAS, "Cliente Pruebas");
    }

    /** Número de cuenta de 12 dígitos para pruebas que crean sus propias cuentas. */
    public static String numeroCuentaAleatorio() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
    }

    private static void guardarSiNoExiste(ClienteRefRepository repositorio, UUID clienteId, String nombre) {
        if (!repositorio.existsById(clienteId)) {
            repositorio.save(ClienteRef.crear(clienteId, nombre, true, FECHA_SIEMBRA));
        }
    }
}

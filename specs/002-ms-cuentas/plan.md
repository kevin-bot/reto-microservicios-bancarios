# Implementation Plan: Microservicio de Cuentas y Movimientos (ms-cuentas)

**Branch**: `002-ms-cuentas` | **Date**: 2026-10-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-ms-cuentas/spec.md`

## Summary

Microservicio Spring Boot que gestiona cuentas (CRU sobre `/api/cuentas`) y movimientos (CRU
sobre `/api/movimientos`) y genera el estado de cuenta anidado en
`/api/reportes?fecha=inicio,fin&cliente={clienteId}`.

- **Saldo**: `Cuenta` es una entidad rica que aplica depósitos y retiros y rechaza con "Saldo no
  disponible".
- **Corrección de movimientos**: se puede corregir cualquier movimiento; los saldos posteriores se
  recalculan en cadena, todo o nada.
- **Cliente inactivo**: sus cuentas rechazan movimientos mientras siga inactivo.
- **Listado de movimientos**: filtrable por cliente, cuenta y fechas (caso de uso 5).
- **Concurrencia**: bloqueo pesimista por cuenta.
- **Clientes**: los conoce por una réplica local alimentada por los eventos de ms-clientes en
  RabbitMQ, con consumidor idempotente, descarte de eventos fuera de orden, 3 reintentos con
  backoff y DLQ.
- **Calidad**: misma arquitectura en capas, ProblemDetail y matriz de pruebas que ms-clientes.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.5.16 (Web, Data JPA, Validation, AMQP, Actuator), Flyway
(`flyway-core`, `flyway-database-postgresql`), MapStruct 1.6.3, Lombok, springdoc-openapi 2.8.17.

**Storage**: PostgreSQL 16, base `cuentas_db` (usuario `cuentas_user`), con Flyway y
`ddl-auto=validate`.

**Testing**: JUnit 5, Mockito, AssertJ, `@WebMvcTest`, `@SpringBootTest` + Testcontainers
(PostgreSQL, RabbitMQ), Karate 1.5.2.

**Target Platform**: Contenedor Linux (`eclipse-temurin:21-jre-alpine`) en Docker Compose.

**Project Type**: Web service (REST) y consumidor de mensajes.

**Performance Goals**:
- Reporte con hasta 1.000 movimientos en menos de 2 s (SC-006).
- Réplica de un cliente en menos de 5 s (SC-003).

**Constraints**:
- El saldo nunca queda negativo, ni siquiera con 20 retiros concurrentes (SC-002).
- Ninguna llamada síncrona a ms-clientes (SC-005).
- Servicio sin estado.

**Scale/Scope**: 4 entidades (más la réplica), 3 recursos REST con 12 operaciones y 1 cola
consumida con su DLQ.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple el diseño | Estado |
|---|---|---|
| I. Capas limpias | Paquetes `clienteref`, `cuenta`, `movimiento` y `reporte`, cada uno con `controller/service/repository/entity/dto/mapper` según aplique, más `shared/`. Services como interfaz e implementación; DTOs `record`; ninguna entidad sale por la API | ✅ |
| II. Dominio rico | `Cuenta.registrarMovimiento`, `corregirMovimiento`, `activar/desactivar`, `cambiarTipo`; `ClienteRef.aplicar` con control de orden. Excepciones de dominio sin HTTP | ✅ |
| III. Autonomía + async | Base `cuentas_db` propia; réplica `cliente_ref` alimentada solo por eventos; ninguna llamada REST a ms-clientes | ✅ |
| IV. Mensajería resiliente | Consumidor idempotente por `eventId` (`evento_procesado`); 3 reintentos con backoff de 1, 2 y 4 s; DLQ `cuentas.clientes.dlq`; ack después del commit | ✅ |
| V. Integridad del dinero | `BigDecimal` / `NUMERIC(15,2)`; `@Lock(PESSIMISTIC_WRITE)` al registrar y corregir; `CHECK (saldo_disponible >= 0)`; mensaje exacto "Saldo no disponible" con 422 | ✅ |
| VI. Pruebas por niveles | TDD en `CuentaTest`; unitarias de entidades y services; `@WebMvcTest` para los 3 controllers; 2 pruebas de integración con Testcontainers (concurrencia y mensajería); Karate de los casos de uso 2 a 5; Postman E2E | ✅ |
| VII. Errores RFC 7807 | `GlobalExceptionHandler`: 400, 404, 409 (número duplicado), 422 (saldo, cuenta inactiva, cliente no disponible), 500 | ✅ |
| Restricciones técnicas | Mismo stack y convenciones que ms-clientes; Dockerfile multi-stage; servicio en el compose; `BaseDatos.sql` consolidado en la raíz | ✅ |

**Resultado**: PASS, sin violaciones.

**Re-check post-diseño (Phase 1)**: PASS. Hay dos notas justificadas en el research:
- R1: se duplican piezas transversales entre servicios, por autonomía de los microservicios.
- R8: ms-clientes depende de que ms-cuentas esté healthy en el compose, para que la cola exista
  antes del primer evento.

Las clarificaciones del 2026-10-29 (cliente inactivo, listado por cliente y fecha del sistema)
están en research R14 y no cambian el resultado.

## Project Structure

### Documentation (this feature)

```text
specs/002-ms-cuentas/
├── plan.md
├── research.md          # R1–R13
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── cuentas-api.yaml         # OpenAPI: /cuentas, /movimientos, /reportes
│   └── eventos-consumidos.md    # Topología, reintentos, DLQ e idempotencia
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
docker-compose.yml                 # + servicio ms-cuentas (8082); ms-clientes depende de ms-cuentas healthy
BaseDatos.sql                      # esquema consolidado de clientes_db y cuentas_db
README.md                          # README raíz de la solución completa
postman/reto-banco-e2e.postman_collection.json   # casos de uso 1 a 5 entre ambos servicios

ms-cuentas/
├── Dockerfile, .dockerignore, mvnw, mvnw.cmd, .mvn/, pom.xml, README.md
└── src/
    ├── main/java/com/banco/cuentas/
    │   ├── MsCuentasApplication.java
    │   ├── clienteref/
    │   │   ├── entity/       ClienteRef, EventoProcesado
    │   │   ├── repository/   ClienteRefRepository, EventoProcesadoRepository
    │   │   ├── event/        ClienteEventoRecibido, TipoEventoCliente, ClienteEventoListener
    │   │   └── service/      ClienteEventoProcessor
    │   ├── cuenta/
    │   │   ├── controller/   CuentaController
    │   │   ├── service/      CuentaService, CuentaServiceImpl
    │   │   ├── repository/   CuentaRepository, CuentaSpecifications
    │   │   ├── entity/       Cuenta, TipoCuenta
    │   │   ├── dto/          CuentaCreateRequest, CuentaUpdateRequest, CuentaPatchRequest, CuentaResponse
    │   │   ├── mapper/       CuentaMapper
    │   │   └── exception/    CuentaNoEncontradaException, NumeroCuentaDuplicadoException,
    │   │                     SaldoNoDisponibleException, CuentaInactivaException,
    │   │                     ClienteNoDisponibleException
    │   ├── movimiento/
    │   │   ├── controller/   MovimientoController
    │   │   ├── service/      MovimientoService, MovimientoServiceImpl
    │   │   ├── repository/   MovimientoRepository, MovimientoSpecifications
    │   │   ├── entity/       Movimiento, TipoMovimiento
    │   │   ├── dto/          MovimientoCreateRequest, MovimientoUpdateRequest,
    │   │   │                 MovimientoPatchRequest, MovimientoResponse
    │   │   ├── mapper/       MovimientoMapper
    │   │   └── exception/    MovimientoNoEncontradoException
    │   ├── reporte/
    │   │   ├── controller/   ReporteController
    │   │   ├── service/      ReporteService, ReporteServiceImpl
    │   │   ├── dto/          ReporteResponse, ClienteResumen, CuentaReporte, MovimientoReporte
    │   │   └── exception/    ClienteNoEncontradoException
    │   └── shared/
    │       ├── exception/    GlobalExceptionHandler, ReglaNegocioException, DomainValidationException
    │       ├── validation/   AlMenosUnCampo (+ validator)
    │       ├── dto/          PaginaResponse
    │       ├── fecha/        RangoFechas, RangoFechasConverter
    │       └── config/       RabbitConfig, WebConfig, OpenApiConfig, BancoProperties
    ├── main/resources/
    │   ├── application.yml                       # port 8082, /api, retry del listener, zona horaria
    │   └── db/migration/V1__crear_tablas_cuentas.sql
    └── test/
        ├── java/com/banco/cuentas/
        │   ├── cuenta/entity/CuentaTest.java                      # TDD F2/F3 + corrección
        │   ├── clienteref/entity/ClienteRefTest.java
        │   ├── shared/fecha/RangoFechasTest.java
        │   ├── clienteref/service/ClienteEventoProcessorTest.java
        │   ├── cuenta/service/CuentaServiceImplTest.java
        │   ├── movimiento/service/MovimientoServiceImplTest.java
        │   ├── reporte/service/ReporteServiceImplTest.java
        │   ├── cuenta/controller/CuentaControllerTest.java
        │   ├── movimiento/controller/MovimientoControllerTest.java
        │   ├── reporte/controller/ReporteControllerTest.java
        │   ├── movimiento/MovimientoConcurrenciaIntegrationTest.java
        │   ├── clienteref/ClienteEventoListenerIntegrationTest.java
        │   ├── karate/CuentasKarateTest.java
        │   └── support/TestcontainersConfig.java, ClientesDePrueba.java
        └── resources/karate/cuentas/casos-de-uso.feature
```

**Structure Decision**: Proyecto Maven independiente `ms-cuentas/`, con la misma organización
por feature y capas que ms-clientes. Los entregables globales (`BaseDatos.sql`, README raíz y la
colección Postman E2E) se crean en la raíz porque esta feature completa la solución.

## Complexity Tracking

Sin violaciones de la constitución; no aplica.

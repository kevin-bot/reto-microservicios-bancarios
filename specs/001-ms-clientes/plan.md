# Implementation Plan: Microservicio de Clientes (ms-clientes)

**Branch**: `001-ms-clientes` | **Date**: 2026-10-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-ms-clientes/spec.md`

## Summary

Microservicio Spring Boot que gestiona clientes (`Cliente` hereda de `Persona` vía herencia JPA
JOINED) con CRUD completo sobre `/api/clientes`: alta, consulta individual y paginada,
reemplazo (PUT), actualización parcial (PATCH) y eliminación lógica. La contraseña se guarda con
BCrypt y nunca se expone. Cada cambio confirmado publica, después del commit, un evento JSON en
el topic exchange `clientes.exchange` de RabbitMQ para que ms-cuentas mantenga su réplica.
Arquitectura en capas por feature, entidades ricas, errores RFC 7807 y pruebas en cuatro niveles
(unitaria, `@WebMvcTest`, integración con Testcontainers y Karate).

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**:
- Spring Boot 3.5.x: Web, Data JPA, Validation, AMQP, Actuator.
- Flyway (`flyway-core`, `flyway-database-postgresql`).
- `spring-security-crypto` (BCrypt).
- MapStruct 1.6.x, Lombok.
- springdoc-openapi 2.8.x.

**Storage**: PostgreSQL 16, base `clientes_db`, esquema gestionado por Flyway
(`ddl-auto=validate`).

**Testing**:
- JUnit 5, Mockito, AssertJ.
- Spring Boot Test (`@WebMvcTest`, `@SpringBootTest`).
- Testcontainers (PostgreSQL, RabbitMQ) con `@ServiceConnection`.
- Karate 1.5.x (`karate-junit5`).

**Target Platform**: Contenedor Linux (`eclipse-temurin:21-jre-alpine`), orquestado con
Docker Compose.

**Project Type**: Web service (microservicio REST + productor de mensajes).

**Performance Goals**: Operaciones CRUD por debajo de 500 ms p95 en entorno local; evento
publicado en menos de 5 s tras el commit (SC-005).

**Constraints**:
- Servicio sin estado (escalable horizontalmente).
- La contraseña nunca sale del servicio.
- Sin llamadas síncronas a otros servicios.
- Build Docker con `-DskipTests`.

**Scale/Scope**: Alcance de reto técnico: 1 agregado (Cliente), 6 endpoints y 3 tipos de
evento.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo lo cumple el diseño | Estado |
|---|---|---|
| I. Capas limpias | Paquete `cliente/{controller,service,repository,entity,dto,mapper,event}`, más `shared/{exception,config}`. `ClienteService` (interfaz) + `ClienteServiceImpl`. Controller sin lógica. DTOs `record`; la entidad nunca sale por la API | ✅ |
| II. Dominio rico | `Cliente.crear(...)`, `activar()`, `desactivar()`, `cambiarContrasena()`, `eliminar()`, `actualizarContacto()`; sin `@Setter`; excepciones de dominio sin HTTP | ✅ |
| III. Autonomía + async | Base `clientes_db` propia; eventos a `clientes.exchange` con `eventId`, `tipo`, `fechaOcurrencia` y payload; ninguna llamada REST a ms-cuentas | ✅ |
| IV. Mensajería resiliente | Publicación `AFTER_COMMIT`; outbox documentado como mejora; idempotencia, reintentos y DLQ son del consumidor (ms-cuentas). El contrato exige `eventId` para habilitarlos | ✅ |
| V. Integridad del dinero | No aplica: ms-clientes no maneja montos | N/A |
| VI. Pruebas por niveles | TDD en `ClienteTest`; unitarias de entidad y service; `@WebMvcTest` del controller; `@SpringBootTest` + Testcontainers; Karate en `mvn test`; Postman con el caso de uso 1 | ✅ |
| VII. Errores RFC 7807 | `GlobalExceptionHandler` con 400, 404, 409, 422 y 500 (research R9) | ✅ |
| Restricciones técnicas | Java 21, Boot 3.x, Maven, Flyway con `validate`, BCrypt, MapStruct, Lombok restringido, springdoc, Dockerfile multi-stage, compose en la raíz | ✅ |

**Resultado**: PASS. No hay violaciones que justificar.

**Re-check post-diseño (Phase 1)**: PASS. Una nota: MapStruct se usa solo de entidad a
response (research R11) para no romper el Principio II. Es coherente con la constitución, que
exige MapStruct para los mappers pero también entidades sin setters.

## Project Structure

### Documentation (this feature)

```text
specs/001-ms-clientes/
├── plan.md              # Este archivo
├── research.md          # Phase 0: decisiones R1–R12
├── data-model.md        # Phase 1: entidades, tablas, transiciones
├── quickstart.md        # Phase 1: guía de validación
├── contracts/
│   ├── clientes-api.yaml    # OpenAPI 3 de /api/clientes
│   └── eventos-cliente.md   # Contrato de eventos RabbitMQ
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
docker-compose.yml                      # postgres + rabbitmq + ms-clientes (ms-cuentas en feature 002)
docker/
└── postgres/init/01-crear-bases.sql    # crea clientes_db y cuentas_db con usuarios propios

ms-clientes/
├── Dockerfile                          # multi-stage, -DskipTests, usuario no root
├── mvnw, mvnw.cmd, .mvn/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/banco/clientes/
    │   │   ├── MsClientesApplication.java
    │   │   ├── cliente/
    │   │   │   ├── controller/   ClienteController
    │   │   │   ├── service/      ClienteService, ClienteServiceImpl
    │   │   │   ├── repository/   ClienteRepository
    │   │   │   ├── entity/       Persona, Cliente, Genero
    │   │   │   ├── dto/          ClienteCreateRequest, ClienteUpdateRequest,
    │   │   │   │                 ClientePatchRequest, ClienteResponse
    │   │   │   ├── mapper/       ClienteMapper (MapStruct)
    │   │   │   ├── event/        ClienteEventoDominio, ClienteEvento, TipoEventoCliente,
    │   │   │   │                 ClienteEventPublisher
    │   │   │   └── exception/    ClienteNoEncontradoException,
    │   │   │                     IdentificacionDuplicadaException, ClienteEliminadoException
    │   │   └── shared/
    │   │       ├── exception/    GlobalExceptionHandler, ReglaNegocioException,
    │   │       │                 DomainValidationException
    │   │       ├── validation/   AlMenosUnCampo (+ validator)
    │   │       ├── dto/          PaginaResponse
    │   │       └── config/       RabbitConfig, PasswordEncoderConfig, OpenApiConfig
    │   └── resources/
    │       ├── application.yml             # server.servlet.context-path=/api, port 8081
    │       └── db/migration/V1__crear_tablas_persona_cliente.sql
    └── test/
        ├── java/com/banco/clientes/
        │   ├── cliente/entity/ClienteTest.java                 # unitaria F5 (TDD)
        │   ├── cliente/service/ClienteServiceImplTest.java     # unitaria Mockito
        │   ├── cliente/event/ClienteEventPublisherTest.java    # unitaria Mockito
        │   ├── cliente/controller/ClienteControllerTest.java   # @WebMvcTest
        │   ├── cliente/ClienteIntegrationTest.java             # @SpringBootTest + Testcontainers (F6)
        │   ├── karate/ClientesKarateTest.java                  # runner Karate
        │   └── support/TestcontainersConfig.java               # contenedores compartidos
        └── resources/karate/clientes/clientes.feature

postman/
└── ms-clientes.postman_collection.json   # caso de uso 1 y CRUD
```

**Structure Decision**: Un proyecto Maven independiente en `ms-clientes/` (la constitución
pide un proyecto por servicio, sin POM padre). Los paquetes van por feature (`cliente`) con
capas dentro, más `shared` para lo transversal. Los artefactos que comparten ambos servicios
(`docker-compose.yml`, `docker/`, `postman/`) viven en la raíz del monorepo. `BaseDatos.sql`
y el README raíz se consolidan cuando ambos servicios existan.

## Complexity Tracking

Sin violaciones de la constitución; esta sección no aplica.

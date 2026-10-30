---
description: "Lista de tareas para implementar ms-cuentas"
---

# Tasks: Microservicio de Cuentas y Movimientos (ms-cuentas)

**Input**: Design documents from `/specs/002-ms-cuentas/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Requeridos por la constitución (Principio VI). **TDD obligatorio** en `CuentaTest`
(reglas de saldo F2/F3 y corrección con recálculo) y en las reglas de orden de `ClienteRef`. En
el resto, las pruebas de cada historia se escriben primero y deben fallar antes de implementar.

**Organization**: Tareas agrupadas por historia de usuario.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1–US4)

## Path Conventions

- `M/` = `ms-cuentas/src/main/java/com/banco/cuentas/`
- `R/` = `ms-cuentas/src/main/resources/`
- `T/` = `ms-cuentas/src/test/java/com/banco/cuentas/`
- `TR/` = `ms-cuentas/src/test/resources/`
- Rutas sin prefijo son relativas a la raíz del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicializar el proyecto Maven con el mismo stack que ms-clientes.

- [ ] T001 Crear el proyecto `ms-cuentas/`:
  - `pom.xml` con groupId `com.banco`, artifactId `ms-cuentas`, parent Spring Boot **3.5.16** y Java 21.
  - Las mismas dependencias y `annotationProcessorPaths` que `ms-clientes/pom.xml`.
  - Además, `org.awaitility:awaitility` en scope test (versión gestionada por Boot).
  - Maven Wrapper (Maven 3.9.11) y `M/MsCuentasApplication.java` con `@ConfigurationPropertiesScan`.
- [ ] T002 Crear `R/application.yml`:
  - **Servidor**: `server.port=8082`, `server.servlet.context-path=/api`.
  - **Datasource** por variables de entorno con defaults locales (`DB_URL=jdbc:postgresql://localhost:5433/cuentas_db`, `DB_USER=cuentas_user`, `DB_PASSWORD=cuentas_pass`).
  - **JPA**: `ddl-auto=validate`, `open-in-view=false`, Flyway habilitado.
  - **RabbitMQ** por variables de entorno (`RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`).
  - **Listener**: `spring.rabbitmq.listener.simple.retry.enabled=true`, `max-attempts=4`, `initial-interval=1s`, `multiplier=2`, `max-interval=4s` y `default-requeue-rejected=false`.
  - **Errores y JSON**: `spring.mvc.problemdetails.enabled=true`, `spring.jackson.deserialization.fail-on-unknown-properties=true`.
  - **Paginación**: `spring.data.web.pageable.max-page-size=100`.
  - **Banco**: `banco.zona-horaria=America/Guayaquil`.
  - **Otros**: Actuator `health`; springdoc `/swagger-ui.html`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Esquema, réplica de clientes, entidad Cuenta básica, manejo de errores y utilidades
que usan todas las historias.

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta completar esta fase.

### Base transversal

- [ ] T003 [P] Crear las excepciones base en `M/shared/exception/`:
  - `ReglaNegocioException` (abstracta, constructor `(titulo, mensaje)` y `getTitulo()`).
  - `DomainValidationException` (extiende `ReglaNegocioException`, con título "Datos inválidos").
  - `RecursoNoEncontradoException` (abstracta, con título).
  - `ConflictoException` (abstracta, con título).
- [ ] T004 Crear `R/db/migration/V1__crear_tablas_cuentas.sql` exactamente según data-model.md:
  - **Tablas**: `cliente_ref`, `cuenta`, `movimiento` y `evento_procesado`.
  - **Restricciones nombradas**:
    - `uk_cuenta_numero`;
    - `ck_cuenta_saldo CHECK (saldo_disponible >= 0)`;
    - `CHECK (saldo_inicial >= 0)`;
    - `uk_movimiento_id`;
    - `CHECK (valor <> 0)`;
    - `CHECK (saldo >= 0)`.
  - **Montos**: `NUMERIC(15,2)`.
  - **Índices**: `idx_cuenta_cliente` e `idx_movimiento_cuenta_fecha (cuenta_id, fecha, id)`.
- [ ] T005 [P] Crear `M/shared/exception/GlobalExceptionHandler.java` (`@RestControllerAdvice` sobre `ResponseEntityExceptionHandler`, ProblemDetail), siguiendo el de ms-clientes:

  | Excepción | HTTP | `title` |
  |---|---|---|
  | Validación de Bean Validation | 400 | "Datos inválidos", con `errores` por campo |
  | JSON ilegible o campo desconocido | 400 | "Solicitud mal formada" |
  | `TypeMismatch` (UUID o `fecha` inválidos) y `PropertyReferenceException` | 400 | "Parámetro inválido" |
  | Parámetro obligatorio faltante (`MissingServletRequestParameterException`) | 400 | "Parámetro inválido" |
  | `DomainValidationException` | 400 | "Datos inválidos" |
  | `RecursoNoEncontradoException` | 404 | El título de la excepción |
  | `ConflictoException` | 409 | El título de la excepción |
  | `ReglaNegocioException` | 422 | El título de la excepción |
  | `DataIntegrityViolationException` con `uk_cuenta_numero` | 409 | "Número de cuenta duplicado" |
  | `DataIntegrityViolationException` con `ck_cuenta_saldo` | 422 | "Saldo no disponible", detail "Saldo no disponible" |
  | Cualquier otra | 500 | "Error interno", sin detalle (con `log.error`) |
- [ ] T006 [P] Crear `M/shared/dto/PaginaResponse.java`, igual que en ms-clientes
- [ ] T007 [P] Crear `M/shared/validation/AlMenosUnCampo.java` y su validator, igual que en ms-clientes, más `T/shared/validation/AlMenosUnCampoValidatorTest.java`
- [ ] T008 [P] Configuración de tiempo y documentación:
  - `M/shared/config/BancoProperties.java`: `@ConfigurationProperties("banco")`, `ZoneId zonaHoraria`.
  - `M/shared/config/TiempoConfig.java`: bean `Clock` = `Clock.system(zonaHoraria)`; las pruebas lo reemplazan.
  - `M/shared/config/OpenApiConfig.java`: título "ms-cuentas API".

### Rango de fechas (compartido por el listado y el reporte)

- [ ] T009 **(TDD: escribir primero y verificar que falla)** Crear `T/shared/fecha/RangoFechasTest.java`:
  - **Acepta**: `"2022-02-01,2022-02-10"` e inicio igual a fin.
  - **Rechaza** con `IllegalArgumentException`:
    - sin coma;
    - tres fechas;
    - fecha no ISO;
    - `inicio > fin`;
    - más de 366 días.
  - **Convierte a instantes**: `desde(zona)` = inicio 00:00 y `hasta(zona)` = (fin + 1 día) 00:00 en la zona dada.
- [ ] T010 Crear `M/shared/fecha/RangoFechas.java` (record con `static parse(String)`, `desde(ZoneId)`, `hasta(ZoneId)`), `M/shared/fecha/RangoFechasConverter.java` (`Converter<String, RangoFechas>`) y `M/shared/config/WebConfig.java`, que registra el converter

### Réplica de clientes y Cuenta básica (usadas por todas las historias)

- [ ] T011 [P] **(TDD)** Crear `T/clienteref/entity/ClienteRefTest.java`:
  - `ClienteRef.crear(clienteId, nombre, estado, fechaOcurrencia)`.
  - `aplicar(...)` con una fecha posterior actualiza nombre, estado y `ultimaActualizacion`, y devuelve `true`.
  - Con una fecha igual o anterior no cambia nada y devuelve `false`.
  - `estaActivo()`.
- [ ] T012 Crear `M/clienteref/entity/ClienteRef.java` hasta que `ClienteRefTest` pase:
  - `@Table(name="cliente_ref")`, PK `clienteId`.
  - Lombok: solo `@Getter` y `@NoArgsConstructor(access = PROTECTED)`; **prohibidos `@Data`, `@Setter` y `@EqualsAndHashCode`**.
  - Repositorio `M/clienteref/repository/ClienteRefRepository.java`.
- [ ] T013 **(TDD: escribir primero)** Crear `T/cuenta/entity/CuentaTest.java`, nested "Apertura y estado":
  - `abrir` con datos válidos: saldo disponible = saldo inicial.
  - `abrir` lanza `DomainValidationException` si el número no cumple `^[0-9]{6,20}$`, el saldo inicial es negativo o tiene 3 decimales, o el tipo es nulo.
  - `abrir` con un cliente inactivo lanza `ClienteNoDisponibleException`.
  - `desactivar` y `activar` funcionan; `activar` con el cliente inactivo lanza `ClienteNoDisponibleException`.
  - `cambiarTipo`.
- [ ] T014 Crear el modelo de Cuenta hasta que el nested de T013 pase:
  - `M/cuenta/entity/TipoCuenta.java` (`AHORROS`, `CORRIENTE`).
  - `M/cuenta/exception/ClienteNoDisponibleException.java` (422, título y detalle "Cliente no disponible").
  - `M/cuenta/entity/Cuenta.java`: `@ManyToOne(fetch = LAZY)` a `ClienteRef`; métodos `abrir`, `activar`, `desactivar`, `cambiarTipo`; mismas restricciones de Lombok.
- [ ] T015 Crear `M/cuenta/repository/CuentaRepository.java` (`JpaRepository<Cuenta, Long>` y `JpaSpecificationExecutor`) con:
  - `findByNumeroCuenta(String)`;
  - `buscarParaActualizar(String)` con `@Lock(PESSIMISTIC_WRITE)` y `@Query`;
  - `existsByNumeroCuenta(String)`;
  - `findByClienteClienteId(UUID)`.

### Soporte de pruebas

- [ ] T016 [P] Crear `T/support/TestcontainersConfig.java`, igual que en ms-clientes, y `T/support/ClientesDePrueba.java`:
  - Inserta con `ClienteRefRepository` a Jose Lema, Marianela Montalvo, Juan Osorio y "Cliente Pruebas", con `clienteId` fijos (constantes públicas).
  - Es **idempotente**: solo inserta si `!existsById`, porque las pruebas que comparten contexto de Spring comparten la base.
  - Así las historias US2–US4 no dependen del consumidor de eventos (US1).
  - Incluye un helper `numeroCuentaAleatorio()` (12 dígitos) para las pruebas que crean sus propias cuentas.

**Checkpoint**: `./mvnw test` en verde y la aplicación arranca contra el compose (Flyway valida el esquema).

---

## Phase 3: User Story 1 - Conocer a los clientes del banco (Priority: P1)

**Goal**: Réplica `cliente_ref` alimentada por los eventos de ms-clientes, con idempotencia,
descarte de eventos fuera de orden, reintentos y DLQ.

**Independent Test**: Publicar eventos en `clientes.exchange` y verificar la réplica, un
duplicado sin efecto, la eliminación que desactiva cuentas y un JSON inválido en la DLQ.

### Tests for User Story 1 ⚠️

- [ ] T017 [P] [US1] Crear `T/clienteref/service/ClienteEventoProcessorImplTest.java` (Mockito: `ClienteRefRepository`, `EventoProcesadoRepository`, `CuentaRepository`):
  - `CLIENTE_CREADO` nuevo: guarda el `ClienteRef` y el `EventoProcesado`.
  - `CLIENTE_ACTUALIZADO` más reciente: aplica el cambio.
  - `eventId` ya procesado: no guarda nada.
  - Evento fuera de orden: no cambia la réplica, pero registra el `eventId`.
  - `CLIENTE_ELIMINADO`: deja el cliente inactivo y llama a `desactivar()` en cada una de sus cuentas.
- [ ] T018 [P] [US1] Crear `T/clienteref/ClienteEventoListenerIntegrationTest.java` (`@SpringBootTest`, Testcontainers). Publica JSON con `RabbitTemplate` en `clientes.exchange` y espera con Awaitility:
  - `cliente.creado`: la réplica existe en **menos de 5 s** (SC-003).
  - Mismo mensaje dos veces: una sola fila en `evento_procesado` y la réplica sin cambios.
  - Evento con una `fechaOcurrencia` anterior: se ignora.
  - `cliente.eliminado`: el cliente queda inactivo y sus cuentas (creadas con `CuentaRepository` y números aleatorios) quedan `estado=false`.
  - Mensaje con JSON inválido: aparece en `cuentas.clientes.dlq` en **menos de 5 s**, porque no se reintenta.
  - **Error transitorio**: con `@MockitoSpyBean ClienteEventoProcessor` que lanza `RuntimeException`, el mensaje llega a `cuentas.clientes.dlq` (esperar hasta 15 s) y el procesador se invocó **exactamente 4 veces** (FR-004).

### Implementation for User Story 1

- [ ] T019 [P] [US1] Crear `M/clienteref/entity/EventoProcesado.java` (PK `eventId`, `procesadoEn`) y `M/clienteref/repository/EventoProcesadoRepository.java`
- [ ] T020 [P] [US1] Crear `M/clienteref/event/TipoEventoCliente.java` y el record `M/clienteref/event/ClienteEventoRecibido.java` (`eventId`, `tipo`, `fechaOcurrencia`, `clienteId`, `nombre`, `estado`), según contracts/eventos-consumidos.md
- [ ] T021 [US1] Crear la interfaz `M/clienteref/service/ClienteEventoProcessor.java` con `procesar(ClienteEventoRecibido)`, y `ClienteEventoProcessorImpl.java` (`@Transactional`), con el flujo de contracts/eventos-consumidos.md (idempotencia, orden, upsert y desactivación de cuentas) y logs INFO en cada descarte
- [ ] T022 [US1] Crear `M/shared/config/RabbitConfig.java`:
  - **Exchange**: `clientes.exchange` (topic, durable).
  - **Cola**: `cuentas.clientes.queue` (durable, `x-dead-letter-exchange=cuentas.clientes.dlx`, `x-dead-letter-routing-key=cuentas.clientes.queue`), con binding `cliente.*`.
  - **DLQ**: DLX `cuentas.clientes.dlx` (direct) y cola `cuentas.clientes.dlq` con su binding.
  - **Converter**: `Jackson2JsonMessageConverter` con una copia del `ObjectMapper` con `FAIL_ON_UNKNOWN_PROPERTIES=false` y `setAlwaysConvertToInferredType(true)`.
  - **Política de reintentos**: bean `RabbitRetryTemplateCustomizer` (destino `LISTENER`) con una política que no reintenta `MessageConversionException` (ni una `ListenerExecutionFailedException` causada por ella) y mantiene los 4 intentos para el resto.
- [ ] T023 [US1] Crear `M/clienteref/event/ClienteEventoListener.java` (`@RabbitListener(queues = "cuentas.clientes.queue")`), que delega en `ClienteEventoProcessor`

**Checkpoint**: T017–T018 en verde.

---

## Phase 4: User Story 2 - Abrir y administrar cuentas (Priority: P1)

**Goal**: CRU de cuentas: abrir (casos de uso 2 y 3), consultar, listar por cliente y actualizar el tipo o el estado.

**Independent Test**: Con los clientes de prueba, crear las cuentas 478758, 225487, 495878,
496825 y 585545; un número duplicado da 409; un cliente inexistente da 422.

### Tests for User Story 2 ⚠️

- [ ] T024 [P] [US2] Crear `T/cuenta/service/CuentaServiceImplTest.java` (Mockito):
  - **crear**: correcto; número duplicado → `NumeroCuentaDuplicadoException`; cliente inexistente o inactivo → `ClienteNoDisponibleException`.
  - **obtener**: 404 si no existe.
  - **listar** con filtro de cliente.
  - **actualizar / actualizarParcial**: cambian el tipo y el estado; activar con el cliente inactivo → `ClienteNoDisponibleException`.
- [ ] T025 [P] [US2] Crear `T/cuenta/controller/CuentaControllerTest.java` (`@WebMvcTest`):
  - **POST**: 201 con `Location: /cuentas/{numeroCuenta}`.
  - **POST inválido** → 400:
    - número `"12ab"`;
    - `saldoInicial` −1 o con 3 decimales;
    - sin `clienteId`;
    - con un campo extra `saldoDisponible`.
  - **POST**: 409 por número duplicado y 422 por cliente no disponible.
  - **GET**: por número (200 y 404) y listado `?clienteId=`.
  - **PUT**: 200, y 400 si falta `estado`.
  - **PATCH**: `{}` da 400 y `{"estado": false}` da 200.
  - **DELETE** `/cuentas/{numeroCuenta}`: 405, porque las cuentas no se eliminan (FR-012).
- [ ] T026 [P] [US2] Crear el runner `T/karate/CuentasKarateTest.java`:
  - `@SpringBootTest(RANDOM_PORT)` con `TestcontainersConfig`.
  - Siembra con `ClientesDePrueba` y pasa los `clienteId` como propiedades del sistema de Karate.
  - Ejecuta `classpath:karate/cuentas`.

  Crear también:
  - `TR/karate/cuentas/casos-de-uso.feature`, con **un único escenario ordenado** "Casos de uso 2 a 5", que en esta historia cubre:
    - caso 2: las 4 cuentas con sus saldos;
    - caso 3: la cuenta 585545 de Jose; el listado por cliente devuelve 2 cuentas.
  - `TR/karate/cuentas/cuentas.feature`, con escenarios de error sobre "Cliente Pruebas" y números de cuenta aleatorios:
    - número duplicado: 409;
    - cliente inexistente: 422;
    - PATCH de estado.

### Implementation for User Story 2

- [ ] T027 [P] [US2] Crear las excepciones de cuenta:
  - `M/cuenta/exception/CuentaNoEncontradaException.java` (`RecursoNoEncontradoException`, título "Cuenta no encontrada").
  - `M/cuenta/exception/NumeroCuentaDuplicadoException.java` (`ConflictoException`, título "Número de cuenta duplicado").
- [ ] T028 [P] [US2] Crear los DTOs en `M/cuenta/dto/`:
  - `CuentaCreateRequest`:

    | Campo | Restricciones |
    |---|---|
    | `numeroCuenta` | `@NotBlank @Pattern("^[0-9]{6,20}$")` |
    | `tipo` | `@NotNull` |
    | `saldoInicial` | `@NotNull @DecimalMin("0.00") @Digits(integer=13, fraction=2)` |
    | `estado` | `Boolean`; si es nulo, se toma `true` |
    | `clienteId` | `@NotNull UUID` |
  - `CuentaUpdateRequest`: `tipo` y `estado`, ambos `@NotNull`.
  - `CuentaPatchRequest`: anotado con `@AlMenosUnCampo`, campos `tipo` y `estado`.
  - `CuentaResponse`: `numeroCuenta`, `tipo`, `saldoInicial`, `saldoDisponible`, `estado`, `clienteId` y `nombreCliente`.
- [ ] T029 [P] [US2] Crear `M/cuenta/mapper/CuentaMapper.java` (MapStruct: `cliente.clienteId` → `clienteId`, `cliente.nombre` → `nombreCliente`) y `M/cuenta/repository/CuentaSpecifications.java` (`deCliente(UUID)` opcional)
- [ ] T030 [US2] Crear la interfaz `M/cuenta/service/CuentaService.java` y `CuentaServiceImpl.java` con `crear`, `obtener`, `listar(clienteId, pageable)`, `actualizar` y `actualizarParcial`; el service usa los métodos de dominio de `Cuenta`
- [ ] T031 [US2] Crear `M/cuenta/controller/CuentaController.java` en `/cuentas`:
  - `POST`: 201 con `Location`.
  - `GET /{numeroCuenta}`.
  - `GET` de listado con `clienteId` opcional y `@PageableDefault(size=20, sort="numeroCuenta")`.
  - `PUT /{numeroCuenta}` y `PATCH /{numeroCuenta}`.
  - Anotaciones de springdoc.

**Checkpoint**: US2 demostrable con Karate.

---

## Phase 5: User Story 3 - Registrar movimientos (Priority: P1)

**Goal**: Depósitos y retiros con "Saldo no disponible" (F2, F3), concurrencia segura, corrección
con recálculo y listado plano por cliente, cuenta y fechas (caso de uso 5).

**Independent Test**: Caso de uso 4 (saldos 1425, 700, 150, 0); un retiro de −1 sobre 0 da 422
"Saldo no disponible"; 20 retiros simultáneos nunca dejan saldo negativo.

### Tests for User Story 3 ⚠️

- [ ] T032 [US3] **(TDD: escribir primero y verificar que falla)** Agregar a `T/cuenta/entity/CuentaTest.java` el nested "Movimientos".

  `registrarMovimiento`:
  - 2000 y −575: saldo 1425, tipo `RETIRO`, el saldo resultante del movimiento es 1425.
  - 100 y +600: `DEPOSITO`, saldo 700.
  - 540 y −540: saldo 0 (permitido).
  - 0 y −1: `SaldoNoDisponibleException` con el mensaje exacto "Saldo no disponible"; el saldo sigue en 0.
  - Cuenta inactiva: `CuentaInactivaException`.
  - Cliente inactivo: `ClienteNoDisponibleException`.
  - Valor 0 o con 3 decimales: `DomainValidationException`.

  `corregirMovimiento`:
  - 2000 con −575 y +100; corregir el primero a −500 deja saldos 1500 y 1600, y la cuenta en 1600.
  - Corregir a un valor que deja un saldo intermedio negativo: `SaldoNoDisponibleException` y **ningún** valor ni saldo cambia.
  - Cambiar el signo cambia el tipo.
  - Cuenta inactiva o cliente inactivo: rechaza.
- [ ] T033 [P] [US3] Crear `T/movimiento/service/MovimientoServiceImplTest.java` (Mockito, con un `Clock` fijo):
  - **registrar**: usa `buscarParaActualizar`, la fecha viene del `Clock` y guarda el movimiento; cuenta inexistente → 404.
  - **obtener**: 404 si no existe.
  - **listar**: delega con los filtros.
  - **corregir**: verifica con `InOrder` la secuencia `buscarNumeroCuenta(movimientoId)` → `buscarParaActualizar(numero)` → `findByMovimientoId` + `findPosteriores`, y que delega en el dominio.
- [ ] T034 [P] [US3] Crear `T/movimiento/controller/MovimientoControllerTest.java` (`@WebMvcTest`):
  - **POST**:
    - 201;
    - 422 con `title` y `detail` "Saldo no disponible";
    - 422 "Cuenta inactiva";
    - 400 con `valor` ausente;
    - 400 con el campo `fecha` en el cuerpo;
    - 404 si la cuenta no existe.
  - **GET por id**: 200 y 404.
  - **GET de listado**: con `cliente`, `numeroCuenta` y `fecha` responde 200 con filas planas (`nombreCliente`, `numeroCuenta`, `tipoCuenta`, `saldoInicial`, `estadoCuenta`, `valor`, `saldo`); con `fecha=2022-02-10,2022-02-01` responde 400.
  - **PUT y PATCH**: 200, `{}` da 400 y 422 "Saldo no disponible".
- [ ] T035 [P] [US3] Crear `T/movimiento/MovimientoConcurrenciaIntegrationTest.java` (`@SpringBootTest`, Testcontainers) para SC-002:
  - Cuenta propia (número aleatorio, "Cliente Pruebas") con saldo 100; 20 hilos lanzan a la vez un retiro de −10 cada uno, a través de `MovimientoService`.
  - Exactamente 10 retiros tienen éxito y 10 reciben `SaldoNoDisponibleException`.
  - El saldo final es 0; hay 10 movimientos en la BD y ningún saldo negativo.
- [ ] T036 [US3] Pruebas Karate de movimientos. "Hoy" se calcula con `LocalDate.now(ZoneId.of('America/Guayaquil'))`.
  - Extender el escenario de `TR/karate/cuentas/casos-de-uso.feature`:
    - caso 4: los 4 movimientos y sus saldos (1425, 700, 150, 0);
    - retiro de −1 sobre 496825: 422 "Saldo no disponible";
    - caso 5 plano: `GET /movimientos?cliente={marianela}&fecha={hoy},{hoy}` devuelve 2 filas.
  - Crear `TR/karate/cuentas/movimientos.feature` sobre cuentas aleatorias de "Cliente Pruebas":
    - corrección −575 → −500 que recalcula el saldo;
    - movimiento sobre una cuenta inactiva: 422;
    - movimiento con el campo `fecha`: 400.

### Implementation for User Story 3

- [ ] T037 [P] [US3] Crear `M/movimiento/entity/TipoMovimiento.java` (`DEPOSITO`, `RETIRO`, con `static desde(BigDecimal valor)`) y las excepciones:
  - `M/cuenta/exception/SaldoNoDisponibleException.java` (título y mensaje "Saldo no disponible").
  - `M/cuenta/exception/CuentaInactivaException.java` (título "Cuenta inactiva").
  - `M/movimiento/exception/MovimientoNoEncontradoException.java` (404, "Movimiento no encontrado").
- [ ] T038 [US3] Crear `M/movimiento/entity/Movimiento.java`:
  - Constructor de paquete, que solo usa `Cuenta`.
  - `movimientoId` UUID generado.
  - Métodos de paquete `corregirValor` y `actualizarSaldo`.
  - Solo `@Getter`, y `@ManyToOne(fetch = LAZY)` a `Cuenta`.
- [ ] T039 [US3] Implementar en `M/cuenta/entity/Cuenta.java` `registrarMovimiento(valor, fecha)` y `corregirMovimiento(movimiento, nuevoValor, posteriores)` según research R4, hasta que T032 pase
- [ ] T040 [US3] Crear `M/movimiento/repository/MovimientoRepository.java` (+ `JpaSpecificationExecutor`) y `M/movimiento/repository/MovimientoSpecifications.java`:
  - **Repositorio**: `findByMovimientoId`; `buscarNumeroCuenta(UUID movimientoId)` (proyección `@Query`, sin cargar la entidad); `findPosteriores(cuenta, fecha, id)` ordenados por `fecha, id`; `findDelReporte(List<Long> cuentaIds, Instant desde, Instant hasta)` ordenados por `fecha, id`.
  - **Specifications**: `deCliente`, `deCuenta`, `enRango`, más un `fetch join` de cuenta y cliente que se omite en la consulta de conteo (`query.getResultType() != Long.class`).
- [ ] T041 [P] [US3] Crear los DTOs en `M/movimiento/dto/`:
  - `MovimientoCreateRequest`: `numeroCuenta` `@NotBlank`; `valor` `@NotNull @Digits(integer=13, fraction=2)`.
  - `MovimientoUpdateRequest`: `valor` `@NotNull`.
  - `MovimientoPatchRequest`: `@AlMenosUnCampo`, campo `valor`.
  - `MovimientoResponse`: fila plana según el contrato.

  Crear también `M/movimiento/mapper/MovimientoMapper.java` (MapStruct, con `@Mapping` desde `cuenta.*` y `cuenta.cliente.*`).
- [ ] T042 [US3] Crear la interfaz `M/movimiento/service/MovimientoService.java` y `MovimientoServiceImpl.java`:
  - `registrar` y `corregir`, ambos `@Transactional` con bloqueo pesimista; la fecha viene del `Clock`.
  - `corregir` sigue el orden obligatorio de research R4: `buscarNumeroCuenta` → `buscarParaActualizar` → cargar el movimiento y los posteriores. Así nunca recalcula sobre una entidad desactualizada de la caché.
  - `obtener` y `listar(cliente, numeroCuenta, RangoFechas, pageable)`, este con `readOnly`.
  - `log.info` por cada movimiento, sin datos sensibles.
- [ ] T043 [US3] Crear `M/movimiento/controller/MovimientoController.java` en `/movimientos`:
  - `POST`: 201 con `Location`.
  - `GET /{movimientoId}`.
  - `GET` de listado con `cliente`, `numeroCuenta` y `fecha` opcionales, y `@PageableDefault(size=20, sort="fecha", direction=DESC)`.
  - `PUT /{movimientoId}` y `PATCH /{movimientoId}`.

**Checkpoint**: F2 y F3 completos; concurrencia verificada.

---

## Phase 6: User Story 4 - Reporte de estado de cuenta (Priority: P2)

**Goal**: `GET /reportes?fecha=inicio,fin&cliente={clienteId}` con estructura anidada (F4).

**Independent Test**: Reporte de Marianela en el rango de hoy: cuentas 225487 (saldo 700, movimiento
+600) y 496825 (saldo 0, movimiento −540).

### Tests for User Story 4 ⚠️

- [ ] T044 [P] [US4] Crear `T/reporte/service/ReporteServiceImplTest.java` (Mockito):
  - Cliente desconocido → `ClienteNoEncontradoException`.
  - Agrupa los movimientos por cuenta conservando el orden.
  - Una cuenta sin movimientos aparece con la lista vacía.
  - Usa `rango.desde(zona)` y `rango.hasta(zona)` de `BancoProperties`.
- [ ] T045 [P] [US4] Crear `T/reporte/controller/ReporteControllerTest.java` (`@WebMvcTest`):
  - 200 con la estructura `cliente`, `fechaInicio`, `fechaFin` y `cuentas[].movimientos[]`.
  - 400 sin `fecha` o sin `cliente`.
  - 400 con `fecha=2022-02-10,2022-02-01` o `fecha=2022-02-01`.
  - 404 si el cliente no existe.
- [ ] T046 [US4] Pruebas Karate del reporte. "Hoy" se calcula en `America/Guayaquil`.
  - Extender el escenario de `TR/karate/cuentas/casos-de-uso.feature` con el caso 5: reporte de Marianela con 2 cuentas, saldos 700 y 0 y un movimiento en cada una.
  - Crear `TR/karate/cuentas/reportes.feature`:
    - rango invertido: 400;
    - rango de más de 1 año: 400;
    - cliente inexistente: 404.
- [ ] T047 [P] [US4] Crear `T/reporte/ReporteRendimientoIntegrationTest.java` (Testcontainers) para SC-006: inserta 1.000 movimientos en una cuenta propia (número aleatorio, "Cliente Pruebas") y verifica que `ReporteService.generar` responde en menos de 2 s

### Implementation for User Story 4

- [ ] T048 [P] [US4] Crear los records en `M/reporte/dto/` (`ReporteResponse`, `ClienteResumen`, `CuentaReporte`, `MovimientoReporte`) según el contrato, y `M/reporte/exception/ClienteNoEncontradoException.java` (404, "Cliente no encontrado")
- [ ] T049 [US4] Crear la interfaz `M/reporte/service/ReporteService.java` y `ReporteServiceImpl.java` (`@Transactional(readOnly = true)`, 3 consultas sin N+1 según research R11)
- [ ] T050 [US4] Crear `M/reporte/controller/ReporteController.java` con `GET /reportes`, que recibe `@RequestParam RangoFechas fecha` y `@RequestParam UUID cliente`, con anotaciones de springdoc

**Checkpoint**: Las 4 historias completas.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T051 [P] Crear `ms-cuentas/Dockerfile` (multi-stage, igual que en ms-clientes, `EXPOSE 8082`) y `ms-cuentas/.dockerignore`
- [ ] T052 Actualizar `docker-compose.yml`:
  - Agregar el servicio `ms-cuentas`:
    - `build: ./ms-cuentas`, puerto `8082:8082`;
    - variables de entorno hacia `postgres:5432/cuentas_db` y `rabbitmq`;
    - `depends_on` de postgres y rabbitmq con estado healthy;
    - healthcheck con `wget`.
  - Hacer que `ms-clientes` dependa de `ms-cuentas` con estado healthy (research R8).
- [ ] T053 [P] Crear `BaseDatos.sql` en la raíz, consolidando:
  - las bases y usuarios (`docker/postgres/init`);
  - el esquema de `clientes_db` (V1 de ms-clientes);
  - el esquema de `cuentas_db` (V1 de ms-cuentas).

  Con comentarios por sección y `\connect` entre bases.
- [ ] T054 [P] Crear `postman/reto-banco-e2e.postman_collection.json`:
  - Variables `clientesUrl` y `cuentasUrl`.
  - Caso de uso 1 en ms-clientes y, tras una espera de 1 s en el pre-request, los casos 2–5 en ms-cuentas.
  - Casos de error: saldo no disponible, número duplicado, rango inválido.
  - Tests de estado y saldos en cada request.
  - Usa los números de cuenta literales del enunciado. La descripción de la colección indica ejecutarla sobre una base limpia (`docker compose down -v && docker compose up -d --build`).
- [ ] T055 [P] Crear `ms-cuentas/README.md` (requisitos, pruebas, API, mensajería y DLQ, decisiones y mejoras futuras) y el `README.md` raíz:
  - arquitectura de la solución (diagrama de los 2 servicios con Postgres y RabbitMQ);
  - despliegue con `docker compose up -d --build`;
  - URLs y orden de ejecución de las colecciones Postman;
  - mapa F1–F7 → dónde se cumple;
  - rendimiento, escalabilidad y resiliencia, incluido el compromiso de arranque: ms-clientes espera a que ms-cuentas esté healthy (research R8);
  - mejoras futuras;
  - cómo reiniciar la base antes de repetir la colección E2E.
- [ ] T056 Revisión de cumplimiento de la constitución en ms-cuentas:
  - Buscar con grep que no haya `@Data`, `@Setter` ni `@EqualsAndHashCode` en las entidades.
  - Verificar que no haya `double` ni `float` en montos.
  - Verificar que ningún controller exponga entidades o use repositorios.
  - Verificar que no queden TODOs ni código comentado.
- [ ] T057 Ejecutar `./mvnw test` en `ms-cuentas/` y en `ms-clientes/` (con Docker encendido): todas las pruebas en verde
- [ ] T058 Validar quickstart.md contra `docker compose up -d --build`:
  - Escenarios 1–11 (incluidos 5b y 10b) con `curl`.
  - Verificación de la DLQ con un mensaje inválido.
  - SC-005: detener el contenedor `ms-clientes` y confirmar que ms-cuentas sigue registrando movimientos y generando reportes.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup → Foundational**, que **bloquea** todas las historias.
- **Orden interno de Foundational**:
  - T009 (rojo) → T010;
  - T011 → T012;
  - T013 (rojo) → T014 → T015.
- **US1, US2 y US3**: dependen solo de Foundational y son independientes entre sí. US1 usa
  `Cuenta` (de Foundational) para desactivar cuentas. US2–US4 siembran clientes con `ClientesDePrueba`.
- **US3**: modifica `Cuenta.java` (T039) y `CuentaTest.java` (T032), por lo que no corre en paralelo con
  tareas de US2 que toquen esos archivos.
- **US4**: depende de US3 (necesita `Movimiento` y `MovimientoRepository.findDelReporte`).
- **Polish**: depende de todas las historias.

### Recommended Order

Setup → Foundational → US1 → US2 → US3 → US4 → Polish (un checkpoint y un commit por fase).

### Parallel Opportunities

- **Foundational**:
  - T003, T005, T006, T007 y T008 en paralelo;
  - T011 en paralelo con T009 y T013.
- **Cada historia**: todas las pruebas [P] en paralelo; luego los DTOs y excepciones [P].
- **Polish**: T051, T053, T054 y T055.

---

## Parallel Example: User Story 3

```bash
# Pruebas primero (T032 va antes y es secuencial: TDD sobre CuentaTest):
Task: "T033 MovimientoServiceImplTest en T/movimiento/service/"
Task: "T034 MovimientoControllerTest en T/movimiento/controller/"
Task: "T035 MovimientoConcurrenciaIntegrationTest en T/movimiento/"
Task: "T036 movimientos.feature en TR/karate/cuentas/"

# Luego, en paralelo:
Task: "T037 TipoMovimiento y excepciones"
Task: "T041 DTOs y MovimientoMapper"
```

---

## Implementation Strategy

### MVP

Setup + Foundational + US2 + US3: cuentas y movimientos con "Saldo no disponible" (F1 parcial,
F2, F3), con los clientes sembrados. Después US1 (comunicación asíncrona real) y US4 (F4).

### Incremental Delivery

1. Foundational: dominio de Cuenta con TDD, `RangoFechas` y manejo de errores.
2. US1: réplica de clientes por eventos (resiliencia: idempotencia, reintentos, DLQ).
3. US2: cuentas (casos de uso 2 y 3).
4. US3: movimientos (caso de uso 4, F2, F3, concurrencia, caso de uso 5 plano).
5. US4: reporte (F4).
6. Polish: Docker de la solución completa (F7), `BaseDatos.sql`, Postman E2E y README raíz.

Commits con Conventional Commits: `test:` para las pruebas en rojo y `feat:` para la implementación en verde.

---

## Notes

- Verificar que cada prueba falle antes de implementar, especialmente T009, T011, T013 y T032 (TDD).
- `casos-de-uso.feature` lo extienden T026, T036 y T046, en ese orden: por eso esas tareas no llevan [P].
- Montos siempre con `BigDecimal` y `compareTo`; nunca `double`.
- El mensaje "Saldo no disponible" debe ser exacto en la excepción, en `title` y en `detail`.
- Newman no está disponible en este entorno: la validación final se hace con `curl`, y la colección
  Postman se entrega para ejecutarla en Postman.

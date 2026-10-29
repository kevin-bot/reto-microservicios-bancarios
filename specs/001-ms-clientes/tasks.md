---
description: "Lista de tareas para implementar ms-clientes"
---

# Tasks: Microservicio de Clientes (ms-clientes)

**Input**: Design documents from `/specs/001-ms-clientes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Requeridos por la constitución (Principio VI). TDD obligatorio en la entidad `Cliente`
(F5); en el resto, las pruebas de cada historia se escriben primero y deben fallar antes de
implementar.

**Organization**: Tareas agrupadas por historia de usuario.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1–US4)

## Path Conventions

- `M/` = `ms-clientes/src/main/java/com/banco/clientes/`
- `R/` = `ms-clientes/src/main/resources/`
- `T/` = `ms-clientes/src/test/java/com/banco/clientes/`
- `TR/` = `ms-clientes/src/test/resources/`
- Rutas sin prefijo son relativas a la raíz del repositorio.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicializar el proyecto Maven y la infraestructura local.

- [ ] T001 Generar el proyecto Spring Boot **3.5.x** en `ms-clientes/` (groupId `com.banco`, artifactId `ms-clientes`, paquete `com.banco.clientes`, Java 21, packaging jar) con Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`) y la clase `M/MsClientesApplication.java`
- [ ] T002 Configurar dependencias en `ms-clientes/pom.xml`:
  - **main**: `spring-boot-starter-web`, `-data-jpa`, `-validation`, `-amqp`, `-actuator`; `flyway-core`, `flyway-database-postgresql`, `postgresql` (runtime); `spring-security-crypto`; `mapstruct` 1.6.x; `lombok`; `springdoc-openapi-starter-webmvc-ui` 2.8.x.
  - **test**: `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:rabbitmq`, `org.testcontainers:junit-jupiter`, `io.karatelabs:karate-junit5` 1.5.x.
  - **annotationProcessorPaths** de `maven-compiler-plugin` en este orden: `lombok`, `lombok-mapstruct-binding`, `mapstruct-processor`.
- [ ] T003 Crear `R/application.yml`:
  - **Servidor**: `server.port=8081`, `server.servlet.context-path=/api`.
  - **Datasource** por variables de entorno con defaults locales (`DB_URL=jdbc:postgresql://localhost:5432/clientes_db`, `DB_USER=clientes_user`, `DB_PASSWORD=clientes_pass`).
  - **JPA**: `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, Flyway habilitado.
  - **RabbitMQ** por variables de entorno (`RABBIT_HOST=localhost`, `RABBIT_PORT=5672`, `RABBIT_USER=guest`, `RABBIT_PASSWORD=guest`).
  - **Errores y JSON**: `spring.mvc.problemdetails.enabled=true`, `spring.jackson.deserialization.fail-on-unknown-properties=true`.
  - **Paginación**: `spring.data.web.pageable.max-page-size=100`.
  - **Otros**: exponer `health` en Actuator; `springdoc.swagger-ui.path=/swagger-ui.html`.
- [ ] T004 [P] Crear `.gitignore` en la raíz (`target/`, `.idea/`, `*.iml`, `.vscode/`, `*.log`, `.DS_Store`)
- [ ] T005 [P] Crear `docker/postgres/init/01-crear-bases.sql`, que crea los usuarios `clientes_user` y `cuentas_user` y las bases `clientes_db` (dueño `clientes_user`) y `cuentas_db` (dueño `cuentas_user`)
- [ ] T006 [P] Crear `docker-compose.yml` en la raíz con:
  - `postgres` (`postgres:16-alpine`, puerto 5432, volumen con `docker/postgres/init`, healthcheck `pg_isready`).
  - `rabbitmq` (`rabbitmq:3.13-management`, puertos 5672 y 15672, healthcheck `rabbitmq-diagnostics -q ping`).

  El servicio ms-clientes se agrega en T055.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entidad de dominio (con TDD), esquema, mensajería y manejo de errores que usan todas las historias.

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta completar esta fase.

### Excepciones y tipos base

- [ ] T007 [P] Crear `M/shared/exception/ReglaNegocioException.java` (abstracta, extiende `RuntimeException`) y `M/shared/exception/DomainValidationException.java` (extiende `ReglaNegocioException`)
- [ ] T008 [P] Crear las excepciones del cliente:
  - `M/cliente/exception/ClienteNoEncontradoException.java` (recibe `UUID clienteId`; mensaje "No existe un cliente con id {clienteId}").
  - `M/cliente/exception/IdentificacionDuplicadaException.java` (mensaje "Ya existe un cliente con identificación {identificacion}").
  - `M/cliente/exception/ClienteEliminadoException.java` (extiende `ReglaNegocioException`).
- [ ] T009 [P] Crear el enum `M/cliente/entity/Genero.java` con `MASCULINO`, `FEMENINO`, `OTRO`

### Esquema

- [ ] T010 Crear `R/db/migration/V1__crear_tablas_persona_cliente.sql` según data-model.md:
  - **persona**: `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, `nombre VARCHAR(100) NOT NULL`, `genero VARCHAR(10) NOT NULL`, `edad INTEGER NOT NULL CONSTRAINT ck_persona_edad CHECK (edad BETWEEN 18 AND 120)`, `identificacion VARCHAR(20) NOT NULL CONSTRAINT uk_persona_identificacion UNIQUE`, `direccion VARCHAR(200) NOT NULL`, `telefono VARCHAR(15) NOT NULL`.
  - **cliente**: `persona_id BIGINT PRIMARY KEY REFERENCES persona(id)`, `cliente_id UUID NOT NULL CONSTRAINT uk_cliente_cliente_id UNIQUE`, `contrasena VARCHAR(100) NOT NULL`, `estado BOOLEAN NOT NULL DEFAULT TRUE`, `eliminado BOOLEAN NOT NULL DEFAULT FALSE`, `fecha_creacion TIMESTAMPTZ NOT NULL`, `fecha_actualizacion TIMESTAMPTZ NOT NULL`.
  - **Índice**: `idx_cliente_eliminado_estado (eliminado, estado)`.

### Entidad de dominio (TDD — F5)

- [ ] T011 **(TDD: escribir primero y verificar que falla)** Crear `T/cliente/entity/ClienteTest.java`. Es una prueba unitaria pura, sin Spring, con AssertJ, Given/When/Then, `@DisplayName` en español y nombres `metodo_cuandoCondicion_debeResultado`. Debe cubrir:
  - `crear` con datos válidos: genera `clienteId` no nulo, `estado` por defecto `true` y `eliminado=false`.
  - `crear` con datos inválidos lanza `DomainValidationException` en cada uno de estos casos:
    - edad 17 o 121 (regla "18–120");
    - identificación que no cumple `^[A-Za-z0-9]{5,20}$`;
    - teléfono que no cumple `^[0-9]{7,15}$`;
    - nombre vacío o de más de 100 caracteres;
    - dirección vacía o de más de 200 caracteres;
    - hash de contraseña vacío;
    - género nulo.
  - `desactivar` deja `estado=false` y `activar` deja `estado=true`.
  - `eliminar` deja `eliminado=true` y `estado=false`.
  - `activar`, `desactivar` o `eliminar` sobre un cliente eliminado lanza `ClienteEliminadoException`.
  - `cambiarContrasena` reemplaza el hash; un hash vacío lanza excepción.
  - `actualizarDatosPersonales` y `actualizarContacto` aplican sus cambios y validan las mismas reglas.
- [ ] T012 Crear la entidad abstracta `M/cliente/entity/Persona.java`:
  - `@Entity`, `@Table(name="persona")`, `@Inheritance(strategy = JOINED)`.
  - Campos según data-model.md; `genero` con `@Enumerated(STRING)`.
  - Lombok: solo `@Getter` y `@NoArgsConstructor(access = PROTECTED)`. **Prohibidos `@Data`, `@Setter` y `@EqualsAndHashCode`**.
  - Métodos `actualizarDatosPersonales(nombre, genero, edad, identificacion)` y `actualizarContacto(direccion, telefono)` con validación de invariantes.
- [ ] T013 Crear la entidad `M/cliente/entity/Cliente.java` (extiende `Persona`, `@Table(name="cliente")`, `@PrimaryKeyJoinColumn(name="persona_id")`) hasta que `ClienteTest` pase:
  - Fábrica estática `crear(...)` que genera `UUID.randomUUID()`.
  - Métodos `activar()`, `desactivar()`, `cambiarContrasena(String hash)`, `eliminar()`, `estaEliminado()`.
  - `@CreationTimestamp` en `fechaCreacion` y `@UpdateTimestamp` en `fechaActualizacion`.
  - Mismas restricciones de Lombok que `Persona`.

### Persistencia, seguridad y mensajería

- [ ] T014 Crear `M/cliente/repository/ClienteRepository.java` (`JpaRepository<Cliente, Long>` y `JpaSpecificationExecutor<Cliente>`) con:
  - `findByClienteIdAndEliminadoFalse(UUID)`
  - `existsByIdentificacion(String)`
  - `existsByIdentificacionAndClienteIdNot(String, UUID)`
- [ ] T015 [P] Crear `M/shared/config/PasswordEncoderConfig.java` con un bean `PasswordEncoder` = `new BCryptPasswordEncoder(10)`
- [ ] T016 [P] Crear `M/shared/config/RabbitConfig.java` con:
  - Bean `TopicExchange` `clientes.exchange` (durable, constante pública `CLIENTES_EXCHANGE`).
  - Bean `Jackson2JsonMessageConverter`.
  - `RabbitTemplate` configurado con ese converter.
- [ ] T017 [P] Crear los tipos de evento según contracts/eventos-cliente.md:
  - `M/cliente/event/TipoEventoCliente.java`: enum `CLIENTE_CREADO("cliente.creado")`, `CLIENTE_ACTUALIZADO("cliente.actualizado")`, `CLIENTE_ELIMINADO("cliente.eliminado")`, con su routing key.
  - `M/cliente/event/ClienteEvento.java`: record con `eventId`, `tipo`, `fechaOcurrencia`, `clienteId`, `nombre`, `estado`, y fábrica `desde(Cliente, TipoEventoCliente)`.
  - `M/cliente/event/ClienteEventoDominio.java`: record de evento de aplicación que envuelve un `ClienteEvento`.
- [ ] T018 **(Test primero)** Crear `T/cliente/event/ClienteEventPublisherTest.java` (Mockito sobre `RabbitTemplate`) que verifique:
  - Envía a `clientes.exchange` con la routing key del tipo y el payload `ClienteEvento`.
  - Si `RabbitTemplate` lanza `AmqpException`, no propaga la excepción (solo registra en log).
- [ ] T019 Crear `M/cliente/event/ClienteEventPublisher.java`, un `@Component` con un método `@TransactionalEventListener(phase = AFTER_COMMIT)` que recibe `ClienteEventoDominio`, llama a `rabbitTemplate.convertAndSend(...)` y ante `AmqpException` hace `log.error` con el `eventId` sin relanzar
- [ ] T020 Crear `M/shared/exception/GlobalExceptionHandler.java` (`@RestControllerAdvice`, extiende `ResponseEntityExceptionHandler`) que devuelva `ProblemDetail` según research R9:

  | Excepción | HTTP | `title` |
  |---|---|---|
  | Validación (`MethodArgumentNotValidException`, `HandlerMethodValidationException`) | 400 | "Datos inválidos", con propiedad `errores` (campo → mensaje) |
  | `HttpMessageNotReadableException` | 400 | "Solicitud mal formada" |
  | `DomainValidationException` | 400 | "Datos inválidos" |
  | `MethodArgumentTypeMismatchException` (UUID inválido) y `PropertyReferenceException` (`sort` inválido) | 400 | "Parámetro inválido" |
  | `ClienteNoEncontradoException` | 404 | "Cliente no encontrado" |
  | `IdentificacionDuplicadaException`, y `DataIntegrityViolationException` **solo** si el nombre de la restricción violada es `uk_persona_identificacion` | 409 | "Identificación duplicada" |
  | `ReglaNegocioException` (p. ej. `ClienteEliminadoException`) | 422 | "Regla de negocio incumplida" |
  | `Exception` y cualquier otra `DataIntegrityViolationException` | 500 | "Error interno", sin detalle (con `log.error`) |
- [ ] T021 [P] Crear `M/cliente/dto/ClienteResponse.java`, un record con `clienteId`, `nombre`, `genero`, `edad`, `identificacion`, `direccion`, `telefono` y `estado`. **Sin `contrasena`.** También crear `M/cliente/mapper/ClienteMapper.java` (MapStruct, `componentModel = "spring"`) con el método `toResponse(Cliente)`
- [ ] T022 [P] Crear `M/shared/config/OpenApiConfig.java` con el título "ms-clientes API", la versión 1.0.0 y la descripción
- [ ] T023 [P] Crear `T/support/TestcontainersConfig.java` (`@TestConfiguration(proxyBeanMethods = false)`) con beans `@ServiceConnection` de `PostgreSQLContainer("postgres:16-alpine")` y `RabbitMQContainer("rabbitmq:3.13-management")`

**Checkpoint**: `./mvnw test` pasa `ClienteTest` y `ClienteEventPublisherTest`, y la aplicación arranca contra el compose (Flyway valida el esquema).

---

## Phase 3: User Story 1 - Registrar un cliente (Priority: P1) 🎯 MVP

**Goal**: Alta de clientes con contraseña hasheada, `clienteId` generado y evento `CLIENTE_CREADO`.

**Independent Test**: Registrar los 3 clientes del caso de uso 1 y comprobar que la identificación duplicada da 409 y la edad 17 da 400.

### Tests for User Story 1 ⚠️ (escribir primero; deben fallar)

- [ ] T024 [P] [US1] En `T/cliente/service/ClienteServiceImplTest.java` (Mockito: `ClienteRepository`, `PasswordEncoder`, `ApplicationEventPublisher`, `ClienteMapper`), probar `crear`:
  - hashea la contraseña con `PasswordEncoder` y nunca guarda el texto plano;
  - guarda el cliente;
  - publica un `ClienteEventoDominio` de tipo `CLIENTE_CREADO`;
  - si `existsByIdentificacion` es `true`, lanza `IdentificacionDuplicadaException` y no guarda ni publica.
- [ ] T025 [P] [US1] En `T/cliente/controller/ClienteControllerTest.java` (`@WebMvcTest(ClienteController.class)`, `@MockitoBean ClienteService`, importa `GlobalExceptionHandler`), probar `POST /clientes`:
  - **201** con el header `Location` y un cuerpo sin la clave `contrasena`.
  - **400** con `errores.edad` para edad 17.
  - **400** para una identificación `"ab-1"`.
  - **400** si faltan campos obligatorios.
  - **400** para un JSON mal formado.
  - **409** cuando el service lanza `IdentificacionDuplicadaException`.
- [ ] T026 [P] [US1] Crear el runner `T/karate/ClientesKarateTest.java`:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)` con `@Import(TestcontainersConfig.class)`.
  - Un método `@Karate.Test` que fija la propiedad del sistema `karate.baseUrl = http://localhost:{port}/api`.

  Crear también `TR/karate/clientes/clientes.feature` con el escenario "Caso de uso 1":
  - Crear a Jose Lema, Marianela Montalvo y Juan Osorio con direcciones, teléfonos y contraseñas del enunciado, más género, edad (≥ 18) e identificación válidos.
  - Validar el 201 y que la respuesta no incluya `contrasena`.

  Y el escenario "Identificación duplicada": responde 409.
- [ ] T027 [P] [US1] Crear `T/cliente/ClienteIntegrationTest.java` (`@SpringBootTest`, `@Import(TestcontainersConfig.class)`, `MockMvc` o `TestRestTemplate`) que declare una cola temporal enlazada a `clientes.exchange` con `cliente.*` y lea los mensajes con `rabbitTemplate.receiveAndConvert(cola, 5000)` (SC-005: menos de 5 s). Debe verificar:
  - Un POST válido persiste la fila, la contraseña queda guardada como hash BCrypt (empieza con `$2`) y llega exactamente un mensaje `CLIENTE_CREADO` sin contraseña.
  - Un POST con identificación duplicada no publica ningún mensaje.

### Implementation for User Story 1

- [ ] T028 [P] [US1] Crear el record `M/cliente/dto/ClienteCreateRequest.java` con Bean Validation tal como en contracts/clientes-api.yaml:

  | Campo | Restricciones |
  |---|---|
  | `nombre` | `@NotBlank @Size(max=100)` |
  | `genero` | `@NotNull` |
  | `edad` | `@NotNull @Min(18) @Max(120)` |
  | `identificacion` | `@NotBlank @Pattern("^[A-Za-z0-9]{5,20}$")` |
  | `direccion` | `@NotBlank @Size(max=200)` |
  | `telefono` | `@NotBlank @Pattern("^[0-9]{7,15}$")` |
  | `contrasena` | `@NotBlank @Size(min=4, max=72)` |
  | `estado` | `Boolean`; si es `null`, se toma `true` |
- [ ] T029 [US1] Crear la interfaz `M/cliente/service/ClienteService.java` con `ClienteResponse crear(ClienteCreateRequest)`, y su implementación `M/cliente/service/ClienteServiceImpl.java`:
  - `@Transactional`.
  - Valida duplicados con `existsByIdentificacion`.
  - Hashea con `PasswordEncoder` y crea el cliente con `Cliente.crear(...)`.
  - Hace `save` y publica `ClienteEventoDominio(ClienteEvento.desde(cliente, CLIENTE_CREADO))`.
  - Devuelve `mapper.toResponse(...)`.
  - Registra `log.info` solo con el `clienteId`.
- [ ] T030 [US1] Crear `M/cliente/controller/ClienteController.java` (`@RestController`, `@RequestMapping("/clientes")`, depende solo de la interfaz `ClienteService`) con `POST` y `@Valid`. Debe responder `201 Created` con `Location: /api/clientes/{clienteId}` y anotaciones de springdoc (`@Operation`, `@ApiResponse`)

**Checkpoint**: T024–T027 en verde. La US1 es demostrable por sí sola: es el MVP.

---

## Phase 4: User Story 2 - Consultar clientes (Priority: P1)

**Goal**: Consultar un cliente por `clienteId` y listar clientes con paginación y filtros por `estado` e `identificacion`, excluyendo siempre los eliminados.

**Independent Test**: Tras crear clientes:
- consultar uno por id (200);
- consultar un id inexistente (404);
- listar (200, con `totalElementos` correcto);
- filtrar por `identificacion` (devuelve un solo resultado).

### Tests for User Story 2 ⚠️

- [ ] T031 [P] [US2] En `T/cliente/service/ClienteServiceImplTest.java`, agregar pruebas de:
  - `obtener`: devuelve el response si existe, y lanza `ClienteNoEncontradoException` si `findByClienteIdAndEliminadoFalse` está vacío.
  - `listar`: delega en `findAll(Specification, Pageable)` y devuelve un `PaginaResponse`.
- [ ] T032 [P] [US2] En `T/cliente/controller/ClienteControllerTest.java`, agregar pruebas de:
  - `GET /clientes/{id}`: 200, 404, y 400 si el UUID es inválido.
  - `GET /clientes?page=0&size=20&estado=true&identificacion=1712345678`: 200, con la estructura `contenido`, `pagina`, `tamano`, `totalElementos`, `totalPaginas`.
  - `GET /clientes?sort=campoInexistente`: 400 "Parámetro inválido" (no 500).
- [ ] T033 [P] [US2] En `TR/karate/clientes/clientes.feature`, agregar escenarios para:
  - consultar por id (200, sin `contrasena`);
  - id inexistente (404);
  - listado paginado;
  - filtro por `identificacion`, que devuelve exactamente 1 elemento.

### Implementation for User Story 2

- [ ] T034 [P] [US2] Crear el record `M/shared/dto/PaginaResponse.java` `<T>` con `contenido`, `pagina`, `tamano`, `totalElementos`, `totalPaginas` y una fábrica `static <T> PaginaResponse<T> de(Page<T>)`
- [ ] T035 [P] [US2] Crear `M/cliente/repository/ClienteSpecifications.java` con:
  - `noEliminado()`, que se aplica siempre;
  - `conEstado(Boolean)` y `conIdentificacion(String)`, que devuelven `null` si el parámetro es `null` (filtro opcional).
- [ ] T036 [US2] Agregar `obtener(UUID)` y `listar(Boolean estado, String identificacion, Pageable)` a `M/cliente/service/ClienteService.java` y a `ClienteServiceImpl.java` (`@Transactional(readOnly = true)`)
- [ ] T037 [US2] Agregar a `M/cliente/controller/ClienteController.java`:
  - `GET /{clienteId}`;
  - `GET` de listado con `@PageableDefault(size = 20, sort = "nombre")` y `@RequestParam(required = false)` para `estado` e `identificacion`.

**Checkpoint**: US1 y US2 funcionan juntas y por separado.

---

## Phase 5: User Story 3 - Actualizar un cliente (Priority: P2)

**Goal**: Actualización completa con PUT (sin contraseña) y parcial con PATCH (incluye cambio de contraseña y de estado), con el evento `CLIENTE_ACTUALIZADO`.

**Independent Test**: PATCH de la dirección (solo cambia la dirección), PATCH `{"estado": false}` (queda inactivo), PUT con `contrasena` (400) y PATCH `{}` (400).

### Tests for User Story 3 ⚠️

- [ ] T038 [P] [US3] Crear `T/shared/validation/AlMenosUnCampoValidatorTest.java`: un record con todos los campos `null` es inválido, y con al menos un campo no nulo es válido
- [ ] T039 [P] [US3] En `T/cliente/service/ClienteServiceImplTest.java`, agregar pruebas de `actualizar` (PUT) y `actualizarParcial` (PATCH):
  - aplican los cambios mediante métodos de dominio;
  - `estado=true/false` invoca `activar()` o `desactivar()`;
  - una contraseña en PATCH se hashea;
  - una identificación de otro cliente (`existsByIdentificacionAndClienteIdNot`) lanza `IdentificacionDuplicadaException`;
  - un cliente inexistente lanza `ClienteNoEncontradoException`;
  - publica `CLIENTE_ACTUALIZADO`;
  - PATCH no modifica los campos `null`.
- [ ] T040 [P] [US3] En `T/cliente/controller/ClienteControllerTest.java`, agregar pruebas de:
  - PUT válido: 200.
  - PUT con `contrasena`: 400 "Solicitud mal formada".
  - PATCH `{"direccion":"Amazonas y NNUU"}`: 200.
  - PATCH `{}`: 400.
  - PATCH con edad 17: 400.
  - Identificación duplicada: 409.
  - Cliente inexistente: 404.
- [ ] T041 [P] [US3] En `TR/karate/clientes/clientes.feature`, agregar escenarios para:
  - PATCH de dirección: se verifica con un GET posterior.
  - PATCH de estado a `false`.
  - PATCH de contraseña: 200, sin `contrasena` en la respuesta.
  - PUT completo: 200.
  - PUT con `contrasena`: 400.

### Implementation for User Story 3

- [ ] T042 [P] [US3] Crear `M/shared/validation/AlMenosUnCampo.java` (anotación de clase con Bean Validation) y `M/shared/validation/AlMenosUnCampoValidator.java` (recorre los componentes del record por reflexión y es válido si al menos uno no es `null`)
- [ ] T043 [P] [US3] Crear el record `M/cliente/dto/ClienteUpdateRequest.java` con `nombre`, `genero`, `edad`, `identificacion`, `direccion`, `telefono` y `estado`, **sin `contrasena`**:
  - todos los campos obligatorios;
  - las mismas restricciones que en T028;
  - `estado` con `@NotNull`.
- [ ] T044 [P] [US3] Crear el record `M/cliente/dto/ClientePatchRequest.java` anotado con `@AlMenosUnCampo`:
  - todos los campos son opcionales, incluida `contrasena` (`@Size(min=4, max=72)`);
  - cada campo, si viene, cumple las restricciones de T028 (sin `@NotNull`).
- [ ] T045 [US3] Agregar `actualizar(UUID, ClienteUpdateRequest)` y `actualizarParcial(UUID, ClientePatchRequest)` a la interfaz `M/cliente/service/ClienteService.java` y a `ClienteServiceImpl.java`:
  - Validan la identificación duplicada solo si cambió.
  - Combinan los campos `null` con los valores actuales antes de llamar a `actualizarDatosPersonales` y `actualizarContacto`.
  - Aplican `estado` con `activar()` o `desactivar()`.
  - Hashean la contraseña y llaman a `cambiarContrasena(...)`.
  - Publican `CLIENTE_ACTUALIZADO`.
- [ ] T046 [US3] Agregar `PUT /{clienteId}` y `PATCH /{clienteId}` con `@Valid` a `M/cliente/controller/ClienteController.java`

**Checkpoint**: US1, US2 y US3 funcionan.

---

## Phase 6: User Story 4 - Eliminar un cliente (Priority: P3)

**Goal**: Eliminación lógica con el evento `CLIENTE_ELIMINADO`; la identificación queda reservada.

**Independent Test**: DELETE (204), luego GET (404), DELETE de nuevo (404) y POST con la misma identificación (409).

### Tests for User Story 4 ⚠️

- [ ] T047 [P] [US4] En `T/cliente/service/ClienteServiceImplTest.java`, agregar pruebas de `eliminar`:
  - invoca `cliente.eliminar()`;
  - publica `CLIENTE_ELIMINADO` con `estado=false`;
  - si el cliente no existe, lanza `ClienteNoEncontradoException`.
- [ ] T048 [P] [US4] En `T/cliente/controller/ClienteControllerTest.java`, agregar pruebas de `DELETE /clientes/{id}`: 204 y 404
- [ ] T049 [P] [US4] En `TR/karate/clientes/clientes.feature`, agregar el escenario "Eliminación lógica": DELETE (204), GET (404), PATCH (404), PUT (404), DELETE (404), POST con la misma identificación (409) y el listado ya no lo incluye (FR-012a)
- [ ] T050 [P] [US4] En `T/cliente/ClienteIntegrationTest.java`, agregar el ciclo completo: crear, PATCH y DELETE publican exactamente 3 mensajes (`CLIENTE_CREADO`, `CLIENTE_ACTUALIZADO`, `CLIENTE_ELIMINADO`) con `eventId` distintos, cada uno recibido en menos de 5 s (`receiveAndConvert(cola, 5000)`), y la fila sigue en BD con `eliminado = true`

### Implementation for User Story 4

- [ ] T051 [US4] Agregar `void eliminar(UUID)` a la interfaz `M/cliente/service/ClienteService.java` y a `ClienteServiceImpl.java`: busca el cliente, llama a `cliente.eliminar()` y publica `CLIENTE_ELIMINADO`
- [ ] T052 [US4] Agregar `DELETE /{clienteId}` a `M/cliente/controller/ClienteController.java`, que responde `204 No Content`

**Checkpoint**: CRUD completo y las 4 historias verificadas.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T053 [P] Crear `ms-clientes/Dockerfile` multi-stage:
  - **Etapa de build**: `maven:3.9-eclipse-temurin-21`, copia el `pom.xml`, ejecuta `dependency:go-offline`, copia `src` y ejecuta `package -DskipTests`.
  - **Etapa de runtime**: `eclipse-temurin:21-jre-alpine`, usuario no root, `EXPOSE 8081`, `ENTRYPOINT java -jar app.jar`.
- [ ] T054 [P] Crear `ms-clientes/.dockerignore` que excluya `target/`, `.idea/` y `*.iml`
- [ ] T055 Agregar el servicio `ms-clientes` a `docker-compose.yml`:
  - `build: ./ms-clientes`, puerto `8081:8081`.
  - Variables de entorno `DB_URL=jdbc:postgresql://postgres:5432/clientes_db`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST=rabbitmq`.
  - `depends_on` de `postgres` y `rabbitmq` con `condition: service_healthy`.
  - Healthcheck con `wget -qO- http://localhost:8081/api/actuator/health`.
- [ ] T056 [P] Crear `postman/ms-clientes.postman_collection.json`:
  - Variable `baseUrl = http://localhost:8081/api`.
  - Requests para el caso de uso 1 (3 clientes) y para los escenarios 1–11 del quickstart.
  - Un script de test que guarda `clienteId` en una variable y verifica códigos de estado y la ausencia de `contrasena`.
- [ ] T057 [P] Crear `ms-clientes/README.md` con:
  - requisitos (Java 21, Docker);
  - `./mvnw test` (con Docker encendido para Testcontainers);
  - ejecución con `docker compose up -d --build`;
  - URLs de Swagger y de RabbitMQ;
  - resumen de endpoints y eventos;
  - decisiones de arquitectura y mejoras futuras: Transactional Outbox, bloqueo optimista y validación de cédula.
- [ ] T058 Revisión de cumplimiento de la constitución:
  - Buscar con grep que no haya `@Data`, `@Setter` ni `@EqualsAndHashCode` en `M/cliente/entity/`.
  - Verificar que ninguna entidad se exponga en controllers.
  - Verificar que `contrasena` no aparezca en ningún DTO de respuesta ni en los eventos.
  - Verificar que no queden código comentado ni TODOs huérfanos.
- [ ] T059 Ejecutar `./mvnw test` en `ms-clientes/` (con Docker encendido): todas las pruebas en verde (unitarias, `@WebMvcTest`, integración y Karate)
- [ ] T060 Ejecutar la validación de quickstart.md: `docker compose up -d --build`, los escenarios 1–11 con la colección Postman y la verificación de eventos en el RabbitMQ management

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup y **bloquea todas las historias**.
  - Orden interno: T011 (test en rojo) → T012 → T013 (verde).
  - T018 → T019.
  - T014 depende de T013.
- **US1 (Phase 3)**: depende de Foundational.
- **US2, US3 y US4 (Phases 4–6)**: dependen de Foundational y de que exista `ClienteService` y `ClienteController` (T029, T030). Se recomienda hacerlas en orden de prioridad: US1 → US2 → US3 → US4.
- **Polish (Phase 7)**: depende de las historias que se vayan a entregar.

### Within Each User Story

- Pruebas primero, que deben fallar; luego DTOs, service y controller.
- `ClienteService`, `ClienteServiceImpl`, `ClienteController`, `ClienteServiceImplTest`, `ClienteControllerTest` y `clientes.feature` se **comparten entre historias**. Las tareas que tocan el mismo archivo no van en paralelo entre historias.

### Parallel Opportunities

- **Setup**: T004, T005 y T006.
- **Foundational**:
  - T007, T008 y T009 en paralelo;
  - T015, T016 y T017 en paralelo;
  - T021, T022 y T023 en paralelo.
- **Dentro de cada historia**: todas las tareas de prueba [P] en paralelo, porque usan archivos distintos o agregan métodos independientes; también los DTOs [P].
- **Polish**: T053, T054, T056 y T057.

---

## Parallel Example: User Story 1

```bash
# Pruebas primero (deben fallar):
Task: "T024 ClienteServiceImplTest.crear en T/cliente/service/ClienteServiceImplTest.java"
Task: "T025 ClienteControllerTest POST en T/cliente/controller/ClienteControllerTest.java"
Task: "T026 Karate caso de uso 1 en TR/karate/clientes/clientes.feature"
Task: "T027 ClienteIntegrationTest en T/cliente/ClienteIntegrationTest.java"

# Luego, en paralelo con lo anterior:
Task: "T028 ClienteCreateRequest en M/cliente/dto/ClienteCreateRequest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup y Phase 2 Foundational (incluye el TDD de `ClienteTest`, F5).
2. Phase 3 US1: registrar clientes con evento `CLIENTE_CREADO` e integración con Testcontainers (F6).
3. **Validar**: `./mvnw test` en verde y el caso de uso 1 por Karate.

### Incremental Delivery

1. US1: alta (MVP).
2. US2: consulta y listado con filtros.
3. US3: PUT y PATCH.
4. US4: eliminación lógica, con lo que se completa el CRUD de F1.
5. Polish: Docker (F7), Postman y README.

Se recomienda un commit por tarea o grupo lógico, con Conventional Commits (`test:` para las pruebas en rojo y `feat:` para la implementación que las pone en verde).

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- Verificar que cada prueba falle antes de implementar (especialmente T011, por el TDD obligatorio).
- La contraseña jamás debe aparecer en respuestas, logs ni eventos.
- Detenerse en cada checkpoint para validar la historia de forma independiente.

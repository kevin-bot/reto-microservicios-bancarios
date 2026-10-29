# Research: Microservicio de Clientes (ms-clientes)

**Feature**: `001-ms-clientes` | **Date**: 2026-10-28

Las decisiones de stack ya están fijadas por la constitución v1.0.0. Este documento resuelve las
decisiones de diseño concretas que la constitución deja abiertas.

## R1. Versión de Spring Boot

- **Decision**: Spring Boot **3.5.x** (última línea 3.x) con Java 21 y Maven Wrapper (`mvnw`).
- **Rationale**: La constitución fija Boot 3.x. La 3.5 trae `ProblemDetail`, Testcontainers con
  `@ServiceConnection` y soporte maduro de Flyway para PostgreSQL 16. El wrapper evita depender
  del Maven local (3.8.4).
- **Alternatives considered**: Spring Boot 4.x (descartado: viola la constitución y es demasiado
  nuevo para defender en entrevista); 3.3/3.4 (sin ventaja).

## R2. Herencia JPA Persona → Cliente

- **Decision**: `Persona` como clase `abstract` `@Entity` con
  `@Inheritance(strategy = InheritanceType.JOINED)`; `Cliente` extiende `Persona`. Tablas
  `persona` y `cliente`; `cliente.persona_id` es PK y FK hacia `persona.id`.
- **Rationale**: El enunciado pide que Persona tenga su PK y que Cliente "herede" con su propia
  clave. JOINED produce un esquema normalizado que refleja exactamente el modelo y permite
  agregar otros tipos de Persona sin columnas nulas.
- **Alternatives considered**: `SINGLE_TABLE` (columnas nulas, mezcla conceptos);
  `@MappedSuperclass` (Persona no sería entidad ni tendría tabla/PK propia, contradice el
  enunciado); `TABLE_PER_CLASS` (consultas polimórficas con UNION, innecesario).

## R3. Identificador de cliente (clienteId)

- **Decision**: `clienteId` de tipo **UUID** generado por la aplicación al crear el cliente,
  con restricción `UNIQUE` e inmutable. Es el identificador público en la API
  (`/clientes/{clienteId}`) y en los eventos. La PK técnica (`persona.id`, `BIGINT` identity)
  nunca se expone.
- **Rationale**: Separa la clave técnica de la clave de negocio; el UUID no es adivinable ni
  secuencial, y puede viajar en eventos sin coordinación entre servicios.
- **Alternatives considered**: Exponer la PK numérica (acopla la API al esquema, enumerable);
  código legible tipo `CLI-0001` (requiere secuencia adicional sin aportar valor al reto).

## R4. Eliminación lógica

- **Decision**: Columna `eliminado BOOLEAN NOT NULL DEFAULT FALSE` en `cliente`. El método de
  dominio `Cliente.eliminar()` marca `eliminado = true` y `estado = false`. Los repositorios
  exponen consultas explícitas que filtran (`findByClienteIdAndEliminadoFalse`,
  `findAllByEliminadoFalse(Pageable)`); la validación de identificación duplicada consulta
  **todas** las filas (`existsByIdentificacion`), de modo que la identificación de un eliminado
  queda reservada (FR-012b), reforzado por `UNIQUE` en BD.
- **Rationale**: Filtros explícitos en el nombre del método son visibles y testeables.
- **Alternatives considered**: `@SQLRestriction("eliminado = false")` de Hibernate (oculta el
  filtro, complica la verificación de duplicados que sí debe ver eliminados).

## R5. Publicación de eventos después del commit

- **Decision**: El service publica un evento de aplicación (`ClienteEventoDominio`) con
  `ApplicationEventPublisher`. Un `ClienteEventPublisher` con
  `@TransactionalEventListener(phase = AFTER_COMMIT)` lo convierte al mensaje de integración y lo
  envía con `RabbitTemplate` a un **topic exchange** `clientes.exchange` con routing keys
  `cliente.creado`, `cliente.actualizado`, `cliente.eliminado`. Serialización JSON con
  `Jackson2JsonMessageConverter`. Si el envío falla se registra en log con nivel ERROR y el
  cambio no se revierte (riesgo aceptado, outbox como mejora futura).
- **Rationale**: Cumple FR-015 y el Principio IV. El topic exchange permite que ms-cuentas (u
  otros) se suscriban por patrón (`cliente.*`) sin cambiar el productor.
- **Alternatives considered**: Publicar dentro de la transacción (puede emitir eventos de cambios
  revertidos); Transactional Outbox (diferido por constitución); Spring Cloud Stream (abstracción
  extra innecesaria para un solo broker).
- **Responsabilidad de topología**: ms-clientes declara solo el exchange. Colas, bindings, DLQ y
  reintentos los declara el consumidor (ms-cuentas, feature posterior).

## R6. Hash de contraseña

- **Decision**: `spring-security-crypto` con un bean `PasswordEncoder` (`BCryptPasswordEncoder`,
  strength 10). El service hashea antes de pasar el valor a la entidad
  (`Cliente.cambiarContrasena(String contrasenaHash)`); la entidad nunca ve texto plano.
- **Rationale**: BCrypt sin activar Spring Security completo (no hay autenticación en el alcance).
  Depender de la interfaz `PasswordEncoder` permite mockearlo en pruebas unitarias.
- **Alternatives considered**: `spring-boot-starter-security` (activa filtros y login por
  defecto, fuera de alcance); Argon2 (dependencia adicional, sin beneficio para el reto).

## R7. Actualización completa (PUT) vs parcial (PATCH)

- **Decision**: `PUT` con `ClienteUpdateRequest` (todos los campos editables obligatorios,
  **sin contraseña**). La contraseña solo se cambia por `PATCH`. Se activa globalmente
  `spring.jackson.deserialization.fail-on-unknown-properties=true`, de modo que enviar
  `contrasena` (o cualquier campo desconocido) en un PUT produce 400 "Solicitud mal formada".
  Una anotación por DTO no basta porque Spring Boot desactiva ese fallo por defecto. `PATCH` con `ClientePatchRequest` cuyos campos son todos opcionales
  (`null` = no cambiar); se valida con una restricción de clase propia `@AlMenosUnCampo` y Bean
  Validation sobre los campos presentes.
- **Rationale**: Semántica HTTP correcta y simple. Evita JSON Patch (RFC 6902), que es más
  complejo de explicar y validar.
- **Alternatives considered**: JSON Merge Patch con `JsonNode` (no se puede validar con Bean
  Validation fácilmente); un solo DTO para ambos (mezcla reglas de obligatoriedad).

## R8. Paginación del listado

- **Decision**: `GET /clientes?page=0&size=20&sort=nombre,asc&estado=true&identificacion=...`
  usando `Pageable` y filtros opcionales combinables construidos con `Specification` de Spring Data
  (`JpaSpecificationExecutor`); el filtro `eliminado = false` se aplica siempre. Respuesta con un
  record propio `PaginaResponse<T>(contenido, pagina, tamano, totalElementos, totalPaginas)`.
  Tamaño máximo 100.
- **Rationale**: Serializar `Page`/`PageImpl` directamente genera un JSON inestable (Spring
  advierte de ello); un record propio define un contrato estable.
- **Alternatives considered**: `PagedModel` de Spring HATEOAS (dependencia extra); lista sin
  paginar (no escala).

## R9. Manejo de errores

- **Decision**: `GlobalExceptionHandler` (`@RestControllerAdvice`, extiende
  `ResponseEntityExceptionHandler`) que devuelve `ProblemDetail`:

  | Excepción | HTTP | `title` |
  |---|---|---|
  | `MethodArgumentNotValidException` / `HandlerMethodValidationException` | 400 | Datos inválidos (con propiedad `errores` por campo) |
  | `HttpMessageNotReadableException` | 400 | Solicitud mal formada |
  | `DomainValidationException` (dato inválido detectado por la entidad) | 400 | Datos inválidos |
  | `MethodArgumentTypeMismatchException`, `PropertyReferenceException` (UUID o `sort` inválidos) | 400 | Parámetro inválido |
  | `ClienteNoEncontradoException` | 404 | Cliente no encontrado |
  | `IdentificacionDuplicadaException`; `DataIntegrityViolationException` **solo** si la restricción violada es `uk_persona_identificacion` (carrera entre dos altas simultáneas) | 409 | Identificación duplicada |
  | `ReglaNegocioException` (p. ej. `ClienteEliminadoException`) | 422 | Regla de negocio incumplida |
  | `Exception` y cualquier otra `DataIntegrityViolationException` | 500 | Error interno (sin detalle; se registra en log) |

  Se habilita `spring.mvc.problemdetails.enabled=true`.
- **Rationale**: Principio VII; las excepciones de dominio no conocen HTTP, el mapeo vive en un
  único lugar.
- **Alternatives considered**: Envoltorio de error propio (reinventa RFC 7807).

## R10. Estrategia de pruebas concreta

- **Decision**:
  - **Unitarias** (sin Spring): `ClienteTest` (F5, TDD), `ClienteServiceImplTest` (Mockito:
    repositorio, `PasswordEncoder`, `ApplicationEventPublisher`), `ClienteEventPublisherTest`.
  - **Slice**: `ClienteControllerTest` con `@WebMvcTest` + `@MockitoBean` del service.
  - **Integración** (F6): `ClienteIntegrationTest` con `@SpringBootTest` + Testcontainers
    (`postgres:16-alpine`, `rabbitmq:3.13-management`) vía `@ServiceConnection`; verifica
    persistencia real, migraciones Flyway y que el mensaje llega a una cola de prueba enlazada a
    `clientes.exchange`.
  - **API**: Karate (`io.karatelabs:karate-junit5` 1.5.x) con `ClientesKarateTest` que levanta la
    app en `RANDOM_PORT` con los mismos contenedores; feature `clientes.feature` con los casos
    del enunciado.
  - Configuración de contenedores compartida en una clase `TestcontainersConfig`
    (`@TestConfiguration`) reutilizada por integración y Karate.
- **Rationale**: Cumple la matriz del Principio VI; compartir contenedores reduce tiempo de
  ejecución.
- **Alternatives considered**: H2 (no es PostgreSQL real, prohibido por la decisión Q7e);
  REST Assured (Karate ya cubre pruebas de API y es entregable del reto).

## R11. Mapeo DTO ↔ entidad

- **Decision**: MapStruct solo para **entidad → response** (`ClienteMapper.toResponse`). La
  creación y actualización de entidades se hace por constructor/métodos de dominio en el service,
  no por MapStruct.
- **Rationale**: Las entidades ricas (Principio II) no tienen setters; MapStruct necesitaría
  setters o romper invariantes. Para la salida es ideal.
- **Alternatives considered**: MapStruct bidireccional con `@ObjectFactory` (complejo y oculta
  reglas de negocio).

## R12. Contenedores

- **Decision**: Dockerfile multi-stage en `ms-clientes/` (`maven:3.9-eclipse-temurin-21` para
  build con `-DskipTests`, `eclipse-temurin:21-jre-alpine` para runtime, usuario no root).
  `docker-compose.yml` en la raíz con `postgres:16-alpine` (script de init que crea las bases
  `clientes_db` y `cuentas_db` con usuarios propios), `rabbitmq:3.13-management` y `ms-clientes`
  (puerto 8081). Healthchecks con Actuator (`/actuator/health`) y `depends_on: condition:
  service_healthy`.
- **Rationale**: Principio de contenedores de la constitución; ms-cuentas se agregará al mismo
  compose en su feature.
- **Alternatives considered**: Jib/Buildpacks (menos transparente para la defensa).

## R13. Concurrencia sobre clientes

- **Decision**: Sin control de concurrencia (última escritura gana); sin `@Version`.
- **Rationale**: Decisión de clarificación 2026-10-28: datos personales con baja contención; el
  control estricto se reserva para el saldo (Principio V, ms-cuentas).
- **Alternatives considered**: Bloqueo optimista interno (`@Version` → 409) o expuesto en la API
  (`If-Match`/ETag): descartados por el usuario; se pueden mencionar como mejora futura.

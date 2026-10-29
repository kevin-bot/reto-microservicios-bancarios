# Constitución — Reto Técnico Microservicios Bancarios (ms-clientes / ms-cuentas)

## Core Principles

### I. Arquitectura en Capas Limpia (SOLID + Clean Code)

- Cada microservicio MUST seguir la arquitectura en capas: `controller` → `service` → `repository`.
- Los paquetes MUST organizarse **por feature con capas dentro**
  (p. ej. `cliente/{controller,service,repository,entity,dto,mapper}`), más un paquete
  `shared/{exception,config}` para lo transversal.
- Cada service MUST exponerse como interfaz con su implementación
  (`ClienteService` / `ClienteServiceImpl`); los controllers dependen solo de la interfaz.
- Los controllers MUST NOT contener lógica de negocio ni acceder a repositorios; solo validan
  entrada, delegan al service y mapean la respuesta.
- Las entidades JPA MUST NOT exponerse en la API; toda entrada/salida HTTP usa DTOs (`record`).
- Se aplican SOLID y Clean Code: nombres que revelan intención, métodos cortos de una sola
  responsabilidad, sin código muerto ni comentarios que repitan el código.

**Rationale**: El reto evalúa explícitamente buenas prácticas; una estructura en capas
consistente y predecible es fácil de navegar y de defender en la entrevista técnica.

### II. Dominio Rico

- Las reglas de negocio propias de una entidad MUST vivir en la entidad
  (p. ej. `cuenta.aplicarMovimiento(valor)`, `cliente.desactivar()`).
- Los services MUST limitarse a orquestar: transacción, carga/persistencia vía repositorio,
  publicación de eventos y mapeo.
- Las entidades MUST proteger sus invariantes: sin `@Setter` general; los cambios de estado se
  hacen mediante métodos con intención (`actualizarDireccion(...)`, `desactivar()`).
- Las violaciones de reglas de negocio MUST lanzarse como excepciones de dominio tipadas
  (p. ej. `SaldoNoDisponibleException`) sin dependencias de HTTP.

**Rationale**: Mantiene las reglas cohesionadas y probables sin Spring, y hace que la prueba
unitaria de la entidad `Cliente` (F5) verifique comportamiento real.

### III. Microservicios Autónomos con Integración Asíncrona

- Existen dos microservicios: **ms-clientes** (Persona, Cliente) y **ms-cuentas**
  (Cuenta, Movimiento, Reportes).
- Cada servicio MUST tener su propia base de datos (*database-per-service*); ningún servicio
  MUST leer o escribir la base del otro.
- La comunicación entre servicios MUST ser asíncrona vía **RabbitMQ**; no se permiten llamadas
  REST síncronas entre servicios.
- ms-clientes MUST publicar `ClienteCreado`, `ClienteActualizado` y `ClienteEliminado`.
- ms-cuentas MUST mantener una réplica local de solo lectura (`cliente_ref`: id, nombre,
  estado) alimentada por esos eventos, y usarla para validar clientes y construir reportes.
- Los contratos de eventos MUST documentarse en la spec correspondiente e incluir `eventId`
  (UUID), tipo de evento, fecha de ocurrencia y payload.

**Rationale**: Desacopla los servicios (ms-cuentas sigue operando si ms-clientes cae) y cumple
el requisito de comunicación asíncrona, aceptando consistencia eventual.

### IV. Mensajería Resiliente

- Los eventos MUST publicarse **después del commit** de la transacción
  (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
- El patrón **Transactional Outbox** MUST quedar documentado como mejora futura, explicando el
  riesgo residual (caída entre commit y publicación).
- Todo consumidor MUST ser **idempotente**: procesar dos veces el mismo `eventId` no altera
  el resultado.
- Todo consumidor MUST tener **reintentos con backoff** (3 intentos) y una
  **Dead Letter Queue** para los mensajes que fallan definitivamente.

**Rationale**: Cubre los factores de resiliencia que el reto pide contemplar, con soporte
nativo de RabbitMQ y sin sobreingeniería.

### V. Integridad del Dinero

- Montos MUST representarse con `BigDecimal` (Java) y `NUMERIC` (PostgreSQL); nunca
  `double`/`float`.
- El registro de un movimiento MUST ejecutarse en una transacción que bloquee la cuenta con
  **bloqueo pesimista** (`@Lock(PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE`).
- Todo movimiento MUST actualizar el saldo disponible de la cuenta y quedar registrado con el
  saldo resultante (historial auditable).
- Un movimiento que deje el saldo en negativo MUST rechazarse con el mensaje exacto
  **"Saldo no disponible"**.

**Rationale**: Evita condiciones de carrera entre retiros concurrentes y errores de redondeo,
requisitos básicos de un dominio financiero (F2, F3).

### VI. Pruebas por Niveles (TDD en reglas críticas)

- **TDD obligatorio** (rojo → verde → refactor) para las reglas de negocio críticas: cálculo y
  validación de saldo (F2, F3) y comportamiento de la entidad `Cliente` (F5).
- El resto del código MAY escribirse test-after, pero ninguna tarea se cierra sin sus pruebas.
- Matriz mínima por microservicio:
  - **Unitarias** (JUnit 5 + Mockito + AssertJ): toda entidad con comportamiento y todo service.
  - **Slice** `@WebMvcTest`: cada controller (contrato HTTP, validaciones y errores).
  - **Integración**: al menos un `@SpringBootTest` con **Testcontainers** (PostgreSQL y RabbitMQ).
  - **API**: escenarios **Karate** que se ejecutan dentro de `mvn test`.
- Se MUST entregar una colección **Postman** con los casos de uso 1–5 del enunciado.
- Convenciones: nombre `metodo_cuandoCondicion_debeResultado`, estructura Given/When/Then
  comentada, `@DisplayName` en español, aserciones con AssertJ.
- No se define umbral de cobertura obligatorio.

**Rationale**: Cada nivel de la pirámide demuestra algo distinto; supera los mínimos del reto
(F5, F6, Karate) y aplica TDD donde está el riesgo de negocio.

### VII. Errores Estandarizados (RFC 7807)

- Toda respuesta de error MUST usar `ProblemDetail` (RFC 7807), generado por un único
  `@RestControllerAdvice` global por servicio.
- Mapeo de códigos:
  - Validación de entrada → **400**
  - Recurso no encontrado → **404**
  - Conflicto (p. ej. duplicado de identificación o número de cuenta) → **409**
  - Regla de negocio (p. ej. "Saldo no disponible") → **422**
  - Error inesperado → **500**, sin exponer stack traces ni detalles internos.
- Los errores inesperados MUST registrarse en el log con su causa.

**Rationale**: Un formato de error estándar y centralizado es predecible para los clientes de
la API y cumple el requisito de manejo de excepciones (F3).

## Restricciones Técnicas

- **Stack**: Java 21 (LTS), Spring Boot 3.x, Maven. Un proyecto Maven independiente por
  microservicio (sin POM padre).
- **Persistencia**: Spring Data JPA (patrón Repository) sobre PostgreSQL.
  - Migraciones con **Flyway**; `spring.jpa.hibernate.ddl-auto=validate`.
  - Entregable `BaseDatos.sql` consolidado a partir de las migraciones.
  - `Cliente` hereda de `Persona` mediante herencia JPA.
- **Idioma**: dominio en español alineado al enunciado (`Cliente`, `Cuenta`, `Movimiento`,
  `/clientes`, `/cuentas`, `/movimientos`, `/reportes`); sufijos técnicos en inglés
  (`ClienteRepository`, `ClienteServiceImpl`, `ClienteController`).
- **API REST**: verbos GET, POST, PUT, PATCH, DELETE según aplique; documentación con
  **springdoc-openapi** (Swagger UI) en cada servicio.
- **Seguridad**: la contraseña del cliente MUST almacenarse con hash **BCrypt**
  (`spring-security-crypto`) y MUST NOT devolverse en ninguna respuesta.
- **Utilidades**:
  - DTOs como `record` con **Bean Validation**.
  - Mappers con **MapStruct**.
  - **Lombok** restringido en entidades JPA: solo `@Getter`,
    `@NoArgsConstructor(access = AccessLevel.PROTECTED)` y `@Builder`. Prohibidos `@Data`,
    `@EqualsAndHashCode` y `@Setter`.
- **Contenedores**:
  - Un Dockerfile multi-stage por servicio, compilando con `-DskipTests`.
  - Un `docker-compose.yml` en la raíz con: un PostgreSQL con dos bases y dos usuarios,
    RabbitMQ, ms-clientes y ms-cuentas.
  - La solución MUST levantar y funcionar completa con `docker compose up`.
- **Rendimiento y escalabilidad** (contemplados y documentados en el README):
  - Servicios sin estado, escalables horizontalmente.
  - Índices en las columnas de búsqueda (p. ej. fecha y cuenta de movimientos).
  - Consultas de reporte acotadas por rango de fechas.

## Flujo de Trabajo y Calidad

- **Repositorio**: monorepo público en GitHub con `ms-clientes/`, `ms-cuentas/`,
  `docker-compose.yml`, `BaseDatos.sql`, la colección Postman y el `README.md`.
- **Spec Kit**:
  - Toda funcionalidad sigue `/speckit-specify` → `/speckit-plan` → `/speckit-tasks`
    → `/speckit-implement`.
  - Cada feature se desarrolla en su propia rama (`NNN-nombre-feature`).
- **Commits**: Conventional Commits (`feat:`, `fix:`, `test:`, `refactor:`, `docs:`,
  `chore:`).
- **Pruebas**:
  - Las pruebas se ejecutan en local con `mvn test` y Docker encendido (requerido por
    Testcontainers); no forman parte del `docker build`.
  - El README MUST documentarlo.
- **README**: MUST incluir requisitos, pasos de despliegue con Docker, cómo ejecutar las
  pruebas, endpoints principales y decisiones de arquitectura (incluidas las mejoras futuras:
  outbox, etc.).
- **Definición de terminado** de una tarea:
  - Compila.
  - Sus pruebas pasan.
  - Cumple esta constitución.
  - Sin código comentado ni TODOs huérfanos.

## Governance

- Esta constitución prevalece sobre cualquier otra práctica del proyecto. Todo
  `/speckit-plan` MUST incluir un "Constitution Check" que verifique el cumplimiento de cada
  principio; cualquier desviación MUST justificarse por escrito en el plan.
- Las enmiendas se hacen con `/speckit-constitution`, documentando el cambio en el
  Sync Impact Report y en un commit `docs: amend constitution to vX.Y.Z`.
- Versionado semántico:
  - **MAJOR**: eliminación o redefinición incompatible de un principio.
  - **MINOR**: nuevo principio o sección, o ampliación material.
  - **PATCH**: aclaraciones y redacción.

**Version**: 1.0.0 | **Ratified**: 2026-10-28 | **Last Amended**: 2026-10-28

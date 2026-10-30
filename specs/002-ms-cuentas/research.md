# Research: Microservicio de Cuentas y Movimientos (ms-cuentas)

**Feature**: `002-ms-cuentas` | **Date**: 2026-10-29

El stack lo fija la constitución v1.0.0 y se reutilizan las decisiones probadas en
`001-ms-clientes` (Spring Boot 3.5.16, Flyway, ProblemDetail, Testcontainers, Karate). Aquí se
resuelven las decisiones propias de ms-cuentas.

## R1. Código compartido entre microservicios

- **Decision**: Duplicar en ms-cuentas las piezas transversales de ms-clientes
  (`GlobalExceptionHandler`, `PaginaResponse`, `AlMenosUnCampo`, `ReglaNegocioException`,
  `DomainValidationException`) adaptadas a sus excepciones. Sin librería común.
- **Rationale**: La constitución exige proyectos Maven independientes sin POM padre. Una librería
  compartida acoplaría versiones y despliegues de ambos servicios por muy poco código.
- **Alternatives considered**: Módulo `shared-kernel` publicado en un repositorio Maven (acopla el
  ciclo de vida de los servicios; excesivo para el reto).

## R2. Montos y redondeo

- **Decision**: `BigDecimal` en Java y `NUMERIC(15,2)` en PostgreSQL. Las entradas se validan con
  `@Digits(integer = 13, fraction = 2)`; nunca se redondea en silencio. Comparaciones con
  `compareTo` (no `equals`, que considera la escala).
- **Rationale**: Principio V de la constitución.
- **Alternatives considered**: `long` en centavos (exige convertir en cada borde de la API).

## R3. Modelo de agregado Cuenta–Movimiento

- **Decision**: `Cuenta` es la raíz que protege el saldo. No mantiene una colección de
  movimientos (evita cargar todo el historial); `Movimiento` referencia a su `Cuenta`
  (`@ManyToOne(fetch = LAZY)`). Los movimientos se crean solo a través de
  `cuenta.registrarMovimiento(valor, fecha)`, que valida estado y saldo, actualiza el saldo
  disponible y devuelve el `Movimiento` con su saldo resultante.
- **Rationale**: Las reglas de saldo (F2, F3) viven en la entidad (Principio II) y son
  verificables con pruebas unitarias puras (TDD).
- **Alternatives considered**: `@OneToMany` de movimientos en Cuenta (carga y bloqueo de todo el
  historial); lógica de saldo en el service (dominio anémico).

## R4. Corrección de movimientos con recálculo en cadena

- **Decision**: `cuenta.corregirMovimiento(movimiento, nuevoValor, posteriores)`:
  1. saldo base = `movimiento.saldo − movimiento.valor` (saldo previo al movimiento);
  2. se calcula en memoria la nueva cadena de saldos para el movimiento corregido y cada
     posterior (orden `fecha, id`);
  3. si algún saldo queda < 0 → `SaldoNoDisponibleException` **antes** de modificar nada;
  4. si todo es válido, se aplica: nuevo valor y tipo del corregido, nuevos saldos de todos y
     nuevo saldo disponible de la cuenta.
  El orden en el service es obligatorio para no recalcular sobre datos viejos de la caché de
  Hibernate:
  1. obtener solo el número de cuenta del movimiento (proyección, sin cargar la entidad);
  2. bloquear la cuenta con `buscarParaActualizar`;
  3. recién entonces cargar el movimiento y sus posteriores.
- **Rationale**: Decisión de clarificación (corregir cualquier movimiento) manteniendo la
  invariante "saldo nunca negativo" y la atomicidad (todo o nada).
- **Alternatives considered**: Recalcular con una sentencia SQL de ventana (`SUM() OVER`): eficiente
  pero saca la regla del dominio y es más difícil de probar unitariamente.

## R5. Concurrencia sobre el saldo

- **Decision**: `CuentaRepository.buscarParaActualizar(numeroCuenta)` con
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) en registrar y corregir
  movimientos. Defensa adicional en BD: `CHECK (saldo_disponible >= 0)`.
- **Rationale**: Principio V. La contención por cuenta es baja y el bloqueo pesimista es simple de
  razonar; el `CHECK` garantiza la invariante aunque hubiera un error de código.
- **Alternatives considered**: Bloqueo optimista con `@Version` y reintento (más código, peor bajo
  contención).

## R6. Consumo de eventos de cliente

- **Decision**:
  - **Topología** (declarada por ms-cuentas, el consumidor):
    - Cola durable `cuentas.clientes.queue` enlazada a `clientes.exchange` con `cliente.*`.
    - La cola tiene `x-dead-letter-exchange = cuentas.clientes.dlx`.
    - DLX directo `cuentas.clientes.dlx` → cola durable `cuentas.clientes.dlq`.
  - **Reintentos**: `spring.rabbitmq.listener.simple.retry` con 4 intentos en total (1 + 3
    reintentos), espera inicial de 1 s y multiplicador 2. Con `default-requeue-rejected=false`, al
    agotarse se rechaza el mensaje y RabbitMQ lo envía a la DLQ.
  - **Errores no reintentables**: por defecto Spring reintenta cualquier excepción, incluidos
    los errores de conversión. Un `RabbitRetryTemplateCustomizer` (destino `LISTENER`) instala una
    política que **no** reintenta `MessageConversionException`. Así un JSON inválido va a la DLQ
    al primer intento, como dice el contrato.
  - **Deserialización**: `Jackson2JsonMessageConverter` con
    `setAlwaysConvertToInferredType(true)`. Se ignora la cabecera `__TypeId__` del productor (su
    clase no existe en ms-cuentas) y se convierte al record local `ClienteEventoRecibido`. Un
    JSON inválido es un error fatal y va directo a la DLQ.
    El converter usa una copia del `ObjectMapper` con `FAIL_ON_UNKNOWN_PROPERTIES = false`. Para
    la API REST se exigen JSON estrictos, pero en los eventos se toleran campos nuevos del
    productor, que son cambios compatibles.
- **Rationale**: Principio IV (reintentos con backoff, DLQ) con configuración declarativa de
  Spring AMQP y poco código.
- **Alternatives considered**:
  - Reintentos manuales con colas de espera por TTL (más topología);
  - `@RetryableTopic` (es de Kafka).

## R7. Idempotencia y orden de eventos

- **Decision**: `ClienteEventoProcessor` en una transacción:
  1. Si `eventId` ya existe en `evento_procesado`, se descarta.
  2. Se busca `cliente_ref`. Si `fechaOcurrencia` es menor o igual a `ultima_actualizacion`, se
     descarta por fuera de orden.
  3. Se aplica el cambio (crear o actualizar la réplica; en `CLIENTE_ELIMINADO`, inactivar al
     cliente y desactivar sus cuentas).
  4. Se inserta `evento_procesado(event_id)`. La PK impide que una carrera entre dos consumidores
     procese el mismo evento dos veces.
- **Rationale**: Cumple FR-002, FR-003 y FR-005. La tabla de eventos procesados es el patrón
  clásico del *idempotent consumer*.
- **Alternatives considered**: Idempotencia solo por `fechaOcurrencia` (no distingue un duplicado
  exacto de un evento distinto con la misma marca de tiempo).

## R8. Arranque y consistencia eventual

- **Riesgo**: si ms-clientes publica antes de que ms-cuentas haya declarado su cola, esos eventos
  se pierden. RabbitMQ descarta los mensajes de un exchange sin colas enlazadas.
- **Decision**:
  - En docker compose, ms-clientes depende de que ms-cuentas esté healthy. Así la cola existe
    antes del primer alta.
  - Como la cola es durable, si ms-cuentas se cae después, los eventos se acumulan y se procesan
    al volver.
  - Queda documentado como mejora futura un endpoint de resincronización, o un replay desde el
    outbox.
- **Alternatives considered**: Que ms-clientes declare la cola de ms-cuentas (acopla el productor
  a sus consumidores).

## R9. Identificadores públicos

- **Decision**:
  - **Cuenta**: identificador público = `numeroCuenta` (lo asigna el operador, es único y es el
    dato del enunciado). La API usa `/cuentas/{numeroCuenta}`.
  - **Movimiento**: `movimientoId` UUID generado por la aplicación; la API usa
    `/movimientos/{movimientoId}`.
  - Las PK numéricas no se exponen.
- **Rationale**: Mismo criterio que `clienteId` en ms-clientes; el número de cuenta ya es la clave
  de negocio natural.
- **Alternatives considered**: UUID también para cuentas (el operador ya conoce el número).

## R10. Parámetros y zona horaria del reporte

- **Decision**:
  - El parámetro `fecha` se convierte a un value object `RangoFechas(inicio, fin)` mediante un
    `Converter<String, RangoFechas>` registrado en Spring MVC.
  - Validaciones:
    - exactamente dos fechas ISO separadas por coma;
    - `inicio <= fin`;
    - rango de 366 días como máximo.
  - Si falla, la conversión lanza `MethodArgumentTypeMismatchException` → 400 "Parámetro
    inválido".
  - Los días completos se calculan en la zona horaria del banco, configurable con
    `banco.zona-horaria`, por defecto `America/Guayaquil`: `[inicio 00:00, fin+1 00:00)`.
- **Rationale**: FR-022 y FR-023. El value object encapsula el parseo y se prueba de forma
  unitaria.
- **Alternatives considered**: Parsear a mano en el controller (mezcla responsabilidades);
  usar UTC (los extremos del día no coincidirían con la percepción del operador).

## R11. Construcción del reporte

- **Decision**: `ReporteServiceImpl` hace tres consultas, sin N+1:
  1. `cliente_ref` (404 si no existe);
  2. las cuentas del cliente;
  3. una sola consulta de los movimientos de esas cuentas en el rango, ordenados por
     `fecha, id`.

  Agrupa en memoria por cuenta y devuelve records anidados (`ReporteResponse` →
  `CuentaReporte` → `MovimientoReporte`). Índice `(cuenta_id, fecha)`.
- **Rationale**: SC-006 (1.000 movimientos en menos de 2 s) con consultas indexadas y acotadas.
- **Alternatives considered**: Una consulta por cuenta (N+1); una vista SQL (lógica fuera del
  código probado).

## R14. Cliente inactivo y listado de movimientos (clarificación 2026-10-29)

- **Decision**:
  - **Cliente inactivo**: `Cuenta.registrarMovimiento` y `corregirMovimiento` verifican
    `cliente.estaActivo()` y lanzan `ClienteNoDisponibleException` (422). El estado de la cuenta no
    se toca, así que la reactivación del cliente (evento `CLIENTE_ACTUALIZADO`) habilita de nuevo
    sus cuentas sin más lógica.
  - **Listado de movimientos**: `GET /movimientos` acepta los filtros combinables `cliente`,
    `numeroCuenta` y `fecha` (el mismo `RangoFechas` del reporte), construidos con
    `MovimientoSpecifications`. La consulta hace `fetch join` de cuenta y cliente, y devuelve filas
    planas (`MovimientoResponse`) con los datos de cliente y cuenta, como el ejemplo del caso de
    uso 5.
  - **Fecha**: la asigna siempre el sistema (`Clock` inyectado, que permite fijar la hora en las
    pruebas); si la petición la incluye, se rechaza con 400 por la deserialización estricta.
- **Rationale**: Una sola verificación en el dominio, sin estados derivados que sincronizar. El
  listado plano cubre de forma literal el caso de uso 5 sin cambiar el reporte anidado.
- **Alternatives considered**: Desactivar las cuentas al desactivar al cliente (exige reactivarlas
  a mano o guardar quién las desactivó).

## R12. Estrategia de pruebas

- **Decision**:
  - **Unitarias**:
    - TDD en `CuentaTest` (F2, F3, corrección con recálculo);
    - `ClienteRefTest` (orden de eventos), `RangoFechasTest`;
    - services con Mockito y `ClienteEventoProcessorTest` (idempotencia).
  - **Slice**: `@WebMvcTest` de `CuentaController`, `MovimientoController` y `ReporteController`.
  - **Integración**:
    - `MovimientoConcurrenciaIntegrationTest`: 20 retiros simultáneos; el saldo nunca queda
      negativo (SC-002).
    - `ClienteEventoListenerIntegrationTest`: publica en `clientes.exchange` → réplica; duplicado
      sin efecto; mensaje inválido termina en la DLQ.
  - **Karate**: `CuentasKarateTest` siembra directamente en la réplica (`ClientesDePrueba`, vía
    repositorio e idempotente) los 3 clientes del caso de uso 1 y un cuarto cliente
    "Cliente Pruebas". Así las historias de cuentas, movimientos y reporte no dependen del
    consumidor de eventos. La mensajería se prueba aparte en
    `ClienteEventoListenerIntegrationTest`. Organización de los features:
    - `casos-de-uso.feature`: un único escenario ordenado con los casos 2 a 5, que cada historia
      va extendiendo, porque los escenarios de Karate no comparten estado;
    - features de errores: usan "Cliente Pruebas" y números de cuenta aleatorios, para no alterar
      los saldos ni las cuentas que espera el caso de uso 5;
    - "hoy" se calcula en `America/Guayaquil`, la misma zona que `banco.zona-horaria`.
  - **Postman**: colección E2E que crea los clientes en ms-clientes y, tras una breve espera,
    ejecuta los casos 2 a 5 en ms-cuentas. Demuestra la comunicación asíncrona real.
- **Rationale**: Principio VI; cubre las garantías propias de este servicio (concurrencia, mensajería).

## R13. Contenedores y entregables globales

- **Decision**:
  - **Contenedor**: ms-cuentas en el puerto 8082, con un Dockerfile igual al de ms-clientes,
    agregado al `docker-compose.yml` raíz.
  - **`BaseDatos.sql`**: se crea en la raíz, consolidando las migraciones de ambos servicios.
  - **`README.md` raíz**: despliegue completo y arquitectura.
- **Rationale**: Ambos servicios existen ya; son entregables explícitos del enunciado.

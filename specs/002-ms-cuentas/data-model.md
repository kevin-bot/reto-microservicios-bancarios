# Data Model: ms-cuentas

**Feature**: `002-ms-cuentas` | **Date**: 2026-10-29 | **Base de datos**: `cuentas_db` (PostgreSQL 16)

## Diagrama

```text
┌───────────────────────────┐       ┌────────────────────────────────┐       ┌──────────────────────────────┐
│ cliente_ref               │ 1   * │ cuenta                         │ 1   * │ movimiento                   │
├───────────────────────────┤◄──────┤────────────────────────────────┤◄──────┤──────────────────────────────┤
│ cliente_id UUID PK        │       │ id BIGINT PK                   │       │ id BIGINT PK                 │
│ nombre VARCHAR(100)       │       │ numero_cuenta VARCHAR(20) UQ   │       │ movimiento_id UUID UQ        │
│ estado BOOLEAN            │       │ tipo VARCHAR(10)               │       │ cuenta_id BIGINT FK          │
│ ultima_actualizacion TZ   │       │ saldo_inicial NUMERIC(15,2)    │       │ fecha TIMESTAMPTZ            │
└───────────────────────────┘       │ saldo_disponible NUMERIC(15,2) │       │ tipo VARCHAR(10)             │
                                    │ estado BOOLEAN                 │       │ valor NUMERIC(15,2)          │
┌───────────────────────────┐       │ cliente_id UUID FK             │       │ saldo NUMERIC(15,2)          │
│ evento_procesado          │       │ fecha_creacion TIMESTAMPTZ     │       └──────────────────────────────┘
├───────────────────────────┤       │ fecha_actualizacion TIMESTAMPTZ│
│ event_id UUID PK          │       └────────────────────────────────┘
│ procesado_en TIMESTAMPTZ  │
└───────────────────────────┘
```

## Entidad: ClienteRef (réplica de solo lectura)

| Campo | Tipo Java | Columna | Reglas |
|---|---|---|---|
| clienteId | `UUID` | `cliente_id UUID PRIMARY KEY` | Viene del evento; nunca se genera aquí |
| nombre | `String` | `nombre VARCHAR(100) NOT NULL` | |
| estado | `boolean` | `estado BOOLEAN NOT NULL` | `false` si el cliente está inactivo o eliminado |
| ultimaActualizacion | `Instant` | `ultima_actualizacion TIMESTAMPTZ NOT NULL` | `fechaOcurrencia` del último evento aplicado |

**Comportamiento**:
- `ClienteRef.desde(evento)`: crea la réplica a partir del primer evento recibido.
- `aplicar(nombre, estado, fechaOcurrencia)`: devuelve `false` y no cambia nada si
  `fechaOcurrencia <= ultimaActualizacion` (evento fuera de orden, FR-003).
- `estaActivo()`.

## Entidad: Cuenta

| Campo | Tipo Java | Columna | Reglas |
|---|---|---|---|
| id | `Long` | `id BIGINT GENERATED ALWAYS AS IDENTITY PK` | Técnico, no se expone |
| numeroCuenta | `String` | `numero_cuenta VARCHAR(20) NOT NULL CONSTRAINT uk_cuenta_numero UNIQUE` | Solo dígitos, 6–20 (FR-007); inmutable |
| tipo | `TipoCuenta` | `tipo VARCHAR(10) NOT NULL` | `AHORROS`, `CORRIENTE` |
| saldoInicial | `BigDecimal` | `saldo_inicial NUMERIC(15,2) NOT NULL CHECK (saldo_inicial >= 0)` | ≥ 0, 2 decimales; inmutable (FR-009, FR-011) |
| saldoDisponible | `BigDecimal` | `saldo_disponible NUMERIC(15,2) NOT NULL CONSTRAINT ck_cuenta_saldo CHECK (saldo_disponible >= 0)` | Solo lo cambian los movimientos |
| estado | `boolean` | `estado BOOLEAN NOT NULL DEFAULT TRUE` | |
| cliente | `ClienteRef` | `cliente_id UUID NOT NULL REFERENCES cliente_ref(cliente_id)` | `@ManyToOne(fetch = LAZY)`; inmutable |
| fechaCreacion / fechaActualizacion | `Instant` | `TIMESTAMPTZ NOT NULL` | `@CreationTimestamp` / `@UpdateTimestamp` |

**Comportamiento** (objetivo del TDD de F2/F3):

| Método | Efecto | Errores |
|---|---|---|
| `Cuenta.abrir(numero, tipo, saldoInicial, estado, cliente)` | Saldo disponible = saldo inicial | `DomainValidationException` (número, saldo negativo, más de 2 decimales); `ClienteNoDisponibleException` si el cliente está inactivo |
| `registrarMovimiento(valor, fecha)` | Valida y actualiza el saldo; devuelve un `Movimiento` con su saldo resultante | `CuentaInactivaException`; `ClienteNoDisponibleException` si `cliente.estaActivo()` es falso (FR-017a); `DomainValidationException` (valor 0 o más de 2 decimales); `SaldoNoDisponibleException` ("Saldo no disponible") |
| `corregirMovimiento(movimiento, nuevoValor, posteriores)` | Recalcula en cadena (research R4), todo o nada | Mismos errores que `registrarMovimiento` (incluido cliente inactivo) |
| `cambiarTipo(tipo)`, `activar()`, `desactivar()` | — | `ClienteNoDisponibleException` al activar si el cliente está inactivo |

## Entidad: Movimiento

| Campo | Tipo Java | Columna | Reglas |
|---|---|---|---|
| id | `Long` | `id BIGINT GENERATED ALWAYS AS IDENTITY PK` | Desempata el orden de movimientos con la misma fecha |
| movimientoId | `UUID` | `movimiento_id UUID NOT NULL CONSTRAINT uk_movimiento_id UNIQUE` | Identificador público |
| cuenta | `Cuenta` | `cuenta_id BIGINT NOT NULL REFERENCES cuenta(id)` | `@ManyToOne(fetch = LAZY)`; inmutable |
| fecha | `Instant` | `fecha TIMESTAMPTZ NOT NULL` | La asigna el sistema; inmutable |
| tipo | `TipoMovimiento` | `tipo VARCHAR(10) NOT NULL` | `DEPOSITO` si valor > 0, `RETIRO` si valor < 0 |
| valor | `BigDecimal` | `valor NUMERIC(15,2) NOT NULL CHECK (valor <> 0)` | Con signo |
| saldo | `BigDecimal` | `saldo NUMERIC(15,2) NOT NULL CHECK (saldo >= 0)` | Saldo resultante |

Constructor de paquete (solo `Cuenta` crea movimientos). Métodos de paquete
`corregirValor(nuevoValor)` y `actualizarSaldo(saldo)` usados únicamente por
`Cuenta.corregirMovimiento`.

## Entidad: EventoProcesado

| Campo | Columna | Uso |
|---|---|---|
| eventId | `event_id UUID PRIMARY KEY` | Idempotencia (FR-002) |
| procesadoEn | `procesado_en TIMESTAMPTZ NOT NULL` | Auditoría |

## Índices

| Tabla | Índice | Motivo |
|---|---|---|
| cuenta | `uk_cuenta_numero` | Búsqueda por número y unicidad |
| cuenta | `idx_cuenta_cliente (cliente_id)` | Cuentas de un cliente (listado y reporte) |
| movimiento | `idx_movimiento_cuenta_fecha (cuenta_id, fecha, id)` | Reporte por rango, listado y recálculo ordenado |

## Migraciones Flyway

- `V1__crear_tablas_cuentas.sql`: crea `cliente_ref`, `cuenta`, `movimiento` y
  `evento_procesado`, con sus restricciones e índices.

## Repositorios

| Repositorio | Métodos clave |
|---|---|
| `ClienteRefRepository` | `findById(UUID)` |
| `EventoProcesadoRepository` | `existsById(UUID)` |
| `CuentaRepository` (+ `JpaSpecificationExecutor`) | `findByNumeroCuenta`; `buscarParaActualizar(numero)` con `@Lock(PESSIMISTIC_WRITE)`; `existsByNumeroCuenta`; `findByClienteClienteId(UUID)` |
| `MovimientoRepository` (+ `JpaSpecificationExecutor`) | `findByMovimientoId`; `findPosteriores(cuenta, fecha, id)` ordenados por `fecha, id`; `findDelReporte(cuentaIds, desde, hasta)`; listado con `MovimientoSpecifications` (`deCliente`, `deCuenta`, `enRango`) y `fetch join` de cuenta y cliente para evitar N+1 |

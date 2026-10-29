# Data Model: Microservicio de Clientes (ms-clientes)

**Feature**: `001-ms-clientes` | **Date**: 2026-10-28 | **Base de datos**: `clientes_db` (PostgreSQL 16)

## Diagrama

```text
┌──────────────────────────────┐        ┌───────────────────────────────┐
│ persona                      │ 1    1 │ cliente                       │
├──────────────────────────────┤◄───────┤───────────────────────────────┤
│ id             BIGINT PK      │        │ persona_id   BIGINT PK, FK    │
│ nombre         VARCHAR(100)   │        │ cliente_id   UUID UNIQUE      │
│ genero         VARCHAR(10)    │        │ contrasena   VARCHAR(100)     │
│ edad           INTEGER        │        │ estado       BOOLEAN          │
│ identificacion VARCHAR(20) UQ │        │ eliminado    BOOLEAN          │
│ direccion      VARCHAR(200)   │        │ fecha_creacion     TIMESTAMPTZ│
│ telefono       VARCHAR(15)    │        │ fecha_actualizacion TIMESTAMPTZ│
└──────────────────────────────┘        └───────────────────────────────┘
```

Herencia JPA `JOINED`: `Persona` (abstracta) → `Cliente` (ver research R2).

## Entidad: Persona (abstracta)

| Campo | Tipo Java | Columna | Reglas |
|---|---|---|---|
| id | `Long` | `id BIGINT GENERATED ALWAYS AS IDENTITY` PK | Técnico, nunca se expone |
| nombre | `String` | `nombre VARCHAR(100) NOT NULL` | Obligatorio, 1–100 caracteres, sin solo espacios |
| genero | `Genero` (enum) | `genero VARCHAR(10) NOT NULL` | `MASCULINO`, `FEMENINO`, `OTRO` (FR-005) |
| edad | `Integer` | `edad INTEGER NOT NULL` + `CONSTRAINT ck_persona_edad CHECK (edad BETWEEN 18 AND 120)` | 18–120, solo mayores de edad (FR-006) |
| identificacion | `String` | `identificacion VARCHAR(20) NOT NULL` + `CONSTRAINT uk_persona_identificacion UNIQUE` | Alfanumérica de 5–20 caracteres (FR-006); única entre todos los clientes incluidos eliminados (FR-004, FR-012b) |
| direccion | `String` | `direccion VARCHAR(200) NOT NULL` | Obligatoria, 1–200 caracteres |
| telefono | `String` | `telefono VARCHAR(15) NOT NULL` | Solo dígitos, 7–15 (FR-006) |

**Comportamiento** (métodos `protected`/`public` con intención, sin setters):
- `actualizarDatosPersonales(nombre, genero, edad, identificacion)`
- `actualizarContacto(direccion, telefono)`
- Cada método valida sus invariantes y lanza `DomainValidationException` si no se cumplen.

## Entidad: Cliente (extiende Persona)

| Campo | Tipo Java | Columna | Reglas |
|---|---|---|---|
| clienteId | `UUID` | `cliente_id UUID NOT NULL UNIQUE` | Generado al crear, inmutable, identificador público (FR-003) |
| contrasena | `String` | `contrasena VARCHAR(100) NOT NULL` | Siempre **hash BCrypt**; nunca expuesta (FR-007) |
| estado | `boolean` | `estado BOOLEAN NOT NULL DEFAULT TRUE` | Activo por defecto (FR-002) |
| eliminado | `boolean` | `eliminado BOOLEAN NOT NULL DEFAULT FALSE` | Eliminación lógica (FR-012) |
| fechaCreacion | `Instant` | `fecha_creacion TIMESTAMPTZ NOT NULL` | Auditoría (`@CreationTimestamp`) |
| fechaActualizacion | `Instant` | `fecha_actualizacion TIMESTAMPTZ NOT NULL` | Auditoría (`@UpdateTimestamp`) |

**Creación**: método de fábrica estático
`Cliente.crear(nombre, genero, edad, identificacion, direccion, telefono, contrasenaHash, estado)`
que genera el `clienteId` y valida todas las invariantes.

**Comportamiento** (objetivo de la prueba unitaria F5, desarrollado con TDD):

| Método | Efecto | Invariante / error |
|---|---|---|
| `activar()` | `estado = true` | Falla si `eliminado` |
| `desactivar()` | `estado = false` | Falla si `eliminado` |
| `cambiarContrasena(hash)` | Reemplaza el hash | Hash no vacío |
| `eliminar()` | `eliminado = true`, `estado = false` | Falla si ya `eliminado` |
| `estaEliminado()` | Consulta | — |

Las operaciones sobre un cliente eliminado lanzan `ClienteEliminadoException` (regla de negocio);
en la práctica el service no llega a cargarlo porque las consultas filtran eliminados (→ 404).

### Transiciones de estado

```text
            crear()
               │
               ▼
      ┌──── ACTIVO ◄────┐
      │        │        │ activar()
desactivar()   │        │
      ▼        │        │
   INACTIVO ───┼────────┘
      │        │
      │ eliminar()
      ▼        ▼
       ELIMINADO (terminal: estado=false, eliminado=true)
```

## Índices

| Tabla | Índice | Motivo |
|---|---|---|
| persona | `uk_persona_identificacion UNIQUE (identificacion)` | Unicidad (FR-004) y búsqueda de duplicados; el nombre de la restricción permite mapear su violación a 409 |
| cliente | `uk_cliente_cliente_id UNIQUE (cliente_id)` | Búsqueda por identificador público |
| cliente | `idx_cliente_eliminado_estado (eliminado, estado)` | Filtros del listado |

## Concurrencia

Sin control de concurrencia (sin `@Version`): ante ediciones simultáneas gana la última
escritura confirmada (decisión de clarificación 2026-10-28).

## Migraciones Flyway

- `V1__crear_tablas_persona_cliente.sql`: crea `persona`, `cliente`, restricciones e índices.
- Los datos del caso de uso 1 **no** se insertan por migración; se crean vía API (colección
  Postman / Karate), porque la contraseña debe pasar por el hash BCrypt de la aplicación.

## Repositorio

`ClienteRepository extends JpaRepository<Cliente, Long>`:

| Método | Uso |
|---|---|
| `Optional<Cliente> findByClienteIdAndEliminadoFalse(UUID clienteId)` | Consultar / actualizar / eliminar |
| `Page<Cliente> findAll(Specification<Cliente> spec, Pageable pageable)` (`JpaSpecificationExecutor`) | Listado paginado: siempre `eliminado = false` y filtros opcionales `estado` / `identificacion` (FR-009) |
| `boolean existsByIdentificacion(String identificacion)` | Duplicado al crear (incluye eliminados) |
| `boolean existsByIdentificacionAndClienteIdNot(String identificacion, UUID clienteId)` | Duplicado al actualizar |

## Evento de integración: ClienteEvento

Ver contrato completo en [contracts/eventos-cliente.md](./contracts/eventos-cliente.md).

| Campo | Tipo | Descripción |
|---|---|---|
| eventId | UUID | Único por evento (idempotencia del consumidor) |
| tipo | `CLIENTE_CREADO` \| `CLIENTE_ACTUALIZADO` \| `CLIENTE_ELIMINADO` | Tipo de evento |
| fechaOcurrencia | ISO-8601 instant | Momento del cambio |
| clienteId | UUID | Identificador público |
| nombre | String | Para la réplica `cliente_ref` de ms-cuentas |
| estado | boolean | Activo/inactivo (false si eliminado) |

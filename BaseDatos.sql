-- =====================================================================================
-- BaseDatos.sql — Esquema completo de la solución (reto técnico microservicios bancarios)
--
-- Una base de datos por microservicio (database-per-service):
--   * clientes_db → ms-clientes (Persona, Cliente)
--   * cuentas_db  → ms-cuentas  (réplica de clientes, Cuenta, Movimiento, eventos procesados)
--
-- En ejecución, el esquema lo crea Flyway en cada servicio (V1__*.sql) y este archivo es su
-- consolidado. Para crearlo a mano con psql como superusuario:
--   psql -U postgres -f BaseDatos.sql
-- Con Docker no hace falta: `docker compose up -d --build` crea bases, usuarios y tablas.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Bases de datos y usuarios (docker/postgres/init/01-crear-bases.sql)
-- -------------------------------------------------------------------------------------
CREATE USER clientes_user WITH PASSWORD 'clientes_pass';
CREATE DATABASE clientes_db OWNER clientes_user;

CREATE USER cuentas_user WITH PASSWORD 'cuentas_pass';
CREATE DATABASE cuentas_db OWNER cuentas_user;

-- -------------------------------------------------------------------------------------
-- 2. clientes_db — ms-clientes
--    (ms-clientes/src/main/resources/db/migration/V1__crear_tablas_persona_cliente.sql)
-- -------------------------------------------------------------------------------------
\connect clientes_db
SET ROLE clientes_user;

CREATE TABLE persona (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL,
    genero         VARCHAR(10)  NOT NULL,
    edad           INTEGER      NOT NULL CONSTRAINT ck_persona_edad CHECK (edad BETWEEN 18 AND 120),
    identificacion VARCHAR(20)  NOT NULL CONSTRAINT uk_persona_identificacion UNIQUE,
    direccion      VARCHAR(200) NOT NULL,
    telefono       VARCHAR(15)  NOT NULL
);

CREATE TABLE cliente (
    persona_id          BIGINT       PRIMARY KEY REFERENCES persona (id),
    cliente_id          UUID         NOT NULL CONSTRAINT uk_cliente_cliente_id UNIQUE,
    contrasena          VARCHAR(100) NOT NULL,
    estado              BOOLEAN      NOT NULL DEFAULT TRUE,
    eliminado           BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion      TIMESTAMPTZ  NOT NULL,
    fecha_actualizacion TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_cliente_eliminado_estado ON cliente (eliminado, estado);

RESET ROLE;

-- -------------------------------------------------------------------------------------
-- 3. cuentas_db — ms-cuentas
--    (ms-cuentas/src/main/resources/db/migration/V1__crear_tablas_cuentas.sql)
-- -------------------------------------------------------------------------------------
\connect cuentas_db
SET ROLE cuentas_user;

CREATE TABLE cliente_ref (
    cliente_id           UUID         PRIMARY KEY,
    nombre               VARCHAR(100) NOT NULL,
    estado               BOOLEAN      NOT NULL,
    ultima_actualizacion TIMESTAMPTZ  NOT NULL
);

CREATE TABLE evento_procesado (
    event_id     UUID        PRIMARY KEY,
    procesado_en TIMESTAMPTZ NOT NULL
);

CREATE TABLE cuenta (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_cuenta       VARCHAR(20)    NOT NULL CONSTRAINT uk_cuenta_numero UNIQUE,
    tipo                VARCHAR(10)    NOT NULL,
    saldo_inicial       NUMERIC(15, 2) NOT NULL CONSTRAINT ck_cuenta_saldo_inicial CHECK (saldo_inicial >= 0),
    saldo_disponible    NUMERIC(15, 2) NOT NULL CONSTRAINT ck_cuenta_saldo CHECK (saldo_disponible >= 0),
    estado              BOOLEAN        NOT NULL DEFAULT TRUE,
    cliente_id          UUID           NOT NULL REFERENCES cliente_ref (cliente_id),
    fecha_creacion      TIMESTAMPTZ    NOT NULL,
    fecha_actualizacion TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_cuenta_cliente ON cuenta (cliente_id);

CREATE TABLE movimiento (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movimiento_id UUID           NOT NULL CONSTRAINT uk_movimiento_id UNIQUE,
    cuenta_id     BIGINT         NOT NULL REFERENCES cuenta (id),
    fecha         TIMESTAMPTZ    NOT NULL,
    tipo          VARCHAR(10)    NOT NULL,
    valor         NUMERIC(15, 2) NOT NULL CONSTRAINT ck_movimiento_valor CHECK (valor <> 0),
    saldo         NUMERIC(15, 2) NOT NULL CONSTRAINT ck_movimiento_saldo CHECK (saldo >= 0)
);

CREATE INDEX idx_movimiento_cuenta_fecha ON movimiento (cuenta_id, fecha, id);

RESET ROLE;

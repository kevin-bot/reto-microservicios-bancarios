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

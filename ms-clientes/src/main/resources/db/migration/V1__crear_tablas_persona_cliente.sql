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

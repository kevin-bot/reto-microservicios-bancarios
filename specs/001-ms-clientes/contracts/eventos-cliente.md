# Contrato de Eventos: Cliente

**Productor**: ms-clientes | **Consumidores previstos**: ms-cuentas (réplica `cliente_ref`)
**Broker**: RabbitMQ | **Formato**: JSON (UTF-8), `content_type=application/json`

## Topología (declarada por el productor)

| Elemento | Valor |
|---|---|
| Exchange | `clientes.exchange` (tipo `topic`, durable) |
| Routing keys | `cliente.creado`, `cliente.actualizado`, `cliente.eliminado` |

Las colas, bindings, DLQ y política de reintentos son responsabilidad de cada consumidor
(constitución, Principio IV). Un consumidor que necesite todos los eventos se enlaza con
`cliente.*`.

## Cuándo se emite

| Operación | Routing key | `tipo` |
|---|---|---|
| POST /clientes exitoso | `cliente.creado` | `CLIENTE_CREADO` |
| PUT o PATCH /clientes/{id} exitoso | `cliente.actualizado` | `CLIENTE_ACTUALIZADO` |
| DELETE /clientes/{id} exitoso | `cliente.eliminado` | `CLIENTE_ELIMINADO` |

- Solo **después del commit** de la transacción (FR-015). Una operación fallida no emite nada.
- Exactamente un evento por operación confirmada.
- Garantía de entrega: *at-most-once* desde el productor (si el broker no está disponible tras
  el commit, el evento se pierde y se registra en log ERROR). Mejora futura documentada:
  Transactional Outbox → *at-least-once*.
- Los consumidores MUST ser idempotentes por `eventId` y, ante eventos fuera de orden, usar
  `fechaOcurrencia` para descartar actualizaciones más antiguas que la ya aplicada.

## Payload

```json
{
  "eventId": "b7e2c1d0-5f3a-4e8b-9c21-0a6d4f8e3b17",
  "tipo": "CLIENTE_ACTUALIZADO",
  "fechaOcurrencia": "2026-10-28T15:04:05.123Z",
  "clienteId": "3f1c2a9e-8b0d-4c55-9a6e-2b7f6c0d1e42",
  "nombre": "Marianela Montalvo",
  "estado": true
}
```

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `eventId` | string (UUID) | Sí | Único por evento; clave de idempotencia |
| `tipo` | string enum | Sí | `CLIENTE_CREADO` \| `CLIENTE_ACTUALIZADO` \| `CLIENTE_ELIMINADO` |
| `fechaOcurrencia` | string (ISO-8601 UTC) | Sí | Momento del cambio confirmado |
| `clienteId` | string (UUID) | Sí | Identificador público del cliente |
| `nombre` | string | Sí | Nombre actual del cliente |
| `estado` | boolean | Sí | `false` si el cliente está inactivo o eliminado |

Nunca se incluyen la contraseña ni datos personales no necesarios por los consumidores
(identificación, dirección, teléfono, edad, género), siguiendo el principio de minimización.

## Versionado

Cambios compatibles (agregar campos opcionales) no cambian el contrato. Un cambio incompatible
requiere nuevas routing keys con sufijo de versión (p. ej. `cliente.creado.v2`) y convivencia
temporal de ambas.

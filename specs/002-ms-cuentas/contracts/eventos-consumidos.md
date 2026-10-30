# Eventos consumidos: Cliente

**Productor**: ms-clientes. El contrato del mensaje está en
[`specs/001-ms-clientes/contracts/eventos-cliente.md`](../../001-ms-clientes/contracts/eventos-cliente.md).

**Consumidor**: ms-cuentas, que actualiza la réplica `cliente_ref`.

## Topología declarada por ms-cuentas

| Elemento | Valor | Propiedades |
|---|---|---|
| Cola principal | `cuentas.clientes.queue` | durable; `x-dead-letter-exchange = cuentas.clientes.dlx` |
| Binding | `clientes.exchange` → `cuentas.clientes.queue` | routing key `cliente.*` |
| Dead letter exchange | `cuentas.clientes.dlx` | `direct`, durable |
| Dead letter queue | `cuentas.clientes.dlq` | durable; binding con routing key `cuentas.clientes.queue` |

ms-cuentas también declara `clientes.exchange` (de forma idempotente y con las mismas
propiedades), así que puede arrancar antes que el productor.

## Procesamiento

```text
mensaje ──► ¿JSON válido? ──no──► DLQ (error fatal, sin reintentos)
                │ sí
                ▼
        ¿eventId procesado? ──sí──► ack (descartado: duplicado)
                │ no
                ▼
        ¿fechaOcurrencia <= ultima_actualizacion? ──sí──► registrar eventId, ack (fuera de orden)
                │ no
                ▼
        aplicar según tipo:
          CLIENTE_CREADO / CLIENTE_ACTUALIZADO → upsert cliente_ref (nombre, estado)
          CLIENTE_ELIMINADO                    → estado = false + desactivar sus cuentas
                │
                ▼
        registrar eventId (misma transacción) ──► ack
```

- **Error transitorio** (p. ej. la base de datos no está disponible): 4 intentos en total, con
  espera de 1 s, 2 s y 4 s; después se rechaza y el mensaje va a la DLQ.
- **Error de conversión** (JSON inválido): no se reintenta. La política de reintentos excluye
  `MessageConversionException` y el mensaje va a la DLQ al primer intento.
- **Entrega**: *at-least-once* desde la cola (ack después del commit) más idempotencia por
  `eventId`, lo que da un efecto *exactly-once* sobre la réplica.
- **Observabilidad**: cada descarte (duplicado o fuera de orden) se registra en log nivel INFO, y
  cada envío a la DLQ en nivel ERROR, con el `eventId`.

## Campos usados

| Campo | Uso |
|---|---|
| `eventId` | Idempotencia (`evento_procesado`) |
| `tipo` | Acción a aplicar |
| `fechaOcurrencia` | Orden (`cliente_ref.ultima_actualizacion`) |
| `clienteId` | PK de `cliente_ref` |
| `nombre` | Reporte y respuestas de cuentas |
| `estado` | Validar la apertura de cuentas |

Los campos desconocidos se ignoran en el consumidor, para tolerar que el productor agregue
campos nuevos (cambios compatibles).

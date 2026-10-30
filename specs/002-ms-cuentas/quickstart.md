# Quickstart: Validar ms-cuentas

**Feature**: `002-ms-cuentas` | Contratos: [cuentas-api.yaml](./contracts/cuentas-api.yaml),
[eventos-consumidos.md](./contracts/eventos-consumidos.md) | Modelo: [data-model.md](./data-model.md)

## Prerrequisitos

- Java 21 y Docker Desktop encendido.
- ms-clientes disponible (ya implementado en `001-ms-clientes`).

## 1. Pruebas automatizadas

```bash
cd ms-cuentas
./mvnw test
```

| Prueba | Nivel | Valida |
|---|---|---|
| `CuentaTest` | Unitaria (TDD) | Saldo, "Saldo no disponible", cuenta inactiva, corrección con recálculo |
| `ClienteRefTest`, `RangoFechasTest` | Unitaria | Orden de eventos; parseo y validación del rango |
| `*ServiceImplTest`, `ClienteEventoProcessorTest` | Unitaria (Mockito) | Orquestación, idempotencia, desactivación de cuentas |
| `*ControllerTest` | `@WebMvcTest` | Códigos HTTP, validaciones y ProblemDetail |
| `MovimientoConcurrenciaIntegrationTest` | Integración | 20 retiros simultáneos; el saldo nunca queda negativo (SC-002) |
| `ClienteEventoListenerIntegrationTest` | Integración | Réplica vía RabbitMQ, duplicados y DLQ |
| `CuentasKarateTest` | API (Karate) | Casos de uso 2 a 5 del enunciado |

## 2. Levantar toda la solución

```bash
docker compose up -d --build
docker compose ps     # postgres, rabbitmq, ms-cuentas y ms-clientes en estado healthy
```

- ms-cuentas: `http://localhost:8082/api` (Swagger: `/api/swagger-ui.html`)
- ms-clientes: `http://localhost:8081/api`

## 3. Recorrido end-to-end (también en la colección Postman)

| # | Acción | Resultado esperado |
|---|---|---|
| 1 | Crear Jose Lema, Marianela Montalvo y Juan Osorio en ms-clientes | `201` cada uno (guardar sus `clienteId`) |
| 2 | Esperar ~1 s y crear en ms-cuentas las cuentas 478758 (AHORROS, 2000, Jose), 225487 (CORRIENTE, 100, Marianela), 495878 (AHORROS, 0, Juan), 496825 (AHORROS, 540, Marianela) | `201`, `saldoDisponible` = saldo inicial |
| 3 | Crear la cuenta 585545 (CORRIENTE, 1000, Jose) | `201`; `GET /cuentas?clienteId={jose}` devuelve 2 cuentas |
| 4 | `POST /movimientos`: 478758 −575, 225487 +600, 495878 +150, 496825 −540 | `201`; saldos 1425, 700, 150, 0 |
| 4b | `POST /movimientos`: 496825 −1 | `422`, `detail` = "Saldo no disponible"; el saldo sigue en 0 |
| 5 | `GET /reportes?fecha={hoy},{hoy}&cliente={marianela}` | `200`, 2 cuentas: 225487 (saldo 700, movimiento +600) y 496825 (saldo 0, movimiento −540) |
| 5b | `GET /movimientos?cliente={marianela}&fecha={hoy},{hoy}` | `200`, 2 filas planas con `nombreCliente` "Marianela Montalvo", `numeroCuenta`, `tipoCuenta`, `saldoInicial`, `estadoCuenta`, `valor` y `saldo` (caso de uso 5) |
| 6 | Crear la cuenta con un número existente | `409` |
| 7 | Crear una cuenta con `clienteId` inexistente | `422` "Cliente no disponible" |
| 8 | `PATCH /movimientos/{id del −575}` con `{"valor": -500}` | `200`, saldo 1500; la cuenta 478758 queda en 1500 |
| 9 | `PATCH /cuentas/495878` con `{"estado": false}` y luego un movimiento sobre ella | `200`, luego `422` "Cuenta inactiva" |
| 10 | `GET /reportes?fecha=2022-02-10,2022-02-01&cliente=…` | `400` "Parámetro inválido" |
| 10b | `PATCH /api/clientes/{jose}` con `{"estado": false}` en ms-clientes; tras ~1 s, movimiento sobre 585545 | `422` "Cliente no disponible"; tras reactivar a Jose (`{"estado": true}`), el movimiento se acepta |
| 11 | `DELETE /api/clientes/{juan}` en ms-clientes y, tras ~1 s, `GET /cuentas/495878` | `estado: false` (cuentas desactivadas por evento) |

## 4. Verificar la mensajería

- En RabbitMQ management (`http://localhost:15672`) existen `cuentas.clientes.queue` (enlazada a
  `clientes.exchange` con `cliente.*`) y `cuentas.clientes.dlq`.
- Publicar a mano en `clientes.exchange` un mensaje con JSON inválido (routing key
  `cliente.creado`). **Esperado**: termina en `cuentas.clientes.dlq` y la réplica no cambia.

# ms-cuentas

Microservicio de cuentas, movimientos y reportes del reto técnico bancario. Conoce a los clientes
por una **réplica local** alimentada por los eventos de **ms-clientes** en RabbitMQ, sin llamarlo
nunca de forma síncrona.

- **Stack**: Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16, Flyway, RabbitMQ, MapStruct.
- **Especificación y diseño**: [`specs/002-ms-cuentas/`](../specs/002-ms-cuentas/).

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21) y Docker Desktop encendido.
- No hace falta Maven: se usa `mvnw`.

## Ejecución

Desde la raíz del repositorio: `docker compose up -d --build` (ver el [README raíz](../README.md)).

| Recurso | URL |
|---|---|
| API | http://localhost:8082/api |
| Swagger UI | http://localhost:8082/api/swagger-ui.html |
| Health | http://localhost:8082/api/actuator/health |

## Pruebas

```bash
cd ms-cuentas
./mvnw test        # Docker encendido: integración y Karate usan Testcontainers
```

| Prueba | Nivel | Qué valida |
|---|---|---|
| `CuentaTest` | Unitaria (TDD) | Saldo, **"Saldo no disponible"**, cuenta/cliente inactivos, corrección con recálculo en cadena |
| `ClienteRefTest`, `RangoFechasTest` | Unitaria (TDD) | Eventos fuera de orden; parseo y validación del rango de fechas |
| `*ServiceImplTest`, `ClienteEventoProcessorImplTest` | Unitaria (Mockito) | Orquestación, idempotencia, orden bloquear → leer al corregir |
| `*ControllerTest` | `@WebMvcTest` | Códigos HTTP, validaciones, ProblemDetail |
| `ClienteEventoListenerIntegrationTest` | Integración | RabbitMQ real: réplica, duplicados, orden, DLQ y **4 intentos** ante error transitorio |
| `MovimientoConcurrenciaIntegrationTest` | Integración | **20 retiros simultáneos**: el saldo nunca queda negativo |
| `ReporteRendimientoIntegrationTest` | Integración | Reporte de 1.000 movimientos en < 2 s |
| `CuentasKarateTest` | API (Karate) | **Casos de uso 2 a 5** del enunciado en un escenario ordenado, más escenarios de error |

## API

Base `http://localhost:8082/api` — contrato: [`cuentas-api.yaml`](../specs/002-ms-cuentas/contracts/cuentas-api.yaml).

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/cuentas` | Abrir cuenta (201; 409 número duplicado; 422 cliente no disponible) |
| GET | `/cuentas/{numeroCuenta}` · `/cuentas?clienteId=` | Consultar / listar |
| PUT · PATCH | `/cuentas/{numeroCuenta}` | Cambiar tipo y estado (las cuentas no se eliminan: DELETE → 405) |
| POST | `/movimientos` | Depósito (valor > 0) o retiro (valor < 0); **422 "Saldo no disponible"** |
| GET | `/movimientos/{id}` · `/movimientos?cliente=&numeroCuenta=&fecha=inicio,fin` | Consultar / listado plano (caso de uso 5) |
| PUT · PATCH | `/movimientos/{id}` | Corregir el valor; recalcula los saldos posteriores (todo o nada) |
| GET | `/reportes?fecha=AAAA-MM-DD,AAAA-MM-DD&cliente={clienteId}` | Estado de cuenta anidado (F4) |

Reglas clave:

- Montos `BigDecimal` / `NUMERIC(15,2)`, máximo 2 decimales, nunca se redondea en silencio.
- La fecha de un movimiento la asigna el sistema; el rango de fechas usa días completos en
  `America/Guayaquil` (configurable con `banco.zona-horaria`), máximo 1 año.
- Un cliente inactivo no abre cuentas ni opera; al eliminarse en ms-clientes, sus cuentas se desactivan.

## Integridad del saldo

1. **Dominio**: solo `Cuenta` crea y corrige movimientos (constructor de paquete en `Movimiento`).
2. **Concurrencia**: `SELECT … FOR UPDATE` sobre la cuenta en registrar y corregir. Al corregir se
   bloquea la cuenta *antes* de leer el movimiento, para no recalcular sobre datos viejos de la caché.
3. **Base de datos**: `CHECK (saldo_disponible >= 0)` como última línea de defensa.

## Mensajería ([contrato](../specs/002-ms-cuentas/contracts/eventos-consumidos.md))

```text
clientes.exchange ──cliente.*──► cuentas.clientes.queue ──(falla)──► cuentas.clientes.dlx ──► cuentas.clientes.dlq
```

- **Idempotencia**: tabla `evento_procesado` por `eventId`; eventos más antiguos que el último
  aplicado se descartan (`cliente_ref.ultima_actualizacion`).
- **Reintentos**: 4 intentos (1 s, 2 s, 4 s) y luego DLQ con log ERROR. Un JSON inválido no se
  reintenta: va directo a la DLQ.
- **Tolerancia**: los campos nuevos del evento se ignoran (la API REST, en cambio, rechaza campos
  desconocidos).

## Mejoras futuras

- **Resincronización de la réplica** (endpoint o replay desde un outbox en ms-clientes) para clientes
  creados mientras la cola no existía.
- **Reprocesamiento de la DLQ** con una herramienta operativa (shovel o endpoint administrativo).
- **Límite diario de retiros** y movimientos con fecha valor, si el negocio lo requiere.

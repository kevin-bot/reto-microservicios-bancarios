# Reto técnico — Microservicios bancarios

Solución en **Java 21 + Spring Boot 3.5** con dos microservicios que se comunican de forma
**asíncrona** por RabbitMQ, cada uno con su propia base de datos PostgreSQL, desplegados con Docker.

| Microservicio | Responsabilidad | Puerto |
|---|---|---|
| [`ms-clientes`](ms-clientes/README.md) | Persona y Cliente (CRUD) · publica eventos de cliente | 8081 |
| [`ms-cuentas`](ms-cuentas/README.md) | Cuentas, movimientos y reporte · réplica de clientes por eventos | 8082 |

## Arquitectura

```text
                    ┌──────────────────────┐   cliente.creado / actualizado / eliminado
  /api/clientes ───►│     ms-clientes      │──────────────┐
                    │ Persona ◄─ Cliente   │              ▼
                    └──────────┬───────────┘      ┌────────────────┐
                               │                  │    RabbitMQ    │  clientes.exchange (topic)
                        clientes_db               │                │
                               │                  └───────┬────────┘
                    ┌──────────▼───────────┐              │ cuentas.clientes.queue (+ DLQ)
                    │      PostgreSQL      │              ▼
                    └──────────▲───────────┘   ┌──────────────────────┐
                               │               │      ms-cuentas      │◄─── /api/cuentas
                          cuentas_db ──────────│ cliente_ref (réplica)│◄─── /api/movimientos
                                               │ Cuenta ◄─ Movimiento │◄─── /api/reportes
                                               └──────────────────────┘
```

- **Database-per-service**: `clientes_db` y `cuentas_db`, con usuarios distintos; ningún servicio
  lee la base del otro.
- **Sin llamadas síncronas entre servicios**: ms-cuentas mantiene una réplica (`cliente_ref`) con
  los eventos de ms-clientes; si ms-clientes cae, ms-cuentas sigue operando.
- **Capas por feature** (`controller → service (interfaz + impl) → repository`), entidades ricas
  con las reglas de negocio, DTOs `record`, errores RFC 7807 (`application/problem+json`).

## Despliegue

Requisitos: Docker Desktop. (Para ejecutar las pruebas, además Java 21.)

```bash
docker compose up -d --build
docker compose ps          # postgres, rabbitmq, ms-cuentas y ms-clientes → healthy
```

| Recurso | URL |
|---|---|
| ms-clientes | http://localhost:8081/api/clientes · Swagger: http://localhost:8081/api/swagger-ui.html |
| ms-cuentas | http://localhost:8082/api · Swagger: http://localhost:8082/api/swagger-ui.html |
| RabbitMQ management | http://localhost:15672 (guest / guest) |
| PostgreSQL (desde el host) | `localhost:5433` (usuario `postgres` / `postgres`) |

Notas:

- PostgreSQL se publica en **5433** para no chocar con una instalación local en 5432.
- **Orden de arranque**: ms-clientes espera a que ms-cuentas esté *healthy* para que su cola exista
  antes del primer evento (RabbitMQ descarta mensajes de un exchange sin colas). Es un compromiso de
  despliegue documentado; la comunicación sigue siendo asíncrona.
- **Reiniciar datos**: `docker compose down -v && docker compose up -d --build`.

## Validación de los endpoints

**Postman** — importar y ejecutar en orden con el Collection Runner:

1. [`postman/reto-banco-e2e.postman_collection.json`](postman/reto-banco-e2e.postman_collection.json):
   casos de uso **1 a 5** de punta a punta entre ambos servicios (más escenarios de error).
   **Ejecutar sobre una base limpia**: los números de cuenta del enunciado son fijos y repetir sin
   limpiar responde 409.
2. [`postman/ms-clientes.postman_collection.json`](postman/ms-clientes.postman_collection.json):
   CRUD completo de clientes (repetible: genera identificaciones únicas).

**Karate** — incluido en cada proyecto y ejecutado junto con todas las pruebas:

```bash
cd ms-clientes && ./mvnw test      # 104 pruebas
cd ms-cuentas  && ./mvnw test      # 132 pruebas
```

Las pruebas de integración y Karate levantan PostgreSQL y RabbitMQ con **Testcontainers**, por lo
que Docker debe estar encendido. Los Dockerfile compilan con `-DskipTests` por el mismo motivo.

## Cumplimiento del enunciado

| Requisito | Dónde se cumple |
|---|---|
| **F1** CRUD de Cliente; CRU de Cuenta y Movimiento | `/clientes` (POST, GET, PUT, PATCH, DELETE lógico) · `/cuentas` y `/movimientos` (POST, GET, PUT, PATCH; DELETE → 405) |
| **F2** Movimientos ±, actualización de saldo, registro | `Cuenta.registrarMovimiento`: tipo por signo, saldo resultante en cada movimiento |
| **F3** "Saldo no disponible" | `SaldoNoDisponibleException` → **422** con `title` y `detail` exactos |
| **F4** Reporte `/reportes?fecha=rango&cliente` en JSON | `GET /reportes?fecha=2022-02-01,2022-02-10&cliente={clienteId}`, estructura anidada cliente → cuentas → movimientos |
| **F5** Prueba unitaria de la entidad Cliente | `ms-clientes/.../ClienteTest.java` (44 casos, escrita con TDD) |
| **F6** Prueba de integración | `ClienteIntegrationTest` (ms-clientes) · `ClienteEventoListenerIntegrationTest`, `MovimientoConcurrenciaIntegrationTest`, `ReporteRendimientoIntegrationTest` (ms-cuentas) |
| **F7** Despliegue en contenedores | `docker-compose.yml` + Dockerfile multi-stage por servicio |
| Comunicación asíncrona | Eventos de cliente por RabbitMQ (topic exchange, DLQ, idempotencia) |
| JPA, Repository, manejo de excepciones | Spring Data JPA; `GlobalExceptionHandler` por servicio con ProblemDetail |
| `BaseDatos.sql` | [`BaseDatos.sql`](BaseDatos.sql) (esquema consolidado de ambas bases) |
| Postman / Karate | Carpeta [`postman/`](postman/) · features Karate en `src/test/resources/karate` |

## Rendimiento, escalabilidad y resiliencia

- **Escalabilidad**: servicios sin estado, escalables horizontalmente; cada uno con su base y su
  ritmo de despliegue.
- **Rendimiento**: índices por identificación, cuenta, cliente y `(cuenta_id, fecha, id)`; listados
  paginados (máx. 100); reporte en 3 consultas sin N+1 (1.000 movimientos en < 2 s, probado).
- **Integridad del dinero**: `BigDecimal`/`NUMERIC(15,2)`, bloqueo pesimista por cuenta (20 retiros
  concurrentes probados) y `CHECK (saldo_disponible >= 0)` en la base.
- **Resiliencia**: publicación de eventos después del commit; consumidor idempotente que descarta
  eventos fuera de orden; 4 intentos con backoff y DLQ; ms-cuentas opera con ms-clientes caído.

## Mejoras futuras

- **Transactional Outbox** en ms-clientes (entrega *at-least-once* y replay para resincronizar la
  réplica).
- **Bloqueo optimista** (`@Version` / `If-Match`) en la edición de clientes.
- **Validación del dígito verificador** de la cédula ecuatoriana.
- **Autenticación** (OAuth2/JWT) y observabilidad (trazas distribuidas, métricas de la DLQ).

## Cómo se construyó

Desarrollo guiado por especificación con **Spec Kit**: constitución del proyecto
([`.specify/memory/constitution.md`](.specify/memory/constitution.md)) y, por microservicio,
spec → clarificaciones → plan → tareas → análisis de consistencia → implementación con TDD
([`specs/`](specs/)).

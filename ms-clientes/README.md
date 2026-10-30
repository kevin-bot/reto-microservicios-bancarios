# ms-clientes

Microservicio de gestión de clientes del reto técnico bancario. Implementa el CRUD de `Cliente`
(que hereda de `Persona`) y publica eventos en RabbitMQ para que **ms-cuentas** mantenga una
réplica local de los clientes.

- **Stack**: Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16, Flyway, RabbitMQ, MapStruct.
- **Especificación y diseño**: [`specs/001-ms-clientes/`](../specs/001-ms-clientes/) (spec, plan,
  research, data-model, contratos y tareas).

## Requisitos

- Java 21 (`JAVA_HOME` debe apuntar a un JDK 21).
- Docker Desktop encendido (para el despliegue y para las pruebas con Testcontainers).
- No se necesita Maven instalado: se usa el wrapper `mvnw`.

## Despliegue con Docker

Desde la raíz del repositorio:

```bash
docker compose up -d --build
docker compose ps        # postgres, rabbitmq y ms-clientes deben quedar "healthy"
```

| Recurso | URL |
|---|---|
| API | http://localhost:8081/api/clientes |
| Swagger UI | http://localhost:8081/api/swagger-ui.html |
| Health | http://localhost:8081/api/actuator/health |
| RabbitMQ management | http://localhost:15672 (guest / guest) |
| PostgreSQL (desde el host) | `localhost:5433`, base `clientes_db` (clientes_user / clientes_pass) |

> PostgreSQL se publica en el puerto **5433** del host para no chocar con una instalación local
> en el 5432. Dentro de la red de Docker los servicios usan `postgres:5432`.

## Pruebas

```bash
cd ms-clientes
./mvnw test                     # Linux/macOS/Git Bash
.\mvnw test                     # PowerShell
```

Las pruebas de integración y Karate levantan sus propios contenedores (Testcontainers), por lo que
Docker debe estar encendido. El `Dockerfile` compila con `-DskipTests` por el mismo motivo: dentro
de `docker build` no hay Docker disponible.

| Prueba | Nivel | Qué valida |
|---|---|---|
| `ClienteTest` | Unitaria (F5, TDD) | Invariantes y transiciones de la entidad Cliente |
| `ClienteServiceImplTest` | Unitaria (Mockito) | Orquestación, duplicados, hash de contraseña, eventos |
| `ClienteEventPublisherTest` | Unitaria (Mockito) | Publicación tras el commit y tolerancia a la caída del broker |
| `AlMenosUnCampoValidatorTest` | Unitaria | Validación de actualizaciones parciales vacías |
| `ClienteControllerTest` | `@WebMvcTest` | Códigos HTTP, validaciones, ProblemDetail, sin contraseña |
| `ClienteIntegrationTest` | Integración (F6) | PostgreSQL + Flyway + RabbitMQ reales; ciclo completo de eventos |
| `ClientesKarateTest` | API (Karate) | Caso de uso 1 del enunciado y flujos CRUD completos |

La colección Postman [`postman/ms-clientes.postman_collection.json`](../postman/ms-clientes.postman_collection.json)
contiene el caso de uso 1 y los escenarios CRUD; ejecútala en orden con el Collection Runner.

## API

Base: `http://localhost:8081/api`. Contrato completo:
[`clientes-api.yaml`](../specs/001-ms-clientes/contracts/clientes-api.yaml).

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| POST | `/clientes` | Registrar cliente | 201, 400, 409 |
| GET | `/clientes/{clienteId}` | Consultar cliente | 200, 400, 404 |
| GET | `/clientes?page&size&sort&estado&identificacion` | Listar (paginado, filtros opcionales) | 200, 400 |
| PUT | `/clientes/{clienteId}` | Reemplazar datos editables (sin contraseña) | 200, 400, 404, 409 |
| PATCH | `/clientes/{clienteId}` | Actualización parcial (incluye contraseña y estado) | 200, 400, 404, 409 |
| DELETE | `/clientes/{clienteId}` | Eliminación lógica | 204, 404 |

Reglas principales:

- La contraseña se guarda con hash BCrypt y **nunca** se devuelve.
- La edad debe estar entre 18 y 120; la identificación es alfanumérica de 5 a 20 caracteres y única
  (incluidos los clientes eliminados).
- Un cliente eliminado responde 404 en todas las operaciones y no aparece en el listado.
- Los errores siguen RFC 7807 (`application/problem+json`): 400 datos inválidos, 404 no
  encontrado, 409 identificación duplicada, 422 regla de negocio, 500 error interno.

## Eventos

Se publican en el topic exchange `clientes.exchange` **después del commit**
(contrato: [`eventos-cliente.md`](../specs/001-ms-clientes/contracts/eventos-cliente.md)).

| Routing key | Cuándo |
|---|---|
| `cliente.creado` | POST exitoso |
| `cliente.actualizado` | PUT o PATCH exitoso |
| `cliente.eliminado` | DELETE exitoso |

```json
{ "eventId": "uuid", "tipo": "CLIENTE_CREADO", "fechaOcurrencia": "2026-10-28T15:04:05Z",
  "clienteId": "uuid", "nombre": "Jose Lema", "estado": true }
```

## Decisiones de arquitectura

- **Capas por feature** (`cliente/{controller,service,repository,entity,dto,mapper,event,exception}`
  + `shared/`), con el service definido como interfaz y su implementación (inversión de
  dependencias).
- **Dominio rico**: `Cliente` y `Persona` validan sus invariantes y exponen métodos con intención
  (`activar`, `desactivar`, `eliminar`, `cambiarContrasena`) en lugar de setters.
- **Herencia JPA `JOINED`**: tablas `persona` y `cliente`; `Persona` conserva su propia PK.
- **`clienteId` UUID** como identificador público; la PK numérica nunca se expone.
- **Eliminación lógica** para conservar la trazabilidad y la referencia en ms-cuentas.
- **Comunicación asíncrona**: ms-cuentas no consulta a ms-clientes; mantiene su réplica a partir
  de los eventos (consistencia eventual, servicios desacoplados).

### Rendimiento, escalabilidad y resiliencia

- Servicio sin estado: puede escalar horizontalmente detrás de un balanceador.
- Índices en `identificacion`, `cliente_id` y `(eliminado, estado)`; listado siempre paginado
  (máximo 100 por página).
- Si RabbitMQ no está disponible, la operación sobre el cliente se confirma igual y el fallo de
  publicación queda en el log.

### Mejoras futuras

- **Transactional Outbox**: hoy la entrega es *at-most-once* (si el servicio cae entre el commit y
  la publicación, el evento se pierde). Guardar el evento en una tabla dentro de la misma
  transacción y publicarlo con un proceso aparte daría *at-least-once*.
- **Control de concurrencia optimista** (`@Version` / `If-Match`) para evitar que una edición
  simultánea sobrescriba otra (hoy gana la última escritura).
- **Validación del dígito verificador** de la cédula ecuatoriana.

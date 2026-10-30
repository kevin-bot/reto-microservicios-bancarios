# Quickstart: Validar ms-clientes

**Feature**: `001-ms-clientes` | Contratos: [clientes-api.yaml](./contracts/clientes-api.yaml),
[eventos-cliente.md](./contracts/eventos-cliente.md) | Modelo: [data-model.md](./data-model.md)

## Prerrequisitos

- Java 21, Docker Desktop encendido (requerido por Testcontainers y por el despliegue).
- No se requiere Maven instalado: se usa `./mvnw`.

## 1. Ejecutar todas las pruebas

```bash
cd ms-clientes
./mvnw test
```

**Esperado**: `BUILD SUCCESS`. Se ejecutan:

| Prueba | Nivel | Valida |
|---|---|---|
| `ClienteTest` | Unitaria (F5) | Invariantes y transiciones de la entidad Cliente |
| `ClienteServiceImplTest` | Unitaria | Orquestación, duplicados, hash, publicación de eventos |
| `ClienteControllerTest` | Slice `@WebMvcTest` | Códigos HTTP, validaciones, ProblemDetail, sin contraseña |
| `ClienteIntegrationTest` | Integración (F6) | PostgreSQL + Flyway + RabbitMQ reales con Testcontainers |
| `ClientesKarateTest` | API (Karate) | Caso de uso 1 del enunciado y flujo CRUD completo |

## 2. Levantar con Docker

```bash
# desde la raíz del repositorio
docker compose up -d --build
docker compose ps        # postgres, rabbitmq y ms-clientes en estado healthy
```

- API: `http://localhost:8081/api/clientes`
- Swagger UI: `http://localhost:8081/api/swagger-ui.html`
- RabbitMQ management: `http://localhost:15672` (credenciales en `docker-compose.yml`)

## 3. Escenarios manuales (también en la colección Postman)

| # | Acción | Resultado esperado |
|---|---|---|
| 1 | `POST /api/clientes` con Jose Lema (ver ejemplo en el contrato) | `201`, header `Location`, cuerpo **sin** `contrasena` |
| 2 | Repetir el POST con la misma `identificacion` | `409` "Identificación duplicada" |
| 3 | `POST` con `edad: 17` | `400` con `errores.edad` |
| 4 | `GET /api/clientes/{clienteId}` | `200` con los datos |
| 5 | `GET /api/clientes?page=0&size=20` | `200`, `totalElementos` = clientes creados |
| 5b | `GET /api/clientes?identificacion=1712345678` | `200`, solo Jose Lema |
| 6 | `PATCH /api/clientes/{clienteId}` con `{"direccion":"Amazonas y NNUU"}` | `200`, solo cambia la dirección |
| 7 | `PATCH` con `{}` | `400` (al menos un campo) |
| 8 | `PUT /api/clientes/{clienteId}` con todos los campos editables (sin `contrasena`) | `200`, contraseña sin cambios |
| 8b | `PUT` incluyendo `contrasena` | `400` |
| 9 | `DELETE /api/clientes/{clienteId}` | `204` |
| 10 | `GET` del cliente eliminado | `404` |
| 11 | `POST` con la identificación del cliente eliminado | `409` |

## 4. Verificar eventos

1. En RabbitMQ management, crear una cola temporal y enlazarla a `clientes.exchange` con
   routing key `cliente.*`.
2. Ejecutar los escenarios 1, 6 y 9.
3. **Esperado**: 3 mensajes (`CLIENTE_CREADO`, `CLIENTE_ACTUALIZADO`, `CLIENTE_ELIMINADO`), cada
   uno con `eventId` distinto y sin contraseña. Los escenarios fallidos (2, 3, 7) no generan
   mensajes.

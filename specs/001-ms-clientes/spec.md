# Feature Specification: Microservicio de Clientes (ms-clientes)

**Feature Branch**: `001-ms-clientes`

**Created**: 2026-10-28

**Status**: Draft

**Input**: User description: "Microservicio ms-clientes del reto técnico: gestión de Persona y Cliente
(Cliente hereda de Persona) con CRUD completo sobre /clientes, y publicación asíncrona de eventos
de cliente para que ms-cuentas mantenga su réplica local."

## Clarifications

### Session 2026-10-28

- Q: ¿La eliminación de un cliente es física o lógica? → A: Lógica: queda marcado como
  eliminado e inactivo, fuera del listado, responde "no encontrado" y su identificación queda
  reservada.
- Q: ¿Debe exigirse que el cliente sea mayor de edad? → A: Sí, edad entre 18 y 120.
- Q: ¿Cómo se valida la identificación? → A: Alfanumérico genérico de 5 a 20 caracteres, sin
  validar algoritmo de cédula.
- Q: ¿La actualización completa incluye la contraseña? → A: No; la contraseña solo se cambia
  mediante actualización parcial.
- Q: ¿Se detectan ediciones simultáneas del mismo cliente? → A: No; gana la última escritura
  confirmada (sin control de concurrencia).
- Q: ¿Qué incluye el listado de clientes? → A: Activos e inactivos (nunca eliminados), con
  filtros opcionales por estado e identificación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un cliente (Priority: P1)

Un operador del banco registra un nuevo cliente con sus datos personales (nombre, género, edad,
identificación, dirección, teléfono), una contraseña y su estado. El sistema le asigna un
identificador de cliente único y lo deja disponible para que se le puedan abrir cuentas.

**Why this priority**: Sin clientes no existe nada más en el sistema: las cuentas, los movimientos
y los reportes dependen de ellos. Es el MVP mínimo.

**Independent Test**: Registrar a "Jose Lema", "Marianela Montalvo" y "Juan Osorio" (caso de uso 1
del enunciado) y verificar que cada uno se puede consultar después con su identificador.

**Acceptance Scenarios**:

1. **Given** no existe un cliente con identificación "1712345678", **When** el operador registra a
   "Jose Lema" con dirección "Otavalo sn y principal", teléfono "098254785", contraseña "1234" y
   estado activo, **Then** el sistema confirma el registro, devuelve los datos del cliente con su
   identificador asignado y **no** devuelve la contraseña.
2. **Given** ya existe un cliente con identificación "1712345678", **When** se intenta registrar
   otro cliente con la misma identificación, **Then** el sistema rechaza el registro indicando que
   la identificación ya está registrada.
3. **Given** un registro con campos obligatorios vacíos o inválidos (p. ej. edad 17),
   **When** el operador lo envía, **Then** el sistema lo rechaza indicando cada campo inválido.
4. **Given** un cliente registrado correctamente, **When** se completa el registro, **Then** se
   notifica a los demás servicios que existe un nuevo cliente.

---

### User Story 2 - Consultar clientes (Priority: P1)

Un operador consulta un cliente por su identificador o lista los clientes registrados.

**Why this priority**: Es necesario para verificar cualquier registro y para operar el resto del
CRUD; junto con la historia 1 forma el MVP.

**Independent Test**: Tras registrar clientes, consultarlos individualmente y en listado, y
consultar un identificador inexistente.

**Acceptance Scenarios**:

1. **Given** un cliente registrado, **When** el operador lo consulta por su identificador,
   **Then** obtiene todos sus datos excepto la contraseña.
2. **Given** varios clientes registrados (activos e inactivos), **When** el operador solicita el
   listado, **Then** obtiene todos los clientes no eliminados de forma paginada.
3. **Given** varios clientes registrados, **When** el operador filtra por identificación o por
   estado, **Then** obtiene solo los clientes que cumplen el filtro.
4. **Given** un identificador que no existe, **When** el operador lo consulta, **Then** el sistema
   responde que el cliente no fue encontrado.

---

### User Story 3 - Actualizar un cliente (Priority: P2)

Un operador modifica los datos de un cliente: puede reemplazar todos sus datos editables o
modificar solo algunos (p. ej. solo la dirección, solo el estado o solo la contraseña).

**Why this priority**: Mantener la información correcta es necesario, pero no bloquea el flujo
principal de abrir cuentas y registrar movimientos.

**Independent Test**: Actualizar la dirección de un cliente y desactivarlo, y verificar el cambio
al consultarlo.

**Acceptance Scenarios**:

1. **Given** un cliente registrado, **When** el operador reemplaza todos sus datos editables
   (excepto la contraseña) con valores válidos, **Then** el sistema guarda los nuevos valores, los
   devuelve y la contraseña no cambia.
2. **Given** un cliente registrado, **When** el operador modifica solo su dirección, **Then** solo
   la dirección cambia y el resto de datos se conserva.
3. **Given** un cliente registrado, **When** el operador cambia su contraseña mediante una
   actualización parcial, **Then** la nueva
   contraseña queda almacenada de forma protegida y no se devuelve en la respuesta.
4. **Given** un cliente activo, **When** el operador lo desactiva, **Then** su estado queda
   inactivo.
5. **Given** cualquier actualización exitosa, **When** se completa, **Then** se notifica a los
   demás servicios el cambio del cliente.
6. **Given** una actualización que intenta usar la identificación de otro cliente existente,
   **When** se envía, **Then** el sistema la rechaza por identificación duplicada.

---

### User Story 4 - Eliminar un cliente (Priority: P3)

Un operador elimina un cliente del sistema.

**Why this priority**: El enunciado exige el CRUD completo de Cliente, pero es la operación menos
frecuente y la de mayor impacto en otros servicios.

**Independent Test**: Eliminar un cliente y verificar que ya no aparece como disponible y que se
notificó la eliminación.

**Acceptance Scenarios**:

1. **Given** un cliente registrado, **When** el operador lo elimina, **Then** el sistema confirma
   la eliminación, el cliente queda marcado como eliminado e inactivo y deja de aparecer en el
   listado.
2. **Given** un cliente eliminado, **When** el operador lo consulta por su identificador,
   **Then** el sistema responde que el cliente no fue encontrado.
3. **Given** un cliente eliminado, **When** se completa la eliminación, **Then** se notifica a los
   demás servicios que el cliente fue eliminado.
4. **Given** un identificador inexistente o ya eliminado, **When** se intenta eliminar, **Then** el sistema
   responde que el cliente no fue encontrado.

---

### Edge Cases

- Registrar o actualizar con una identificación que ya pertenece a otro cliente → se rechaza como
  conflicto.
- Edad fuera de rango (menor a 18 o mayor a 120) → se rechaza como dato inválido.
- Identificación con caracteres no alfanuméricos o longitud fuera de 5–20 → se rechaza.
- Actualización completa que incluye contraseña → el campo no está permitido y la solicitud se
  rechaza como dato inválido.
- Dos operadores actualizan el mismo cliente a la vez → prevalece la última actualización
  confirmada; ambas generan su notificación.
- Género fuera de los valores permitidos → se rechaza como dato inválido.
- Teléfono con caracteres no numéricos o longitud fuera de 7–15 dígitos → se rechaza.
- Contraseña vacía o menor a 4 caracteres → se rechaza.
- Actualización parcial sin ningún campo → se rechaza como dato inválido.
- Cuerpo de la solicitud mal formado → se rechaza indicando que la solicitud no es legible.
- El servicio de mensajería no está disponible al registrar/actualizar/eliminar → la operación
  sobre el cliente se completa igualmente y el fallo de notificación queda registrado en el log
  (riesgo aceptado; ver Principio IV de la constitución).
- Registrar un cliente con la identificación de un cliente eliminado → se rechaza como conflicto.
- Error inesperado → respuesta genérica sin detalles internos.

## Requirements *(mandatory)*

### Functional Requirements

**Datos y registro**

- **FR-001**: El sistema MUST permitir registrar un cliente con: nombre, género, edad,
  identificación, dirección, teléfono, contraseña y estado.
- **FR-002**: Todos los campos de FR-001 MUST ser obligatorios al registrar, excepto el estado,
  que por defecto es activo.
- **FR-003**: El sistema MUST asignar a cada cliente un identificador de cliente único
  ("clienteId") generado por el sistema, que no puede modificarse.
- **FR-004**: La identificación (documento de identidad) MUST ser única entre todos los clientes.
- **FR-005**: Los valores de género permitidos MUST ser: MASCULINO, FEMENINO, OTRO.
- **FR-006**: La edad MUST estar entre 18 y 120; la identificación MUST ser alfanumérica de 5 a 20
  caracteres; el teléfono MUST contener solo dígitos (7–15); la contraseña MUST tener al menos 4
  caracteres.

**Seguridad**

- **FR-007**: La contraseña MUST almacenarse de forma irreversible (hash) y MUST NOT devolverse en
  ninguna respuesta.

**Consulta**

- **FR-008**: El sistema MUST permitir consultar un cliente por su identificador.
- **FR-009**: El sistema MUST permitir listar de forma paginada los clientes activos e inactivos
  (nunca los eliminados), con filtros opcionales y combinables por estado y por identificación.

**Actualización**

- **FR-010**: El sistema MUST permitir el reemplazo completo de los datos editables de un cliente
  (todos los campos de FR-001 excepto el identificador de cliente y la contraseña).
- **FR-011**: El sistema MUST permitir la actualización parcial de uno o más campos editables;
  los campos no enviados se conservan. Es el único medio para cambiar la contraseña.

**Eliminación**

- **FR-012**: El sistema MUST permitir eliminar un cliente mediante **eliminación lógica**: el
  cliente queda marcado como eliminado e inactivo, y su registro se conserva para trazabilidad.
- **FR-012a**: Un cliente eliminado MUST NOT aparecer en el listado; consultarlo, actualizarlo o
  eliminarlo de nuevo MUST responder que el cliente no fue encontrado.
- **FR-012b**: La identificación de un cliente eliminado MUST seguir reservada: no puede
  reutilizarse para registrar otro cliente.

**Integración con otros servicios**

- **FR-013**: El sistema MUST notificar de forma asíncrona a los demás servicios cada vez que un
  cliente se crea, se actualiza o se elimina.
- **FR-014**: Cada notificación MUST incluir un identificador único de evento, el tipo de evento,
  la fecha de ocurrencia y los datos del cliente necesarios para otros servicios (identificador
  de cliente, nombre y estado).
- **FR-015**: Las notificaciones MUST emitirse solo después de que el cambio quede confirmado de
  forma permanente; un cambio fallido MUST NOT generar notificación.

**Errores**

- **FR-016**: Toda respuesta de error MUST seguir un formato estándar que indique el tipo de
  error, un mensaje legible y, para errores de validación, el detalle por campo.
- **FR-017**: El sistema MUST distinguir: datos inválidos, cliente no encontrado, identificación
  duplicada y error inesperado.

**Datos iniciales**

- **FR-018**: Los datos del caso de uso 1 del enunciado (Jose Lema, Marianela Montalvo, Juan
  Osorio) MUST poder registrarse con la colección de pruebas de API entregada.

### Key Entities

- **Persona**: datos personales de un individuo: nombre, género, edad, identificación (única),
  dirección y teléfono. Tiene su propia clave primaria.
- **Cliente**: una Persona que es cliente del banco. Agrega: identificador de cliente (único,
  generado por el sistema), contraseña (protegida), estado (activo/inactivo) y marca de
  eliminado. Comportamiento propio: activarse, desactivarse, cambiar contraseña, actualizar
  datos de contacto y eliminarse (lógicamente).
- **Evento de Cliente**: notificación de un cambio en un cliente (creado, actualizado, eliminado)
  con identificador único, tipo, fecha de ocurrencia y datos mínimos del cliente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los 3 clientes del caso de uso 1 del enunciado se registran correctamente y se
  pueden consultar después, sin intervención manual sobre la base de datos.
- **SC-002**: El 100% de las operaciones de crear, consultar, actualizar (completa y parcial) y
  eliminar clientes están cubiertas por pruebas automatizadas que pasan.
- **SC-003**: Ninguna respuesta del servicio, exitosa o de error, contiene la contraseña del
  cliente (verificado en todas las pruebas de API).
- **SC-004**: El 100% de las solicitudes inválidas reciben un mensaje que identifica el campo o
  la regla incumplida.
- **SC-005**: Cada creación, actualización y eliminación confirmada produce exactamente una
  notificación observable por otros servicios en menos de 5 segundos.
- **SC-006**: Un operador puede registrar un cliente y consultarlo en menos de 1 minuto usando la
  colección de pruebas de API entregada.

## Assumptions

- Los usuarios del servicio son operadores/sistemas internos del banco; la autenticación y
  autorización de quien llama a la API está fuera del alcance del reto.
- La contraseña del cliente se almacena como dato del cliente; el inicio de sesión de clientes
  está fuera del alcance.
- Los datos de ejemplo del enunciado no incluyen género, edad ni identificación; la colección de
  pruebas completará esos campos con valores válidos.
- El listado de clientes usa paginación con tamaño por defecto de 20 elementos.
- La identificación puede modificarse por actualización, siempre que siga siendo única.
- No hay control de concurrencia sobre clientes: ante ediciones simultáneas gana la última
  escritura confirmada (riesgo aceptado: baja contención en datos personales).
- Las consecuencias de eliminar o desactivar un cliente sobre sus cuentas las decide ms-cuentas
  (feature posterior) a partir de las notificaciones; ms-clientes no conoce las cuentas.
- Si la mensajería no está disponible, el cambio del cliente no se revierte; la publicación
  garantizada (outbox) queda como mejora futura según la constitución.

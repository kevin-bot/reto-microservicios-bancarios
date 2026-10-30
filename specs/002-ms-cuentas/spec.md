# Feature Specification: Microservicio de Cuentas y Movimientos (ms-cuentas)

**Feature Branch**: `002-ms-cuentas`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "Microservicio ms-cuentas del reto técnico: gestión de Cuenta y
Movimiento (crear, leer y actualizar) sobre /cuentas y /movimientos, registro de movimientos con
actualización de saldo y alerta 'Saldo no disponible', reporte de estado de cuenta por rango de
fechas y cliente en /reportes, y réplica local de clientes alimentada por los eventos asíncronos
de ms-clientes."

## Clarifications

### Session 2026-10-29

- Q: ¿Qué significa actualizar un movimiento? → A: Se puede corregir el valor de cualquier
  movimiento (ejercicio de prueba, sin restricciones contables); se recalculan en cadena los
  saldos resultantes posteriores y el saldo disponible de la cuenta, y se rechaza con "Saldo no
  disponible" si algún saldo quedara negativo.
- Q: ¿Cómo se envían los parámetros del reporte? → A: `/reportes?fecha=AAAA-MM-DD,AAAA-MM-DD&cliente={clienteId}`
  (un parámetro `fecha` con dos fechas ISO separadas por coma; cliente por su clienteId).
- Q: ¿Qué estructura tiene el reporte? → A: Anidada: cliente y rango, y una lista de cuentas
  con sus saldos, cada una con sus movimientos del rango.
- Q: ¿Qué pasa con las cuentas de un cliente inactivo (no eliminado)? → A: Rechazan movimientos
  con "Cliente no disponible" mientras el cliente siga inactivo, sin cambiar el estado de la
  cuenta; vuelven a operar solas al reactivarse el cliente.
- Q: ¿El listado de movimientos se puede filtrar por cliente? → A: Sí: filtros por cliente, cuenta
  y rango de fechas, con filas planas que incluyen cliente y datos de la cuenta (caso de uso 5).
- Q: ¿Quién asigna la fecha del movimiento? → A: Siempre el sistema al registrarlo; no se aceptan
  fechas en la petición.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conocer a los clientes del banco (Priority: P1)

El servicio de cuentas se mantiene informado de los clientes que existen, su nombre y si están
activos, a partir de las notificaciones que emite el servicio de clientes, sin consultarlo
directamente. Así puede validar a quién se le abre una cuenta y mostrar el nombre del cliente en
los reportes aunque el servicio de clientes no esté disponible.

**Why this priority**: Sin conocer a los clientes no se pueden abrir cuentas ni emitir reportes;
es prerrequisito de todas las demás historias.

**Independent Test**: Registrar un cliente en ms-clientes (o publicar su notificación) y verificar
que ms-cuentas permite abrirle una cuenta; desactivarlo y verificar que ya no lo permite.

**Acceptance Scenarios**:

1. **Given** se recibe la notificación de un cliente creado, **When** se procesa, **Then** el
   cliente queda disponible para abrirle cuentas con su nombre y estado.
2. **Given** un cliente conocido, **When** se recibe la notificación de que fue actualizado,
   **Then** se reflejan su nombre y estado actuales.
3. **Given** un cliente conocido, **When** se recibe la notificación de que fue eliminado,
   **Then** el cliente queda inactivo y sus cuentas se desactivan.
4. **Given** una notificación ya procesada, **When** llega de nuevo (duplicada), **Then** no
   produce ningún cambio adicional.
5. **Given** una notificación más antigua que la última aplicada para ese cliente, **When** llega
   fuera de orden, **Then** se descarta.
6. **Given** una notificación que no se puede procesar, **When** falla tras varios reintentos,
   **Then** se aparta para revisión sin bloquear las siguientes.

---

### User Story 2 - Abrir y administrar cuentas (Priority: P1)

Un operador abre cuentas de ahorros o corrientes para un cliente con un saldo inicial, las
consulta y actualiza su tipo o estado.

**Why this priority**: Las cuentas son la base de los movimientos y del reporte (casos de uso 2
y 3 del enunciado).

**Independent Test**: Crear las cuentas de los casos de uso 2 y 3 (478758, 225487, 495878,
496825, 585545) y consultarlas por número y por cliente.

**Acceptance Scenarios**:

1. **Given** un cliente activo conocido, **When** el operador abre la cuenta 478758 de ahorros con
   saldo inicial 2000, **Then** la cuenta queda activa con saldo disponible 2000.
2. **Given** una cuenta 478758 existente, **When** se intenta abrir otra con el mismo número,
   **Then** se rechaza por número de cuenta duplicado.
3. **Given** un cliente inexistente o inactivo, **When** se intenta abrirle una cuenta, **Then**
   se rechaza indicando que el cliente no está disponible.
4. **Given** cuentas existentes, **When** el operador las consulta por número o lista las de un
   cliente, **Then** obtiene número, tipo, saldo inicial, saldo disponible, estado y cliente.
5. **Given** una cuenta activa, **When** el operador cambia su tipo o la desactiva, **Then** el
   cambio se guarda; el número, el cliente y el saldo inicial no se pueden modificar.
6. **Given** un cliente con una cuenta de ahorros (Jose Lema), **When** se le abre una cuenta
   corriente 585545 con saldo 1000 (caso de uso 3), **Then** el cliente queda con dos cuentas.

---

### User Story 3 - Registrar movimientos (Priority: P1)

Un operador registra depósitos (valores positivos) y retiros (valores negativos) sobre una cuenta.
Cada movimiento actualiza el saldo disponible y queda registrado con el saldo resultante. Si un
retiro deja la cuenta en negativo, se rechaza con el mensaje "Saldo no disponible".

**Why this priority**: Es el núcleo del negocio y cubre las funcionalidades F2 y F3 (caso de uso
4 del enunciado).

**Independent Test**: Sobre las cuentas del caso de uso 2, registrar: retiro de 575 en 478758,
depósito de 600 en 225487, depósito de 150 en 495878 y retiro de 540 en 496825; verificar los
saldos 1425, 700, 150 y 0. Intentar un retiro de 1 en 496825 y recibir "Saldo no disponible".

**Acceptance Scenarios**:

1. **Given** la cuenta 478758 con saldo 2000, **When** se registra un movimiento de -575,
   **Then** el movimiento queda como retiro con saldo resultante 1425 y la cuenta queda con saldo
   disponible 1425.
2. **Given** la cuenta 225487 con saldo 100, **When** se registra un movimiento de 600, **Then**
   queda como depósito con saldo resultante 700.
3. **Given** la cuenta 496825 con saldo 540, **When** se registra un movimiento de -540, **Then**
   el saldo resultante es 0 (retirar todo el saldo está permitido).
4. **Given** la cuenta 496825 con saldo 0, **When** se intenta un movimiento de -1, **Then** se
   rechaza con el mensaje "Saldo no disponible", no se registra el movimiento y el saldo no
   cambia.
5. **Given** una cuenta inactiva, **When** se intenta registrar un movimiento, **Then** se rechaza
   indicando que la cuenta está inactiva.
5b. **Given** una cuenta activa de un cliente inactivo, **When** se intenta registrar un
   movimiento, **Then** se rechaza con "Cliente no disponible"; cuando el cliente se reactiva, la
   misma cuenta vuelve a aceptar movimientos sin intervención del operador.
6. **Given** dos retiros simultáneos sobre la misma cuenta cuya suma excede el saldo, **When** se
   procesan, **Then** uno se aplica y el otro se rechaza con "Saldo no disponible"; el saldo nunca
   queda negativo.
7. **Given** movimientos registrados, **When** el operador los consulta por identificador o los
   lista por cliente, por cuenta y/o por rango de fechas, **Then** obtiene por cada movimiento:
   fecha, cliente, número y tipo de cuenta, saldo inicial, estado de la cuenta, valor y saldo
   resultante (caso de uso 5: movimientos de Marianela Montalvo en el rango).
8. **Given** la cuenta 478758 con movimientos -575 (saldo 1425) y luego +100 (saldo 1525),
   **When** el operador corrige el primero a -500, **Then** sus saldos resultantes pasan a 1500 y
   1600, y el saldo disponible de la cuenta queda en 1600.
9. **Given** una corrección que dejaría algún saldo resultante por debajo de 0, **When** se
   envía, **Then** se rechaza con "Saldo no disponible" y no cambia ningún movimiento ni saldo.

---

### User Story 4 - Reporte de estado de cuenta (Priority: P2)

Un operador genera el estado de cuenta de un cliente para un rango de fechas: sus cuentas con sus
saldos y el detalle de los movimientos de cada cuenta en ese rango.

**Why this priority**: Es la funcionalidad F4 del enunciado; depende de que existan cuentas y
movimientos.

**Independent Test**: Tras el caso de uso 4, generar el reporte de Marianela Montalvo para el
rango que incluye sus movimientos y obtener sus cuentas 225487 (saldo 700, movimiento +600) y
496825 (saldo 0, movimiento -540), como en el caso de uso 5.

**Acceptance Scenarios**:

1. **Given** un cliente con cuentas y movimientos, **When** se solicita su reporte para un rango
   de fechas, **Then** se obtienen todas sus cuentas con saldo inicial, saldo disponible, tipo y
   estado, y los movimientos de cada cuenta dentro del rango (ambos extremos incluidos), ordenados
   por fecha.
2. **Given** un rango sin movimientos, **When** se solicita el reporte, **Then** se obtienen las
   cuentas con sus saldos y la lista de movimientos vacía.
3. **Given** una fecha de inicio posterior a la fecha de fin, o fechas con formato inválido,
   **When** se solicita el reporte, **Then** se rechaza indicando el parámetro inválido.
4. **Given** un cliente desconocido, **When** se solicita su reporte, **Then** se responde que el
   cliente no fue encontrado.
5. **Given** cualquier reporte, **When** se genera, **Then** se entrega en formato JSON con la
   estructura definida en FR-024.

---

### Edge Cases

- Movimiento con valor 0 → se rechaza como dato inválido.
- Valores con más de 2 decimales → se rechazan como dato inválido.
- Saldo inicial negativo → se rechaza como dato inválido.
- Número de cuenta con caracteres no numéricos o longitud fuera de 6–20 → se rechaza.
- Tipo de cuenta distinto de AHORROS o CORRIENTE → se rechaza.
- Movimiento sobre una cuenta inexistente → se responde que la cuenta no fue encontrada.
- Movimiento sobre una cuenta de un cliente eliminado → la cuenta ya está inactiva, se rechaza.
- Se abre una cuenta para un cliente recién creado cuya notificación aún no llegó → se rechaza
  como cliente no disponible; el operador puede reintentar en segundos (consistencia eventual).
- Notificación de cliente con formato inválido → tras los reintentos se aparta para revisión
  (cola de mensajes fallidos) y se registra en el log.
- Servicio de clientes caído → ms-cuentas sigue operando con su réplica local.
- Petición de movimiento que incluye una fecha → se rechaza como solicitud mal formada (campo no
  permitido).
- Reporte con un rango muy amplio → se entrega igualmente; el rango máximo permitido es de 1 año.
- Corrección de un movimiento con valor 0 → se rechaza como dato inválido.
- Corrección de un movimiento de una cuenta inactiva → se rechaza indicando que la cuenta está
  inactiva.
- Parámetro `fecha` sin coma, con más de dos fechas o con fechas no ISO → se rechaza como
  parámetro inválido.
- Error inesperado → respuesta genérica sin detalles internos.

## Requirements *(mandatory)*

### Functional Requirements

**Réplica de clientes**

- **FR-001**: El sistema MUST mantener una réplica local de clientes (identificador, nombre,
  estado) actualizada a partir de las notificaciones de cliente creado, actualizado y eliminado.
- **FR-002**: El procesamiento de notificaciones MUST ser idempotente: una notificación con un
  identificador de evento ya procesado no produce cambios.
- **FR-003**: Una notificación con fecha de ocurrencia anterior a la última aplicada para ese
  cliente MUST descartarse.
- **FR-004**: Una notificación que falla MUST reintentarse al menos 3 veces con espera creciente
  y, si sigue fallando, apartarse en una cola de mensajes fallidos sin bloquear las siguientes.
- **FR-005**: Al procesar la eliminación de un cliente, el sistema MUST marcarlo inactivo y
  desactivar todas sus cuentas.

**Cuentas**

- **FR-006**: El sistema MUST permitir abrir una cuenta con: número de cuenta, tipo (AHORROS o
  CORRIENTE), saldo inicial, estado (activo por defecto) y cliente.
- **FR-007**: El número de cuenta MUST ser único y numérico de 6 a 20 dígitos; lo asigna el
  operador.
- **FR-008**: Solo se MUST poder abrir cuentas a clientes conocidos y activos.
- **FR-009**: El saldo inicial MUST ser mayor o igual a 0, con hasta 2 decimales; al abrir la
  cuenta, el saldo disponible es igual al saldo inicial.
- **FR-010**: El sistema MUST permitir consultar una cuenta por su número y listar cuentas de
  forma paginada, con filtro opcional por cliente.
- **FR-011**: El sistema MUST permitir actualizar el tipo y el estado de una cuenta (completa y
  parcialmente). El número, el cliente, el saldo inicial y el saldo disponible MUST NOT
  modificarse por actualización.
- **FR-012**: Las cuentas MUST NOT eliminarse (el enunciado pide crear, leer y actualizar); se
  desactivan.

**Movimientos**

- **FR-013**: El sistema MUST permitir registrar un movimiento sobre una cuenta indicando su
  valor: positivo para depósito, negativo para retiro; el valor 0 no está permitido.
- **FR-014**: El tipo de movimiento (DEPOSITO o RETIRO) MUST derivarse del signo del valor.
- **FR-015**: Cada movimiento MUST registrar fecha y hora, tipo, valor y saldo resultante, y
  actualizar el saldo disponible de la cuenta. La fecha y hora MUST asignarlas el sistema al
  registrar; la petición MUST NOT poder indicarla.
- **FR-016**: Un movimiento que deje el saldo disponible por debajo de 0 MUST rechazarse con el
  mensaje exacto **"Saldo no disponible"**, sin registrar el movimiento ni alterar el saldo.
- **FR-017**: Solo se MUST poder registrar o corregir movimientos sobre cuentas activas.
- **FR-017a**: Si el cliente dueño de la cuenta está inactivo, registrar o corregir movimientos
  MUST rechazarse con "Cliente no disponible", sin cambiar el estado de la cuenta; al reactivarse
  el cliente, sus cuentas activas vuelven a aceptar movimientos.
- **FR-018**: Movimientos simultáneos sobre una misma cuenta MUST procesarse de forma que el saldo
  nunca quede negativo ni se pierdan actualizaciones.
- **FR-019**: El sistema MUST permitir corregir el valor de cualquier movimiento (completa o
  parcialmente). Al corregirlo, se recalculan el tipo (por el signo), su saldo resultante, los
  saldos resultantes de todos los movimientos posteriores de la cuenta y el saldo disponible.
- **FR-019a**: Si la corrección deja algún saldo resultante por debajo de 0, MUST rechazarse con
  "Saldo no disponible" sin modificar nada. La fecha, la cuenta y el identificador de un movimiento
  MUST NOT modificarse.
- **FR-020**: El sistema MUST permitir consultar un movimiento por su identificador y listar los
  movimientos de forma paginada con filtros opcionales y combinables por cliente, por cuenta y por
  rango de fechas (mismo formato de rango que el reporte).
- **FR-020a**: Cada movimiento consultado o listado MUST incluir: fecha, cliente (identificador y
  nombre), número y tipo de cuenta, saldo inicial y estado de la cuenta, tipo, valor y saldo
  resultante.

**Reporte de estado de cuenta**

- **FR-021**: El sistema MUST generar el estado de cuenta de un cliente para un rango de fechas
  en el recurso `/reportes`, en formato JSON.
- **FR-022**: El reporte MUST solicitarse con dos parámetros obligatorios: `fecha`, con la fecha
  de inicio y la de fin en formato ISO (AAAA-MM-DD) separadas por coma (p. ej.
  `fecha=2022-02-01,2022-02-10`), y `cliente`, con el identificador del cliente (clienteId).
- **FR-023**: El rango de fechas MUST incluir ambos extremos (días completos), la fecha de inicio
  MUST ser anterior o igual a la de fin y el rango MUST NOT superar 1 año.
- **FR-024**: El reporte MUST tener estructura anidada: datos del cliente (identificador y
  nombre), rango consultado, y la lista de todas sus cuentas (número, tipo, saldo inicial, saldo
  disponible, estado), cada una con la lista de sus movimientos del rango (fecha, tipo, valor,
  saldo resultante). Las cuentas sin movimientos en el rango aparecen con la lista vacía.

**Errores**

- **FR-025**: Toda respuesta de error MUST seguir un formato estándar con tipo, mensaje legible y,
  para validaciones, el detalle por campo.
- **FR-026**: El sistema MUST distinguir: datos inválidos, recurso no encontrado (cuenta, cliente,
  movimiento), número de cuenta duplicado, saldo no disponible, cuenta inactiva, cliente no
  disponible y error inesperado.

**Datos de prueba**

- **FR-027**: Los casos de uso 2 a 5 del enunciado MUST poder ejecutarse con la colección de
  pruebas de API entregada, partiendo de los clientes del caso de uso 1.

### Key Entities

- **Cliente (réplica)**: copia local de solo lectura de un cliente de ms-clientes: identificador
  del cliente, nombre, estado y fecha de la última notificación aplicada. No se crea ni modifica
  por la API de ms-cuentas.
- **Cuenta**: número de cuenta (único), tipo (AHORROS/CORRIENTE), saldo inicial, saldo
  disponible, estado y cliente al que pertenece. Comportamiento propio: aplicar un movimiento
  (rechazando si no hay saldo, si está inactiva o si su cliente está inactivo), activarse,
  desactivarse, cambiar de tipo.
- **Movimiento**: transacción sobre una cuenta: identificador, fecha y hora, tipo
  (DEPOSITO/RETIRO), valor (con signo) y saldo resultante. Su valor puede corregirse, lo que
  recalcula los saldos posteriores; la fecha y la cuenta no cambian.
- **Evento procesado**: identificador de cada notificación ya aplicada, para garantizar la
  idempotencia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los casos de uso 2 a 5 del enunciado se ejecutan de punta a punta con la colección
  de pruebas de API y producen exactamente los saldos esperados (1425, 700, 150, 0 y 1000 para la
  cuenta 585545).
- **SC-002**: El 100% de los retiros que exceden el saldo reciben el mensaje "Saldo no disponible"
  y ninguna cuenta queda jamás con saldo negativo, incluso con 20 retiros simultáneos sobre la
  misma cuenta.
- **SC-003**: Un cliente creado en ms-clientes puede recibir una cuenta en ms-cuentas en menos de
  5 segundos.
- **SC-004**: Una notificación duplicada o fuera de orden no modifica la réplica (verificado con
  pruebas automatizadas).
- **SC-005**: Con el servicio de clientes detenido, ms-cuentas sigue registrando movimientos y
  generando reportes.
- **SC-006**: El reporte de un cliente con hasta 1.000 movimientos en el rango se entrega en
  menos de 2 segundos.
- **SC-007**: El 100% de las operaciones de cuentas, movimientos y reportes están cubiertas por
  pruebas automatizadas que pasan.

## Assumptions

- Los usuarios del servicio son operadores/sistemas internos del banco; la autenticación de la API
  está fuera del alcance.
- La moneda es única (USD); los montos usan 2 decimales.
- El número de cuenta lo asigna el operador (el enunciado trae números concretos).
- La fecha del movimiento la asigna el sistema al registrarlo; no se pueden registrar movimientos
  con fecha pasada o futura.
- "Saldos" del reporte = saldo inicial y saldo disponible actual de cada cuenta; cada movimiento
  muestra su saldo resultante.
- No hay límite diario de retiros (el enunciado no lo pide).
- Los clientes del caso de uso 1 se crean en ms-clientes y llegan a ms-cuentas por notificación.
- Listados paginados con tamaño por defecto de 20 elementos y máximo de 100.
- Las notificaciones de cliente siguen el contrato de `specs/001-ms-clientes/contracts/eventos-cliente.md`.

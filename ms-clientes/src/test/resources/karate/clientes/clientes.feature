Feature: API de clientes (ms-clientes)

  Background:
    * url karate.properties['karate.baseUrl']
    * def UUID = Java.type('java.util.UUID')
    * def identificacionUnica = function() { return 'K' + UUID.randomUUID().toString().replaceAll('-', '').substring(0, 12) }

  Scenario Outline: Caso de uso 1 - creación de usuarios: <nombre>
    Given path 'clientes'
    And request
      """
      {
        "nombre": "<nombre>",
        "genero": "<genero>",
        "edad": <edad>,
        "identificacion": "<identificacion>",
        "direccion": "<direccion>",
        "telefono": "<telefono>",
        "contrasena": "<contrasena>",
        "estado": true
      }
      """
    When method post
    Then status 201
    And match responseHeaders['Location'][0] contains '/api/clientes/'
    And match response ==
      """
      {
        clienteId: '#uuid',
        nombre: '<nombre>',
        genero: '<genero>',
        edad: <edad>,
        identificacion: '<identificacion>',
        direccion: '<direccion>',
        telefono: '<telefono>',
        estado: true
      }
      """
    And match response.contrasena == '#notpresent'

    Examples:
      | nombre             | genero    | edad | identificacion | direccion              | telefono  | contrasena |
      | Jose Lema          | MASCULINO | 35   | 1712345678     | Otavalo sn y principal | 098254785 | 1234       |
      | Marianela Montalvo | FEMENINO  | 30   | 1723456789     | Amazonas y NNUU        | 097548965 | 5678       |
      | Juan Osorio        | MASCULINO | 42   | 1734567890     | 13 junio y Equinoccial | 098874587 | 1245       |

  Scenario: Registrar una identificación ya existente responde 409
    * def identificacion = identificacionUnica()
    * def cliente = { nombre: 'Cliente Duplicado', genero: 'OTRO', edad: 28, identificacion: '#(identificacion)', direccion: 'Quito centro', telefono: '0991234567', contrasena: 'clave1' }
    Given path 'clientes'
    And request cliente
    When method post
    Then status 201

    Given path 'clientes'
    And request cliente
    When method post
    Then status 409
    And match response.title == 'Identificación duplicada'

  Scenario: Registrar un menor de edad responde 400 con el detalle del campo
    Given path 'clientes'
    And request { nombre: 'Menor de Edad', genero: 'FEMENINO', edad: 17, identificacion: '#(identificacionUnica())', direccion: 'Quito', telefono: '0991234567', contrasena: 'clave1' }
    When method post
    Then status 400
    And match response.errores.edad == '#string'

  Scenario: Consultar un cliente por su identificador
    * def identificacion = identificacionUnica()
    Given path 'clientes'
    And request { nombre: 'Cliente Consulta', genero: 'FEMENINO', edad: 45, identificacion: '#(identificacion)', direccion: 'Cuenca', telefono: '0987654321', contrasena: 'clave1' }
    When method post
    Then status 201
    * def clienteId = response.clienteId

    Given path 'clientes', clienteId
    When method get
    Then status 200
    And match response.clienteId == clienteId
    And match response.identificacion == identificacion
    And match response.contrasena == '#notpresent'

  Scenario: Consultar un identificador inexistente responde 404
    Given path 'clientes', UUID.randomUUID().toString()
    When method get
    Then status 404
    And match response.title == 'Cliente no encontrado'

  Scenario: Listar clientes paginado y filtrar por identificación
    * def identificacion = identificacionUnica()
    Given path 'clientes'
    And request { nombre: 'Cliente Listado', genero: 'OTRO', edad: 50, identificacion: '#(identificacion)', direccion: 'Loja', telefono: '0981112233', contrasena: 'clave1', estado: false }
    When method post
    Then status 201

    Given path 'clientes'
    And params { page: 0, size: 20 }
    When method get
    Then status 200
    And match response == { contenido: '#[_ > 0]', pagina: 0, tamano: 20, totalElementos: '#number', totalPaginas: '#number' }
    And match each response.contenido == { clienteId: '#uuid', nombre: '#string', genero: '#string', edad: '#number', identificacion: '#string', direccion: '#string', telefono: '#string', estado: '#boolean' }

    Given path 'clientes'
    And param identificacion = identificacion
    When method get
    Then status 200
    And match response.totalElementos == 1
    And match response.contenido[0].identificacion == identificacion
    And match response.contenido[0].estado == false

    Given path 'clientes'
    And params { identificacion: '#(identificacion)', estado: true }
    When method get
    Then status 200
    And match response.totalElementos == 0

  Scenario: Ordenar por un campo inexistente responde 400
    Given path 'clientes'
    And param sort = 'campoInexistente'
    When method get
    Then status 400
    And match response.title == 'Parámetro inválido'

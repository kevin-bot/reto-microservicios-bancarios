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

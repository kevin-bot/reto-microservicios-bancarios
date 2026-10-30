Feature: Reporte de estado de cuenta - validaciones

  Background:
    * url karate.properties['karate.baseUrl']
    * def pruebas = karate.properties['karate.pruebasId']
    * def UUID = Java.type('java.util.UUID')

  Scenario: Un rango invertido responde 400
    Given path 'reportes'
    And params { fecha: '2022-02-10,2022-02-01', cliente: '#(pruebas)' }
    When method get
    Then status 400
    And match response.title == 'Parámetro inválido'

  Scenario: Un rango de más de un año responde 400
    Given path 'reportes'
    And params { fecha: '2022-01-01,2023-06-01', cliente: '#(pruebas)' }
    When method get
    Then status 400
    And match response.title == 'Parámetro inválido'

  Scenario: Faltan parámetros obligatorios
    Given path 'reportes'
    And param cliente = pruebas
    When method get
    Then status 400

  Scenario: Un cliente desconocido responde 404
    Given path 'reportes'
    And params { fecha: '2022-02-01,2022-02-10', cliente: '#(UUID.randomUUID().toString())' }
    When method get
    Then status 404
    And match response.title == 'Cliente no encontrado'

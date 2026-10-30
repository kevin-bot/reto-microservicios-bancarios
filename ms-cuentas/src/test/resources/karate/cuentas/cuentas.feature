Feature: Cuentas - escenarios de error y actualización

  Usan "Cliente Pruebas" y números de cuenta aleatorios para no alterar los casos de uso.

  Background:
    * url karate.properties['karate.baseUrl']
    * def pruebas = karate.properties['karate.pruebasId']
    * def numeroAleatorio = function() { return '' + Math.floor(100000000000 + Math.random() * 899999999999) }
    * def UUID = Java.type('java.util.UUID')
    * def uuidAleatorio = function() { return UUID.randomUUID().toString() }
    * def numero = numeroAleatorio()
    Given path 'cuentas'
    And request { numeroCuenta: '#(numero)', tipo: 'AHORROS', saldoInicial: 50, clienteId: '#(pruebas)' }
    When method post
    Then status 201

  Scenario: Un número de cuenta duplicado responde 409
    Given path 'cuentas'
    And request { numeroCuenta: '#(numero)', tipo: 'CORRIENTE', saldoInicial: 10, clienteId: '#(pruebas)' }
    When method post
    Then status 409
    And match response.title == 'Número de cuenta duplicado'

  Scenario: Un cliente inexistente responde 422 "Cliente no disponible"
    Given path 'cuentas'
    And request { numeroCuenta: '#(numeroAleatorio())', tipo: 'AHORROS', saldoInicial: 10, clienteId: '#(uuidAleatorio())' }
    When method post
    Then status 422
    And match response.title == 'Cliente no disponible'

  Scenario: Actualizar tipo y estado sin tocar los saldos
    Given path 'cuentas', numero
    And request { estado: false }
    When method patch
    Then status 200
    And match response contains { estado: false, tipo: 'AHORROS', saldoInicial: 50, saldoDisponible: 50 }

    Given path 'cuentas', numero
    And request { tipo: 'CORRIENTE', estado: true }
    When method put
    Then status 200
    And match response contains { estado: true, tipo: 'CORRIENTE' }

  Scenario: Las cuentas no se eliminan y una inexistente responde 404
    Given path 'cuentas', numero
    When method delete
    Then status 405

    Given path 'cuentas', '000000999999'
    When method get
    Then status 404

Feature: Movimientos - corrección y escenarios de error

  Usan cuentas propias de "Cliente Pruebas" para no alterar los saldos de los casos de uso.

  Background:
    * url karate.properties['karate.baseUrl']
    * def pruebas = karate.properties['karate.pruebasId']
    * def numero = '' + Math.floor(100000000000 + Math.random() * 899999999999)
    Given path 'cuentas'
    And request { numeroCuenta: '#(numero)', tipo: 'AHORROS', saldoInicial: 2000, clienteId: '#(pruebas)' }
    When method post
    Then status 201

  Scenario: Corregir un movimiento recalcula los saldos posteriores
    Given path 'movimientos'
    And request { numeroCuenta: '#(numero)', valor: -575 }
    When method post
    Then status 201
    * def retiroId = response.movimientoId

    Given path 'movimientos'
    And request { numeroCuenta: '#(numero)', valor: 100 }
    When method post
    Then status 201
    * def depositoId = response.movimientoId

    Given path 'movimientos', retiroId
    And request { valor: -500 }
    When method patch
    Then status 200
    And match response contains { valor: -500, saldo: 1500 }

    Given path 'movimientos', depositoId
    When method get
    Then status 200
    And match response.saldo == 1600

    Given path 'cuentas', numero
    When method get
    Then status 200
    And match response.saldoDisponible == 1600

  Scenario: Una corrección que deja un saldo negativo se rechaza sin cambios
    Given path 'movimientos'
    And request { numeroCuenta: '#(numero)', valor: -575 }
    When method post
    Then status 201
    * def primeroId = response.movimientoId

    Given path 'movimientos'
    And request { numeroCuenta: '#(numero)', valor: -1425 }
    When method post
    Then status 201

    Given path 'movimientos', primeroId
    And request { valor: -600 }
    When method put
    Then status 422
    And match response.detail == 'Saldo no disponible'

    Given path 'cuentas', numero
    When method get
    Then match response.saldoDisponible == 0

  Scenario: Una cuenta inactiva no acepta movimientos
    Given path 'cuentas', numero
    And request { estado: false }
    When method patch
    Then status 200

    Given path 'movimientos'
    And request { numeroCuenta: '#(numero)', valor: 10 }
    When method post
    Then status 422
    And match response.title == 'Cuenta inactiva'

  Scenario: La fecha no se puede indicar y el valor 0 no está permitido
    Given path 'movimientos'
    And request { numeroCuenta: '#(numero)', valor: 10, fecha: '2022-02-10' }
    When method post
    Then status 400

    Given path 'movimientos'
    And request { numeroCuenta: '#(numero)', valor: 0 }
    When method post
    Then status 400

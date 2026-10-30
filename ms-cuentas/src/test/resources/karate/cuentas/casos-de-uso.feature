Feature: Casos de uso 2 a 5 del enunciado

  Un único escenario ordenado: los escenarios de Karate no comparten estado y cada caso de uso
  depende del anterior (cuentas → movimientos → reporte).

  Background:
    * url karate.properties['karate.baseUrl']
    * def jose = karate.properties['karate.joseId']
    * def marianela = karate.properties['karate.marianelaId']
    * def juan = karate.properties['karate.juanId']

  Scenario: Casos de uso 2 a 5

    # ---------- Caso de uso 2: creación de cuentas de usuario ----------
    Given path 'cuentas'
    And request { numeroCuenta: '478758', tipo: 'AHORROS', saldoInicial: 2000, clienteId: '#(jose)' }
    When method post
    Then status 201
    And match response contains { saldoDisponible: 2000, estado: true, nombreCliente: 'Jose Lema' }

    Given path 'cuentas'
    And request { numeroCuenta: '225487', tipo: 'CORRIENTE', saldoInicial: 100, clienteId: '#(marianela)' }
    When method post
    Then status 201
    And match response contains { saldoDisponible: 100, estado: true, nombreCliente: 'Marianela Montalvo' }

    Given path 'cuentas'
    And request { numeroCuenta: '495878', tipo: 'AHORROS', saldoInicial: 0, clienteId: '#(juan)' }
    When method post
    Then status 201
    And match response contains { saldoDisponible: 0, estado: true, nombreCliente: 'Juan Osorio' }

    Given path 'cuentas'
    And request { numeroCuenta: '496825', tipo: 'AHORROS', saldoInicial: 540, clienteId: '#(marianela)' }
    When method post
    Then status 201
    And match response contains { saldoDisponible: 540, estado: true, nombreCliente: 'Marianela Montalvo' }

    # ---------- Caso de uso 3: nueva cuenta corriente para Jose Lema ----------
    Given path 'cuentas'
    And request { numeroCuenta: '585545', tipo: 'CORRIENTE', saldoInicial: 1000, clienteId: '#(jose)' }
    When method post
    Then status 201
    And match response contains { numeroCuenta: '585545', tipo: 'CORRIENTE', saldoDisponible: 1000, nombreCliente: 'Jose Lema' }

    Given path 'cuentas'
    And param clienteId = jose
    When method get
    Then status 200
    And match response.totalElementos == 2
    And match response.contenido[*].numeroCuenta contains only ['478758', '585545']

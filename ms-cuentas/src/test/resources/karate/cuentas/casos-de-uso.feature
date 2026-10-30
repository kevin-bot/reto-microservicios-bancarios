Feature: Casos de uso 2 a 5 del enunciado

  Un único escenario ordenado: los escenarios de Karate no comparten estado y cada caso de uso
  depende del anterior (cuentas → movimientos → reporte).

  Background:
    * url karate.properties['karate.baseUrl']
    * def jose = karate.properties['karate.joseId']
    * def marianela = karate.properties['karate.marianelaId']
    * def juan = karate.properties['karate.juanId']
    # "Hoy" en la zona horaria del banco (banco.zona-horaria)
    * def LocalDate = Java.type('java.time.LocalDate')
    * def ZoneId = Java.type('java.time.ZoneId')
    * def hoy = LocalDate.now(ZoneId.of('America/Guayaquil')).toString()

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

    # ---------- Caso de uso 4: movimientos ----------
    Given path 'movimientos'
    And request { numeroCuenta: '478758', valor: -575 }
    When method post
    Then status 201
    And match response contains { tipo: 'RETIRO', valor: -575, saldo: 1425 }

    Given path 'movimientos'
    And request { numeroCuenta: '225487', valor: 600 }
    When method post
    Then status 201
    And match response contains { tipo: 'DEPOSITO', valor: 600, saldo: 700 }

    Given path 'movimientos'
    And request { numeroCuenta: '495878', valor: 150 }
    When method post
    Then status 201
    And match response contains { tipo: 'DEPOSITO', valor: 150, saldo: 150 }

    Given path 'movimientos'
    And request { numeroCuenta: '496825', valor: -540 }
    When method post
    Then status 201
    And match response contains { tipo: 'RETIRO', valor: -540, saldo: 0 }

    # F3: sin saldo → "Saldo no disponible"
    Given path 'movimientos'
    And request { numeroCuenta: '496825', valor: -1 }
    When method post
    Then status 422
    And match response.title == 'Saldo no disponible'
    And match response.detail == 'Saldo no disponible'

    Given path 'cuentas', '496825'
    When method get
    Then status 200
    And match response.saldoDisponible == 0

    # ---------- Caso de uso 5 (listado plano): movimientos de Marianela por fechas ----------
    Given path 'movimientos'
    And params { cliente: '#(marianela)', fecha: '#(hoy + "," + hoy)' }
    When method get
    Then status 200
    And match response.totalElementos == 2
    And match each response.contenido contains { nombreCliente: 'Marianela Montalvo', estadoCuenta: true }
    And match response.contenido contains deep { numeroCuenta: '225487', tipoCuenta: 'CORRIENTE', saldoInicial: 100, valor: 600, saldo: 700 }
    And match response.contenido contains deep { numeroCuenta: '496825', tipoCuenta: 'AHORROS', saldoInicial: 540, valor: -540, saldo: 0 }

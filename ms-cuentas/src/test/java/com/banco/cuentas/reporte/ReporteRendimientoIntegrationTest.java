package com.banco.cuentas.reporte;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.movimiento.repository.MovimientoRepository;
import com.banco.cuentas.reporte.dto.ReporteResponse;
import com.banco.cuentas.reporte.service.ReporteService;
import com.banco.cuentas.shared.fecha.RangoFechas;
import com.banco.cuentas.support.ClientesDePrueba;
import com.banco.cuentas.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SC-006: el reporte de un cliente con 1.000 movimientos en el rango se entrega en menos de 2 s.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@DisplayName("Rendimiento del reporte de estado de cuenta")
class ReporteRendimientoIntegrationTest {

    private static final int MOVIMIENTOS = 1_000;
    private static final Duration LIMITE = Duration.ofSeconds(2);

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private ClienteRefRepository clienteRefRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("1.000 movimientos en el rango se reportan en menos de 2 s")
    void generar_cuandoMilMovimientos_debeResponderEnMenosDeDosSegundos() {
        // Given: un cliente propio con una cuenta y 1.000 depósitos dentro del rango
        UUID clienteId = UUID.randomUUID();
        String numeroCuenta = ClientesDePrueba.numeroCuentaAleatorio();
        Instant inicio = Instant.parse("2025-03-01T15:00:00Z");
        transactionTemplate.executeWithoutResult(estado -> {
            ClienteRef cliente = clienteRefRepository.save(
                    ClienteRef.crear(clienteId, "Cliente Rendimiento", true, inicio.minusSeconds(86_400)));
            Cuenta cuenta = cuentaRepository.save(
                    Cuenta.abrir(numeroCuenta, TipoCuenta.AHORROS, BigDecimal.ZERO, true, cliente));
            List<Movimiento> movimientos = new ArrayList<>(MOVIMIENTOS);
            for (int i = 0; i < MOVIMIENTOS; i++) {
                movimientos.add(cuenta.registrarMovimiento(BigDecimal.ONE, inicio.plusSeconds(i * 60L)));
            }
            movimientoRepository.saveAll(movimientos);
        });
        RangoFechas rango = RangoFechas.parse("2025-03-01,2025-03-31");
        reporteService.generar(clienteId, rango);

        // When
        long antes = System.nanoTime();
        ReporteResponse reporte = reporteService.generar(clienteId, rango);
        Duration duracion = Duration.ofNanos(System.nanoTime() - antes);

        // Then
        assertThat(reporte.cuentas()).singleElement()
                .satisfies(cuenta -> assertThat(cuenta.movimientos()).hasSize(MOVIMIENTOS));
        assertThat(duracion).isLessThan(LIMITE);
    }
}

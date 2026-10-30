package com.banco.cuentas.movimiento;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.exception.SaldoNoDisponibleException;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.movimiento.dto.MovimientoCreateRequest;
import com.banco.cuentas.movimiento.repository.MovimientoRepository;
import com.banco.cuentas.movimiento.repository.MovimientoSpecifications;
import com.banco.cuentas.movimiento.service.MovimientoService;
import com.banco.cuentas.support.ClientesDePrueba;
import com.banco.cuentas.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SC-002: con bloqueo pesimista, retiros simultáneos sobre la misma cuenta nunca dejan el saldo
 * negativo ni pierden actualizaciones.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@DisplayName("Concurrencia de movimientos sobre una misma cuenta")
class MovimientoConcurrenciaIntegrationTest {

    private static final int HILOS = 20;

    @Autowired
    private MovimientoService movimientoService;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private ClienteRefRepository clienteRefRepository;

    @Test
    @DisplayName("20 retiros simultáneos de 10 sobre saldo 100: 10 se aplican, 10 se rechazan y el saldo queda en 0")
    void registrar_cuandoRetirosConcurrentesExcedenSaldo_debeAplicarSoloLosQueAlcanzan() throws Exception {
        // Given
        ClientesDePrueba.sembrar(clienteRefRepository);
        ClienteRef cliente = clienteRefRepository.findById(ClientesDePrueba.CLIENTE_PRUEBAS).orElseThrow();
        String numeroCuenta = ClientesDePrueba.numeroCuentaAleatorio();
        cuentaRepository.save(Cuenta.abrir(numeroCuenta, TipoCuenta.AHORROS, new BigDecimal("100"), true, cliente));

        AtomicInteger aplicados = new AtomicInteger();
        AtomicInteger rechazados = new AtomicInteger();
        Queue<Throwable> inesperados = new ConcurrentLinkedQueue<>();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(HILOS);

        // When: todos los hilos arrancan a la vez
        for (int i = 0; i < HILOS; i++) {
            ejecutor.submit(() -> {
                try {
                    largada.await();
                    movimientoService.registrar(new MovimientoCreateRequest(numeroCuenta, new BigDecimal("-10")));
                    aplicados.incrementAndGet();
                } catch (SaldoNoDisponibleException ex) {
                    rechazados.incrementAndGet();
                } catch (Throwable ex) {
                    inesperados.add(ex);
                }
            });
        }
        largada.countDown();
        ejecutor.shutdown();
        assertThat(ejecutor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // Then
        assertThat(inesperados).isEmpty();
        assertThat(aplicados.get()).isEqualTo(10);
        assertThat(rechazados.get()).isEqualTo(10);
        assertThat(cuentaRepository.findByNumeroCuenta(numeroCuenta).orElseThrow().getSaldoDisponible())
                .isEqualByComparingTo("0");

        List<Movimiento> movimientos = movimientoRepository.findAll(MovimientoSpecifications.deCuenta(numeroCuenta));
        assertThat(movimientos).hasSize(10);
        assertThat(movimientos).allSatisfy(movimiento -> assertThat(movimiento.getSaldo().signum()).isNotNegative());
    }
}

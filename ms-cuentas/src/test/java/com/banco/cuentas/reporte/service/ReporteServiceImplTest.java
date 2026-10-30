package com.banco.cuentas.reporte.service;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.entity.TipoMovimiento;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.movimiento.repository.MovimientoRepository;
import com.banco.cuentas.reporte.dto.CuentaReporte;
import com.banco.cuentas.reporte.dto.ReporteResponse;
import com.banco.cuentas.reporte.exception.ClienteNoEncontradoException;
import com.banco.cuentas.reporte.mapper.ReporteMapper;
import com.banco.cuentas.shared.config.BancoProperties;
import com.banco.cuentas.shared.fecha.RangoFechas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de reporte de estado de cuenta")
class ReporteServiceImplTest {

    private static final UUID MARIANELA = UUID.randomUUID();
    private static final RangoFechas RANGO = RangoFechas.parse("2022-02-01,2022-02-10");
    private static final Instant DIA_8 = Instant.parse("2022-02-08T14:00:00Z");
    private static final Instant DIA_10 = Instant.parse("2022-02-10T15:00:00Z");

    @Mock
    private ClienteRefRepository clienteRefRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private MovimientoRepository movimientoRepository;

    private final BancoProperties bancoProperties = new BancoProperties("America/Guayaquil");

    private ReporteServiceImpl reporteService;

    @BeforeEach
    void crearServicio() {
        reporteService = new ReporteServiceImpl(clienteRefRepository, cuentaRepository, movimientoRepository,
                Mappers.getMapper(ReporteMapper.class), bancoProperties);
    }

    @Test
    @DisplayName("Arma el reporte anidado del caso de uso 5: cuentas con saldos y sus movimientos del rango")
    void generar_cuandoClienteConMovimientos_debeAgruparMovimientosPorCuenta() {
        // Given
        ClienteRef marianela = ClienteRef.crear(MARIANELA, "Marianela Montalvo", true, DIA_8.minusSeconds(86400));
        Cuenta corriente = Cuenta.abrir("225487", TipoCuenta.CORRIENTE, new BigDecimal("100"), true, marianela);
        Cuenta ahorros = Cuenta.abrir("496825", TipoCuenta.AHORROS, new BigDecimal("540"), true, marianela);
        Movimiento retiro = ahorros.registrarMovimiento(new BigDecimal("-540"), DIA_8);
        Movimiento deposito = corriente.registrarMovimiento(new BigDecimal("600"), DIA_10);
        when(clienteRefRepository.findById(MARIANELA)).thenReturn(Optional.of(marianela));
        when(cuentaRepository.findByClienteClienteId(MARIANELA)).thenReturn(List.of(ahorros, corriente));
        when(movimientoRepository.findDelReporte(anyList(),
                eq(RANGO.desde(bancoProperties.zona())), eq(RANGO.hasta(bancoProperties.zona()))))
                .thenReturn(List.of(retiro, deposito));

        // When
        ReporteResponse reporte = reporteService.generar(MARIANELA, RANGO);

        // Then
        assertThat(reporte.cliente().nombre()).isEqualTo("Marianela Montalvo");
        assertThat(reporte.fechaInicio()).isEqualTo(LocalDate.of(2022, 2, 1));
        assertThat(reporte.fechaFin()).isEqualTo(LocalDate.of(2022, 2, 10));
        assertThat(reporte.cuentas()).extracting(CuentaReporte::numeroCuenta).containsExactly("225487", "496825");

        CuentaReporte cuenta225487 = reporte.cuentas().get(0);
        assertThat(cuenta225487.saldoInicial()).isEqualByComparingTo("100");
        assertThat(cuenta225487.saldoDisponible()).isEqualByComparingTo("700");
        assertThat(cuenta225487.movimientos()).singleElement().satisfies(movimiento -> {
            assertThat(movimiento.tipo()).isEqualTo(TipoMovimiento.DEPOSITO);
            assertThat(movimiento.valor()).isEqualByComparingTo("600");
            assertThat(movimiento.saldo()).isEqualByComparingTo("700");
            assertThat(movimiento.fecha()).isEqualTo(DIA_10);
        });

        CuentaReporte cuenta496825 = reporte.cuentas().get(1);
        assertThat(cuenta496825.saldoDisponible()).isEqualByComparingTo("0");
        assertThat(cuenta496825.movimientos()).singleElement()
                .satisfies(movimiento -> assertThat(movimiento.valor()).isEqualByComparingTo("-540"));
    }

    @Test
    @DisplayName("Una cuenta sin movimientos en el rango aparece con la lista vacía")
    void generar_cuandoCuentaSinMovimientos_debeIncluirlaConListaVacia() {
        // Given
        ClienteRef cliente = ClienteRef.crear(MARIANELA, "Marianela Montalvo", true, DIA_8);
        Cuenta cuenta = Cuenta.abrir("225487", TipoCuenta.CORRIENTE, new BigDecimal("100"), true, cliente);
        when(clienteRefRepository.findById(MARIANELA)).thenReturn(Optional.of(cliente));
        when(cuentaRepository.findByClienteClienteId(MARIANELA)).thenReturn(List.of(cuenta));
        when(movimientoRepository.findDelReporte(anyList(), any(), any())).thenReturn(List.of());

        // When
        ReporteResponse reporte = reporteService.generar(MARIANELA, RANGO);

        // Then
        assertThat(reporte.cuentas()).singleElement().satisfies(cuentaReporte -> {
            assertThat(cuentaReporte.saldoDisponible()).isEqualByComparingTo("100");
            assertThat(cuentaReporte.movimientos()).isEmpty();
        });
    }

    @Test
    @DisplayName("Un cliente sin cuentas no consulta movimientos")
    void generar_cuandoClienteSinCuentas_noDebeConsultarMovimientos() {
        // Given
        when(clienteRefRepository.findById(MARIANELA))
                .thenReturn(Optional.of(ClienteRef.crear(MARIANELA, "Marianela Montalvo", true, DIA_8)));
        when(cuentaRepository.findByClienteClienteId(MARIANELA)).thenReturn(List.of());

        // When
        ReporteResponse reporte = reporteService.generar(MARIANELA, RANGO);

        // Then
        assertThat(reporte.cuentas()).isEmpty();
        verify(movimientoRepository, never()).findDelReporte(anyList(), any(), any());
    }

    @Test
    @DisplayName("Un cliente desconocido lanza ClienteNoEncontradoException")
    void generar_cuandoClienteDesconocido_debeLanzarClienteNoEncontrado() {
        // Given
        when(clienteRefRepository.findById(MARIANELA)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> reporteService.generar(MARIANELA, RANGO))
                .isInstanceOf(ClienteNoEncontradoException.class)
                .hasMessageContaining(MARIANELA.toString());
    }
}

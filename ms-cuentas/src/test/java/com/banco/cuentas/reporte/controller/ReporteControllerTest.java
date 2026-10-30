package com.banco.cuentas.reporte.controller;

import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.entity.TipoMovimiento;
import com.banco.cuentas.reporte.dto.ClienteResumen;
import com.banco.cuentas.reporte.dto.CuentaReporte;
import com.banco.cuentas.reporte.dto.MovimientoReporte;
import com.banco.cuentas.reporte.dto.ReporteResponse;
import com.banco.cuentas.reporte.exception.ClienteNoEncontradoException;
import com.banco.cuentas.reporte.service.ReporteService;
import com.banco.cuentas.shared.fecha.RangoFechas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReporteController.class)
@DisplayName("API REST de reportes")
class ReporteControllerTest {

    private static final UUID MARIANELA = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String RANGO = "2022-02-01,2022-02-10";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReporteService reporteService;

    private static ReporteResponse reporteMarianela() {
        MovimientoReporte deposito = new MovimientoReporte(UUID.randomUUID(), Instant.parse("2022-02-10T15:00:00Z"),
                TipoMovimiento.DEPOSITO, new BigDecimal("600.00"), new BigDecimal("700.00"));
        CuentaReporte corriente = new CuentaReporte("225487", TipoCuenta.CORRIENTE, new BigDecimal("100.00"),
                new BigDecimal("700.00"), true, List.of(deposito));
        return new ReporteResponse(new ClienteResumen(MARIANELA, "Marianela Montalvo"),
                LocalDate.of(2022, 2, 1), LocalDate.of(2022, 2, 10), List.of(corriente));
    }

    @Test
    @DisplayName("200 con la estructura anidada cliente → cuentas → movimientos")
    void generar_cuandoParametrosValidos_debeResponderReporteAnidado() throws Exception {
        // Given
        when(reporteService.generar(MARIANELA, RangoFechas.parse(RANGO))).thenReturn(reporteMarianela());

        // When / Then
        mockMvc.perform(get("/reportes").param("fecha", RANGO).param("cliente", MARIANELA.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.cliente.nombre").value("Marianela Montalvo"))
                .andExpect(jsonPath("$.fechaInicio").value("2022-02-01"))
                .andExpect(jsonPath("$.fechaFin").value("2022-02-10"))
                .andExpect(jsonPath("$.cuentas[0].numeroCuenta").value("225487"))
                .andExpect(jsonPath("$.cuentas[0].saldoInicial").value(100.00))
                .andExpect(jsonPath("$.cuentas[0].saldoDisponible").value(700.00))
                .andExpect(jsonPath("$.cuentas[0].movimientos[0].tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$.cuentas[0].movimientos[0].valor").value(600.00))
                .andExpect(jsonPath("$.cuentas[0].movimientos[0].saldo").value(700.00));
    }

    @Test
    @DisplayName("400 cuando falta el parámetro fecha")
    void generar_cuandoFaltaFecha_debeResponder400() throws Exception {
        mockMvc.perform(get("/reportes").param("cliente", MARIANELA.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
        verify(reporteService, never()).generar(any(), any());
    }

    @Test
    @DisplayName("400 cuando falta el parámetro cliente")
    void generar_cuandoFaltaCliente_debeResponder400() throws Exception {
        mockMvc.perform(get("/reportes").param("fecha", RANGO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    @Test
    @DisplayName("400 cuando el rango está invertido")
    void generar_cuandoRangoInvertido_debeResponder400() throws Exception {
        mockMvc.perform(get("/reportes").param("fecha", "2022-02-10,2022-02-01")
                        .param("cliente", MARIANELA.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    @Test
    @DisplayName("400 cuando la fecha no trae el rango completo")
    void generar_cuandoUnaSolaFecha_debeResponder400() throws Exception {
        mockMvc.perform(get("/reportes").param("fecha", "2022-02-01").param("cliente", MARIANELA.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("404 cuando el cliente no existe")
    void generar_cuandoClienteDesconocido_debeResponder404() throws Exception {
        // Given
        when(reporteService.generar(MARIANELA, RangoFechas.parse(RANGO)))
                .thenThrow(new ClienteNoEncontradoException(MARIANELA));

        // When / Then
        mockMvc.perform(get("/reportes").param("fecha", RANGO).param("cliente", MARIANELA.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Cliente no encontrado"));
    }
}

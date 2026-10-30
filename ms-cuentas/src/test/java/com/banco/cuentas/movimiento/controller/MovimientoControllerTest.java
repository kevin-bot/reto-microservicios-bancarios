package com.banco.cuentas.movimiento.controller;

import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.entity.TipoMovimiento;
import com.banco.cuentas.cuenta.exception.CuentaInactivaException;
import com.banco.cuentas.cuenta.exception.CuentaNoEncontradaException;
import com.banco.cuentas.cuenta.exception.SaldoNoDisponibleException;
import com.banco.cuentas.movimiento.dto.MovimientoCreateRequest;
import com.banco.cuentas.movimiento.dto.MovimientoPatchRequest;
import com.banco.cuentas.movimiento.dto.MovimientoResponse;
import com.banco.cuentas.movimiento.dto.MovimientoUpdateRequest;
import com.banco.cuentas.movimiento.exception.MovimientoNoEncontradoException;
import com.banco.cuentas.movimiento.service.MovimientoService;
import com.banco.cuentas.shared.dto.PaginaResponse;
import com.banco.cuentas.shared.fecha.RangoFechas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovimientoController.class)
@DisplayName("API REST de movimientos")
class MovimientoControllerTest {

    private static final UUID MOVIMIENTO_ID = UUID.fromString("b7e2c1d0-5f3a-4e8b-9c21-0a6d4f8e3b17");
    private static final UUID MARIANELA = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String RETIRO = "{\"numeroCuenta\": \"496825\", \"valor\": -540}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovimientoService movimientoService;

    private static MovimientoResponse filaRetiro() {
        return new MovimientoResponse(MOVIMIENTO_ID, Instant.parse("2026-10-29T15:00:00Z"), MARIANELA,
                "Marianela Montalvo", "496825", TipoCuenta.AHORROS, new BigDecimal("540.00"), true,
                TipoMovimiento.RETIRO, new BigDecimal("-540.00"), new BigDecimal("0.00"));
    }

    @Nested
    @DisplayName("POST /movimientos")
    class Registrar {

        @Test
        @DisplayName("201 con Location y la fila del movimiento")
        void registrar_cuandoValido_debeResponder201() throws Exception {
            // Given
            when(movimientoService.registrar(any(MovimientoCreateRequest.class))).thenReturn(filaRetiro());

            // When / Then
            mockMvc.perform(post("/movimientos").contentType(MediaType.APPLICATION_JSON).content(RETIRO))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/movimientos/" + MOVIMIENTO_ID)))
                    .andExpect(jsonPath("$.tipo").value("RETIRO"))
                    .andExpect(jsonPath("$.saldo").value(0.00));
        }

        @Test
        @DisplayName("422 con title y detail exactos \"Saldo no disponible\" (F3)")
        void registrar_cuandoSinSaldo_debeResponder422SaldoNoDisponible() throws Exception {
            // Given
            when(movimientoService.registrar(any(MovimientoCreateRequest.class)))
                    .thenThrow(new SaldoNoDisponibleException());

            // When / Then
            mockMvc.perform(post("/movimientos").contentType(MediaType.APPLICATION_JSON).content(RETIRO))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Saldo no disponible"))
                    .andExpect(jsonPath("$.detail").value("Saldo no disponible"));
        }

        @Test
        @DisplayName("422 \"Cuenta inactiva\"")
        void registrar_cuandoCuentaInactiva_debeResponder422() throws Exception {
            // Given
            when(movimientoService.registrar(any(MovimientoCreateRequest.class)))
                    .thenThrow(new CuentaInactivaException("496825"));

            // When / Then
            mockMvc.perform(post("/movimientos").contentType(MediaType.APPLICATION_JSON).content(RETIRO))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Cuenta inactiva"));
        }

        @Test
        @DisplayName("400 cuando falta el valor")
        void registrar_cuandoFaltaValor_debeResponder400() throws Exception {
            mockMvc.perform(post("/movimientos").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"numeroCuenta\": \"496825\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.valor").exists());
            verify(movimientoService, never()).registrar(any());
        }

        @Test
        @DisplayName("400 cuando se intenta indicar la fecha (la asigna el sistema)")
        void registrar_cuandoIncluyeFecha_debeResponder400() throws Exception {
            mockMvc.perform(post("/movimientos").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"numeroCuenta\": \"496825\", \"valor\": -10, \"fecha\": \"2022-02-10\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Solicitud mal formada"));
        }

        @Test
        @DisplayName("404 cuando la cuenta no existe")
        void registrar_cuandoCuentaNoExiste_debeResponder404() throws Exception {
            // Given
            when(movimientoService.registrar(any(MovimientoCreateRequest.class)))
                    .thenThrow(new CuentaNoEncontradaException("496825"));

            // When / Then
            mockMvc.perform(post("/movimientos").contentType(MediaType.APPLICATION_JSON).content(RETIRO))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /movimientos")
    class Consultar {

        @Test
        @DisplayName("200 al consultar por identificador; 404 si no existe")
        void obtener_cuandoExisteONo_debeResponder200O404() throws Exception {
            // Given
            when(movimientoService.obtener(MOVIMIENTO_ID)).thenReturn(filaRetiro());
            UUID inexistente = UUID.randomUUID();
            when(movimientoService.obtener(inexistente)).thenThrow(new MovimientoNoEncontradoException(inexistente));

            // When / Then
            mockMvc.perform(get("/movimientos/{id}", MOVIMIENTO_ID)).andExpect(status().isOk());
            mockMvc.perform(get("/movimientos/{id}", inexistente))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Movimiento no encontrado"));
        }

        @Test
        @DisplayName("200 con filas planas al filtrar por cliente y fechas (caso de uso 5)")
        void listar_cuandoClienteYFecha_debeResponderFilasPlanas() throws Exception {
            // Given
            when(movimientoService.listar(eq(MARIANELA), eq(null),
                    eq(RangoFechas.parse("2026-10-29,2026-10-29")), any(Pageable.class)))
                    .thenReturn(new PaginaResponse<>(List.of(filaRetiro()), 0, 20, 1, 1));

            // When / Then
            mockMvc.perform(get("/movimientos")
                            .param("cliente", MARIANELA.toString())
                            .param("fecha", "2026-10-29,2026-10-29"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].nombreCliente").value("Marianela Montalvo"))
                    .andExpect(jsonPath("$.contenido[0].numeroCuenta").value("496825"))
                    .andExpect(jsonPath("$.contenido[0].tipoCuenta").value("AHORROS"))
                    .andExpect(jsonPath("$.contenido[0].saldoInicial").value(540.00))
                    .andExpect(jsonPath("$.contenido[0].estadoCuenta").value(true))
                    .andExpect(jsonPath("$.contenido[0].valor").value(-540.00))
                    .andExpect(jsonPath("$.contenido[0].saldo").value(0.00));
        }

        @Test
        @DisplayName("400 cuando el rango de fechas está invertido")
        void listar_cuandoRangoInvertido_debeResponder400() throws Exception {
            mockMvc.perform(get("/movimientos").param("fecha", "2022-02-10,2022-02-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Parámetro inválido"));
        }
    }

    @Nested
    @DisplayName("PUT y PATCH /movimientos/{id}")
    class Corregir {

        @Test
        @DisplayName("PUT 200 al corregir el valor")
        void corregir_cuandoValido_debeResponder200() throws Exception {
            // Given
            when(movimientoService.corregir(eq(MOVIMIENTO_ID), any(MovimientoUpdateRequest.class)))
                    .thenReturn(filaRetiro());

            // When / Then
            mockMvc.perform(put("/movimientos/{id}", MOVIMIENTO_ID).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"valor\": -500}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH 400 cuando no trae campos")
        void corregirParcial_cuandoCuerpoVacio_debeResponder400() throws Exception {
            mockMvc.perform(patch("/movimientos/{id}", MOVIMIENTO_ID).contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
            verify(movimientoService, never()).corregirParcial(any(), any());
        }

        @Test
        @DisplayName("PATCH 422 cuando la corrección deja un saldo negativo")
        void corregirParcial_cuandoSaldoNegativo_debeResponder422() throws Exception {
            // Given
            when(movimientoService.corregirParcial(eq(MOVIMIENTO_ID), any(MovimientoPatchRequest.class)))
                    .thenThrow(new SaldoNoDisponibleException());

            // When / Then
            mockMvc.perform(patch("/movimientos/{id}", MOVIMIENTO_ID).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"valor\": -5000}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("Saldo no disponible"));
        }
    }
}

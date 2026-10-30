package com.banco.cuentas.cuenta.controller;

import com.banco.cuentas.cuenta.dto.CuentaCreateRequest;
import com.banco.cuentas.cuenta.dto.CuentaPatchRequest;
import com.banco.cuentas.cuenta.dto.CuentaResponse;
import com.banco.cuentas.cuenta.dto.CuentaUpdateRequest;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.exception.ClienteNoDisponibleException;
import com.banco.cuentas.cuenta.exception.CuentaNoEncontradaException;
import com.banco.cuentas.cuenta.exception.NumeroCuentaDuplicadoException;
import com.banco.cuentas.cuenta.service.CuentaService;
import com.banco.cuentas.shared.dto.PaginaResponse;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuentaController.class)
@DisplayName("API REST de cuentas")
class CuentaControllerTest {

    private static final UUID JOSE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String CUENTA_VALIDA = """
            {
              "numeroCuenta": "478758",
              "tipo": "AHORROS",
              "saldoInicial": 2000,
              "clienteId": "00000000-0000-0000-0000-000000000001"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CuentaService cuentaService;

    private static CuentaResponse respuesta478758() {
        return new CuentaResponse("478758", TipoCuenta.AHORROS, new BigDecimal("2000.00"),
                new BigDecimal("2000.00"), true, JOSE, "Jose Lema");
    }

    @Nested
    @DisplayName("POST /cuentas")
    class Crear {

        @Test
        @DisplayName("201 con Location y saldo disponible igual al inicial")
        void crear_cuandoDatosValidos_debeResponder201() throws Exception {
            // Given
            when(cuentaService.crear(any(CuentaCreateRequest.class))).thenReturn(respuesta478758());

            // When / Then
            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(CUENTA_VALIDA))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/cuentas/478758")))
                    .andExpect(jsonPath("$.saldoDisponible").value(2000.00))
                    .andExpect(jsonPath("$.nombreCliente").value("Jose Lema"));
        }

        @Test
        @DisplayName("400 cuando el número de cuenta no es numérico")
        void crear_cuandoNumeroInvalido_debeResponder400() throws Exception {
            String cuerpo = CUENTA_VALIDA.replace("478758", "12ab");

            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.numeroCuenta").exists());
            verify(cuentaService, never()).crear(any());
        }

        @Test
        @DisplayName("400 cuando el saldo inicial es negativo")
        void crear_cuandoSaldoNegativo_debeResponder400() throws Exception {
            String cuerpo = CUENTA_VALIDA.replace("2000", "-1");

            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.saldoInicial").exists());
        }

        @Test
        @DisplayName("400 cuando el saldo inicial tiene más de 2 decimales")
        void crear_cuandoSaldoConTresDecimales_debeResponder400() throws Exception {
            String cuerpo = CUENTA_VALIDA.replace("2000", "10.123");

            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.saldoInicial").exists());
        }

        @Test
        @DisplayName("400 cuando falta el cliente")
        void crear_cuandoFaltaCliente_debeResponder400() throws Exception {
            String cuerpo = """
                    {"numeroCuenta": "478758", "tipo": "AHORROS", "saldoInicial": 2000}
                    """;

            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.clienteId").exists());
        }

        @Test
        @DisplayName("400 cuando se intenta fijar el saldo disponible")
        void crear_cuandoIncluyeSaldoDisponible_debeResponder400() throws Exception {
            String cuerpo = CUENTA_VALIDA.replace("\"saldoInicial\": 2000,", "\"saldoInicial\": 2000, \"saldoDisponible\": 999999,");

            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Solicitud mal formada"));
        }

        @Test
        @DisplayName("409 cuando el número de cuenta ya existe")
        void crear_cuandoNumeroDuplicado_debeResponder409() throws Exception {
            // Given
            when(cuentaService.crear(any(CuentaCreateRequest.class)))
                    .thenThrow(new NumeroCuentaDuplicadoException("478758"));

            // When / Then
            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(CUENTA_VALIDA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Número de cuenta duplicado"));
        }

        @Test
        @DisplayName("422 cuando el cliente no está disponible")
        void crear_cuandoClienteNoDisponible_debeResponder422() throws Exception {
            // Given
            when(cuentaService.crear(any(CuentaCreateRequest.class))).thenThrow(new ClienteNoDisponibleException());

            // When / Then
            mockMvc.perform(post("/cuentas").contentType(MediaType.APPLICATION_JSON).content(CUENTA_VALIDA))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Cliente no disponible"));
        }
    }

    @Nested
    @DisplayName("GET /cuentas")
    class Consultar {

        @Test
        @DisplayName("200 al consultar por número")
        void obtener_cuandoExiste_debeResponder200() throws Exception {
            // Given
            when(cuentaService.obtener("478758")).thenReturn(respuesta478758());

            // When / Then
            mockMvc.perform(get("/cuentas/{numeroCuenta}", "478758"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.numeroCuenta").value("478758"));
        }

        @Test
        @DisplayName("404 cuando la cuenta no existe")
        void obtener_cuandoNoExiste_debeResponder404() throws Exception {
            // Given
            when(cuentaService.obtener("999999")).thenThrow(new CuentaNoEncontradaException("999999"));

            // When / Then
            mockMvc.perform(get("/cuentas/{numeroCuenta}", "999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Cuenta no encontrada"));
        }

        @Test
        @DisplayName("200 al listar las cuentas de un cliente")
        void listar_cuandoFiltroCliente_debeResponder200() throws Exception {
            // Given
            when(cuentaService.listar(eq(JOSE), any(Pageable.class)))
                    .thenReturn(new PaginaResponse<>(List.of(respuesta478758()), 0, 20, 1, 1));

            // When / Then
            mockMvc.perform(get("/cuentas").param("clienteId", JOSE.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].numeroCuenta").value("478758"))
                    .andExpect(jsonPath("$.totalElementos").value(1));
        }
    }

    @Nested
    @DisplayName("PUT, PATCH y DELETE /cuentas/{numeroCuenta}")
    class Actualizar {

        @Test
        @DisplayName("PUT 200 con tipo y estado")
        void actualizar_cuandoDatosValidos_debeResponder200() throws Exception {
            // Given
            when(cuentaService.actualizar(eq("478758"), any(CuentaUpdateRequest.class))).thenReturn(respuesta478758());

            // When / Then
            mockMvc.perform(put("/cuentas/{numeroCuenta}", "478758").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tipo\": \"AHORROS\", \"estado\": true}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT 400 cuando falta el estado")
        void actualizar_cuandoFaltaEstado_debeResponder400() throws Exception {
            mockMvc.perform(put("/cuentas/{numeroCuenta}", "478758").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tipo\": \"AHORROS\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.estado").exists());
        }

        @Test
        @DisplayName("PATCH 400 cuando no trae campos")
        void actualizarParcial_cuandoCuerpoVacio_debeResponder400() throws Exception {
            mockMvc.perform(patch("/cuentas/{numeroCuenta}", "478758").contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
            verify(cuentaService, never()).actualizarParcial(any(), any());
        }

        @Test
        @DisplayName("PATCH 200 al desactivar")
        void actualizarParcial_cuandoEstadoFalse_debeResponder200() throws Exception {
            // Given
            when(cuentaService.actualizarParcial(eq("478758"), any(CuentaPatchRequest.class)))
                    .thenReturn(respuesta478758());

            // When / Then
            mockMvc.perform(patch("/cuentas/{numeroCuenta}", "478758").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\": false}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE 405: las cuentas no se eliminan (FR-012)")
        void eliminar_cuandoSeIntenta_debeResponder405() throws Exception {
            mockMvc.perform(delete("/cuentas/{numeroCuenta}", "478758"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}

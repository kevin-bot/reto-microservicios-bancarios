package com.banco.clientes.cliente.controller;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.entity.Genero;
import com.banco.clientes.cliente.exception.IdentificacionDuplicadaException;
import com.banco.clientes.cliente.service.ClienteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@DisplayName("API REST de clientes")
class ClienteControllerTest {

    private static final UUID CLIENTE_ID = UUID.fromString("3f1c2a9e-8b0d-4c55-9a6e-2b7f6c0d1e42");

    private static final String CLIENTE_VALIDO = """
            {
              "nombre": "Jose Lema",
              "genero": "MASCULINO",
              "edad": 35,
              "identificacion": "1712345678",
              "direccion": "Otavalo sn y principal",
              "telefono": "098254785",
              "contrasena": "1234",
              "estado": true
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService clienteService;

    private static ClienteResponse respuestaJoseLema() {
        return new ClienteResponse(CLIENTE_ID, "Jose Lema", Genero.MASCULINO, 35, "1712345678",
                "Otavalo sn y principal", "098254785", true);
    }

    @Nested
    @DisplayName("POST /clientes")
    class Crear {

        @Test
        @DisplayName("201 con Location y sin la contraseña en el cuerpo")
        void crear_cuandoDatosValidos_debeResponder201SinContrasena() throws Exception {
            // Given
            when(clienteService.crear(any(ClienteCreateRequest.class))).thenReturn(respuestaJoseLema());

            // When / Then
            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(CLIENTE_VALIDO))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/clientes/" + CLIENTE_ID)))
                    .andExpect(jsonPath("$.clienteId").value(CLIENTE_ID.toString()))
                    .andExpect(jsonPath("$.nombre").value("Jose Lema"))
                    .andExpect(jsonPath("$.contrasena").doesNotExist());
        }

        @Test
        @DisplayName("400 con el detalle del campo cuando la edad es menor a 18")
        void crear_cuandoEdadMenorA18_debeResponder400ConErrorDeEdad() throws Exception {
            String cuerpo = CLIENTE_VALIDO.replace("\"edad\": 35", "\"edad\": 17");

            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Datos inválidos"))
                    .andExpect(jsonPath("$.errores.edad").exists());
            verify(clienteService, never()).crear(any());
        }

        @Test
        @DisplayName("400 cuando la identificación no es alfanumérica de 5 a 20 caracteres")
        void crear_cuandoIdentificacionInvalida_debeResponder400() throws Exception {
            String cuerpo = CLIENTE_VALIDO.replace("1712345678", "ab-1");

            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.identificacion").exists());
        }

        @Test
        @DisplayName("400 con un error por cada campo obligatorio faltante")
        void crear_cuandoFaltanCamposObligatorios_debeResponder400ConErrores() throws Exception {
            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.nombre").exists())
                    .andExpect(jsonPath("$.errores.genero").exists())
                    .andExpect(jsonPath("$.errores.edad").exists())
                    .andExpect(jsonPath("$.errores.identificacion").exists())
                    .andExpect(jsonPath("$.errores.direccion").exists())
                    .andExpect(jsonPath("$.errores.telefono").exists())
                    .andExpect(jsonPath("$.errores.contrasena").exists());
        }

        @Test
        @DisplayName("400 'Solicitud mal formada' cuando el JSON no es legible")
        void crear_cuandoJsonMalFormado_debeResponder400() throws Exception {
            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Solicitud mal formada"));
        }

        @Test
        @DisplayName("400 cuando se envía un campo desconocido")
        void crear_cuandoCampoDesconocido_debeResponder400() throws Exception {
            String cuerpo = CLIENTE_VALIDO.replace("\"estado\": true", "\"estado\": true, \"saldo\": 100");

            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Solicitud mal formada"));
        }

        @Test
        @DisplayName("409 cuando la identificación ya está registrada")
        void crear_cuandoIdentificacionDuplicada_debeResponder409() throws Exception {
            // Given
            when(clienteService.crear(any(ClienteCreateRequest.class)))
                    .thenThrow(new IdentificacionDuplicadaException("1712345678"));

            // When / Then
            mockMvc.perform(post("/clientes").contentType(MediaType.APPLICATION_JSON).content(CLIENTE_VALIDO))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Identificación duplicada"));
        }
    }
}

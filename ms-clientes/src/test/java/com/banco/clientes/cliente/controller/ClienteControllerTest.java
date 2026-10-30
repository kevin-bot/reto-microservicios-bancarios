package com.banco.clientes.cliente.controller;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClientePatchRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.dto.ClienteUpdateRequest;
import com.banco.clientes.cliente.entity.Cliente;
import com.banco.clientes.cliente.entity.Genero;
import com.banco.clientes.cliente.exception.ClienteNoEncontradoException;
import com.banco.clientes.cliente.exception.IdentificacionDuplicadaException;
import com.banco.clientes.cliente.service.ClienteService;
import com.banco.clientes.shared.dto.PaginaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Nested
    @DisplayName("GET /clientes/{clienteId}")
    class Obtener {

        @Test
        @DisplayName("200 con los datos del cliente y sin contraseña")
        void obtener_cuandoClienteExiste_debeResponder200() throws Exception {
            // Given
            when(clienteService.obtener(CLIENTE_ID)).thenReturn(respuestaJoseLema());

            // When / Then
            mockMvc.perform(get("/clientes/{clienteId}", CLIENTE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clienteId").value(CLIENTE_ID.toString()))
                    .andExpect(jsonPath("$.identificacion").value("1712345678"))
                    .andExpect(jsonPath("$.contrasena").doesNotExist());
        }

        @Test
        @DisplayName("404 cuando el cliente no existe")
        void obtener_cuandoClienteNoExiste_debeResponder404() throws Exception {
            // Given
            when(clienteService.obtener(CLIENTE_ID)).thenThrow(new ClienteNoEncontradoException(CLIENTE_ID));

            // When / Then
            mockMvc.perform(get("/clientes/{clienteId}", CLIENTE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Cliente no encontrado"));
        }

        @Test
        @DisplayName("400 cuando el identificador no es un UUID")
        void obtener_cuandoIdentificadorNoEsUuid_debeResponder400() throws Exception {
            mockMvc.perform(get("/clientes/{clienteId}", "no-es-un-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Parámetro inválido"));
            verify(clienteService, never()).obtener(any());
        }
    }

    @Nested
    @DisplayName("GET /clientes")
    class Listar {

        @Test
        @DisplayName("200 con la página de clientes y los filtros aplicados")
        void listar_cuandoHayFiltros_debeResponder200ConPagina() throws Exception {
            // Given
            PaginaResponse<ClienteResponse> pagina = new PaginaResponse<>(List.of(respuestaJoseLema()), 0, 20, 1, 1);
            when(clienteService.listar(eq(true), eq("1712345678"), any(Pageable.class))).thenReturn(pagina);

            // When / Then
            mockMvc.perform(get("/clientes")
                            .param("page", "0").param("size", "20")
                            .param("estado", "true").param("identificacion", "1712345678"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].clienteId").value(CLIENTE_ID.toString()))
                    .andExpect(jsonPath("$.contenido[0].contrasena").doesNotExist())
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.tamano").value(20))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.totalPaginas").value(1));
        }

        @Test
        @DisplayName("Sin parámetros usa página 0, tamaño 20 y orden por nombre")
        void listar_cuandoSinParametros_debeUsarPaginacionPorDefecto() throws Exception {
            // Given
            when(clienteService.listar(any(), any(), any(Pageable.class)))
                    .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

            // When
            mockMvc.perform(get("/clientes")).andExpect(status().isOk());

            // Then
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(clienteService).listar(isNull(), isNull(), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isZero();
            assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
            assertThat(pageable.getValue().getSort().getOrderFor("nombre")).isNotNull();
        }

        @Test
        @DisplayName("400 cuando se ordena por un campo inexistente")
        void listar_cuandoOrdenInvalido_debeResponder400() throws Exception {
            // Given
            when(clienteService.listar(any(), any(), any(Pageable.class))).thenThrow(
                    new PropertyReferenceException("campoInexistente", TypeInformation.of(Cliente.class), List.of()));

            // When / Then
            mockMvc.perform(get("/clientes").param("sort", "campoInexistente"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Parámetro inválido"));
        }
    }

    @Nested
    @DisplayName("PUT /clientes/{clienteId}")
    class Actualizar {

        private static final String ACTUALIZACION_VALIDA = """
                {
                  "nombre": "Jose Lema",
                  "genero": "MASCULINO",
                  "edad": 36,
                  "identificacion": "1712345678",
                  "direccion": "Amazonas y NNUU",
                  "telefono": "098254785",
                  "estado": true
                }
                """;

        @Test
        @DisplayName("200 con el cliente actualizado")
        void actualizar_cuandoDatosValidos_debeResponder200() throws Exception {
            // Given
            when(clienteService.actualizar(eq(CLIENTE_ID), any(ClienteUpdateRequest.class)))
                    .thenReturn(respuestaJoseLema());

            // When / Then
            mockMvc.perform(put("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(ACTUALIZACION_VALIDA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clienteId").value(CLIENTE_ID.toString()))
                    .andExpect(jsonPath("$.contrasena").doesNotExist());
        }

        @Test
        @DisplayName("400 si se intenta enviar la contraseña (solo se cambia por PATCH)")
        void actualizar_cuandoIncluyeContrasena_debeResponder400() throws Exception {
            String cuerpo = ACTUALIZACION_VALIDA.replace("\"estado\": true", "\"estado\": true, \"contrasena\": \"nueva\"");

            mockMvc.perform(put("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Solicitud mal formada"));
            verify(clienteService, never()).actualizar(any(), any());
        }

        @Test
        @DisplayName("400 si falta un campo obligatorio")
        void actualizar_cuandoFaltaEstado_debeResponder400() throws Exception {
            String cuerpo = ACTUALIZACION_VALIDA.replace(",\n  \"estado\": true", "");

            mockMvc.perform(put("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.estado").exists());
        }

        @Test
        @DisplayName("404 cuando el cliente no existe")
        void actualizar_cuandoClienteNoExiste_debeResponder404() throws Exception {
            // Given
            when(clienteService.actualizar(eq(CLIENTE_ID), any(ClienteUpdateRequest.class)))
                    .thenThrow(new ClienteNoEncontradoException(CLIENTE_ID));

            // When / Then
            mockMvc.perform(put("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(ACTUALIZACION_VALIDA))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 cuando la identificación pertenece a otro cliente")
        void actualizar_cuandoIdentificacionDuplicada_debeResponder409() throws Exception {
            // Given
            when(clienteService.actualizar(eq(CLIENTE_ID), any(ClienteUpdateRequest.class)))
                    .thenThrow(new IdentificacionDuplicadaException("1712345678"));

            // When / Then
            mockMvc.perform(put("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(ACTUALIZACION_VALIDA))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("PATCH /clientes/{clienteId}")
    class ActualizarParcial {

        @Test
        @DisplayName("200 al cambiar solo la dirección")
        void actualizarParcial_cuandoSoloDireccion_debeResponder200() throws Exception {
            // Given
            when(clienteService.actualizarParcial(eq(CLIENTE_ID), any(ClientePatchRequest.class)))
                    .thenReturn(respuestaJoseLema());

            // When / Then
            mockMvc.perform(patch("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"direccion\": \"Amazonas y NNUU\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("400 cuando el cuerpo no trae ningún campo")
        void actualizarParcial_cuandoCuerpoVacio_debeResponder400() throws Exception {
            mockMvc.perform(patch("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Datos inválidos"));
            verify(clienteService, never()).actualizarParcial(any(), any());
        }

        @Test
        @DisplayName("400 cuando la edad enviada es menor a 18")
        void actualizarParcial_cuandoEdadMenorA18_debeResponder400() throws Exception {
            mockMvc.perform(patch("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"edad\": 17}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.edad").exists());
        }

        @Test
        @DisplayName("404 cuando el cliente no existe")
        void actualizarParcial_cuandoClienteNoExiste_debeResponder404() throws Exception {
            // Given
            when(clienteService.actualizarParcial(eq(CLIENTE_ID), any(ClientePatchRequest.class)))
                    .thenThrow(new ClienteNoEncontradoException(CLIENTE_ID));

            // When / Then
            mockMvc.perform(patch("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": false}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 cuando la identificación pertenece a otro cliente")
        void actualizarParcial_cuandoIdentificacionDuplicada_debeResponder409() throws Exception {
            // Given
            when(clienteService.actualizarParcial(eq(CLIENTE_ID), any(ClientePatchRequest.class)))
                    .thenThrow(new IdentificacionDuplicadaException("ABC12345"));

            // When / Then
            mockMvc.perform(patch("/clientes/{clienteId}", CLIENTE_ID)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"identificacion\": \"ABC12345\"}"))
                    .andExpect(status().isConflict());
        }
    }
}

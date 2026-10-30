package com.banco.clientes.cliente;

import com.banco.clientes.shared.config.RabbitConfig;
import com.banco.clientes.support.TestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración real (F6): API → servicio → PostgreSQL (Flyway) → evento en RabbitMQ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@DisplayName("Integración de clientes con PostgreSQL y RabbitMQ")
class ClienteIntegrationTest {

    private static final String COLA_PRUEBA = "test.clientes.eventos";
    private static final long ESPERA_EVENTO_MS = 5000;
    private static final long ESPERA_SIN_EVENTO_MS = 1000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepararColaDeEventos() {
        Queue cola = new Queue(COLA_PRUEBA, false, false, false);
        amqpAdmin.declareQueue(cola);
        amqpAdmin.declareBinding(BindingBuilder.bind(cola)
                .to(new TopicExchange(RabbitConfig.CLIENTES_EXCHANGE)).with("cliente.*"));
        amqpAdmin.purgeQueue(COLA_PRUEBA, false);
    }

    private static String identificacionUnica() {
        return "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String clienteJson(String identificacion) {
        return """
                {
                  "nombre": "Marianela Montalvo",
                  "genero": "FEMENINO",
                  "edad": 30,
                  "identificacion": "%s",
                  "direccion": "Amazonas y NNUU",
                  "telefono": "097548965",
                  "contrasena": "5678"
                }
                """.formatted(identificacion);
    }

    private JsonNode crearCliente(String identificacion) throws Exception {
        String respuesta = mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content(clienteJson(identificacion)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta);
    }

    private JsonNode recibirEvento() throws Exception {
        Message mensaje = rabbitTemplate.receive(COLA_PRUEBA, ESPERA_EVENTO_MS);
        assertThat(mensaje).as("evento recibido en menos de 5 s (SC-005)").isNotNull();
        return objectMapper.readTree(mensaje.getBody());
    }

    @Test
    @DisplayName("Crear persiste el cliente con hash BCrypt y publica exactamente un CLIENTE_CREADO")
    void crear_cuandoDatosValidos_debePersistirConHashYPublicarUnEvento() throws Exception {
        // Given
        String identificacion = identificacionUnica();

        // When
        JsonNode cliente = crearCliente(identificacion);

        // Then: persistencia real
        String contrasenaGuardada = jdbcTemplate.queryForObject("""
                SELECT c.contrasena FROM cliente c JOIN persona p ON p.id = c.persona_id
                WHERE p.identificacion = ?
                """, String.class, identificacion);
        assertThat(contrasenaGuardada).startsWith("$2").isNotEqualTo("5678");

        // Then: evento publicado
        JsonNode evento = recibirEvento();
        assertThat(evento.get("tipo").asText()).isEqualTo("CLIENTE_CREADO");
        assertThat(evento.get("clienteId").asText()).isEqualTo(cliente.get("clienteId").asText());
        assertThat(evento.get("nombre").asText()).isEqualTo("Marianela Montalvo");
        assertThat(evento.get("eventId").asText()).isNotBlank();
        assertThat(evento.has("contrasena")).isFalse();
        assertThat(rabbitTemplate.receive(COLA_PRUEBA, ESPERA_SIN_EVENTO_MS)).isNull();
    }

    @Test
    @DisplayName("Un registro rechazado por identificación duplicada no publica eventos")
    void crear_cuandoIdentificacionDuplicada_noDebePublicarEvento() throws Exception {
        // Given
        String identificacion = identificacionUnica();
        crearCliente(identificacion);
        recibirEvento();

        // When
        mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON).content(clienteJson(identificacion)))
                .andExpect(status().isConflict());

        // Then
        assertThat(rabbitTemplate.receive(COLA_PRUEBA, ESPERA_SIN_EVENTO_MS)).isNull();
    }

    @Test
    @DisplayName("Ciclo completo: crear, actualizar y eliminar publican 3 eventos y la fila queda con eliminado=true")
    void cicloCompleto_cuandoCrearActualizarEliminar_debePublicarTresEventosYConservarFila() throws Exception {
        // Given
        String identificacion = identificacionUnica();
        String clienteId = crearCliente(identificacion).get("clienteId").asText();

        // When
        mockMvc.perform(patch("/clientes/{clienteId}", clienteId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direccion\": \"Av. 6 de Diciembre\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/clientes/{clienteId}", clienteId))
                .andExpect(status().isNoContent());

        // Then: exactamente 3 eventos, en orden, con eventId distintos
        JsonNode creado = recibirEvento();
        JsonNode actualizado = recibirEvento();
        JsonNode eliminado = recibirEvento();
        assertThat(List.of(creado, actualizado, eliminado))
                .extracting(evento -> evento.get("tipo").asText())
                .containsExactly("CLIENTE_CREADO", "CLIENTE_ACTUALIZADO", "CLIENTE_ELIMINADO");
        assertThat(List.of(creado, actualizado, eliminado))
                .extracting(evento -> evento.get("eventId").asText())
                .doesNotHaveDuplicates();
        assertThat(List.of(creado, actualizado, eliminado))
                .allSatisfy(evento -> assertThat(evento.get("clienteId").asText()).isEqualTo(clienteId));
        assertThat(eliminado.get("estado").asBoolean()).isFalse();
        assertThat(rabbitTemplate.receive(COLA_PRUEBA, ESPERA_SIN_EVENTO_MS)).isNull();

        // Then: la fila se conserva (eliminación lógica)
        Boolean marcadoEliminado = jdbcTemplate.queryForObject(
                "SELECT eliminado FROM cliente WHERE cliente_id = ?", Boolean.class, UUID.fromString(clienteId));
        assertThat(marcadoEliminado).isTrue();
    }
}

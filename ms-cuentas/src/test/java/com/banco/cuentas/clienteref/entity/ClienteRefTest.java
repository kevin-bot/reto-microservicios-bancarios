package com.banco.cuentas.clienteref.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Réplica local de cliente")
class ClienteRefTest {

    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

    private static ClienteRef clienteJose() {
        return ClienteRef.crear(UUID.randomUUID(), "Jose Lema", true, T0);
    }

    @Test
    @DisplayName("Se crea con los datos del primer evento")
    void crear_cuandoDatosDelEvento_debeQuedarActivoConSuFecha() {
        // When
        ClienteRef cliente = clienteJose();

        // Then
        assertThat(cliente.getNombre()).isEqualTo("Jose Lema");
        assertThat(cliente.estaActivo()).isTrue();
        assertThat(cliente.getUltimaActualizacion()).isEqualTo(T0);
    }

    @Test
    @DisplayName("Aplica un evento más reciente")
    void aplicar_cuandoEventoPosterior_debeActualizarYDevolverTrue() {
        // Given
        ClienteRef cliente = clienteJose();
        Instant despues = T0.plusSeconds(60);

        // When
        boolean aplicado = cliente.aplicar("Jose Antonio Lema", false, despues);

        // Then
        assertThat(aplicado).isTrue();
        assertThat(cliente.getNombre()).isEqualTo("Jose Antonio Lema");
        assertThat(cliente.estaActivo()).isFalse();
        assertThat(cliente.getUltimaActualizacion()).isEqualTo(despues);
    }

    @Test
    @DisplayName("Descarta un evento anterior al último aplicado (fuera de orden)")
    void aplicar_cuandoEventoAnterior_noDebeCambiarNadaYDevolverFalse() {
        // Given
        ClienteRef cliente = clienteJose();

        // When
        boolean aplicado = cliente.aplicar("Nombre Viejo", false, T0.minusSeconds(60));

        // Then
        assertThat(aplicado).isFalse();
        assertThat(cliente.getNombre()).isEqualTo("Jose Lema");
        assertThat(cliente.estaActivo()).isTrue();
        assertThat(cliente.getUltimaActualizacion()).isEqualTo(T0);
    }

    @Test
    @DisplayName("Descarta un evento con la misma fecha que el último aplicado")
    void aplicar_cuandoEventoConMismaFecha_debeDevolverFalse() {
        // Given
        ClienteRef cliente = clienteJose();

        // When / Then
        assertThat(cliente.aplicar("Otro", false, T0)).isFalse();
        assertThat(cliente.getNombre()).isEqualTo("Jose Lema");
    }
}

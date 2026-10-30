package com.banco.cuentas.shared.fecha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Rango de fechas del reporte y del listado")
class RangoFechasTest {

    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");

    @Test
    @DisplayName("Interpreta dos fechas ISO separadas por coma")
    void parse_cuandoFormatoValido_debeCrearRango() {
        // When
        RangoFechas rango = RangoFechas.parse("2022-02-01,2022-02-10");

        // Then
        assertThat(rango.inicio()).isEqualTo(LocalDate.of(2022, 2, 1));
        assertThat(rango.fin()).isEqualTo(LocalDate.of(2022, 2, 10));
    }

    @Test
    @DisplayName("Acepta un rango de un solo día")
    void parse_cuandoInicioIgualAFin_debeCrearRango() {
        RangoFechas rango = RangoFechas.parse("2022-02-10,2022-02-10");

        assertThat(rango.inicio()).isEqualTo(rango.fin());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"2022-02-01", "2022-02-01,2022-02-05,2022-02-10", "01/02/2022,10/02/2022",
            "2022-02-01,", "2022-13-01,2022-12-01"})
    @DisplayName("Rechaza formatos inválidos")
    void parse_cuandoFormatoInvalido_debeLanzarIllegalArgumentException(String texto) {
        assertThatThrownBy(() -> RangoFechas.parse(texto)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza una fecha de inicio posterior a la de fin")
    void parse_cuandoInicioPosteriorAFin_debeLanzarIllegalArgumentException() {
        assertThatThrownBy(() -> RangoFechas.parse("2022-02-10,2022-02-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inicio");
    }

    @Test
    @DisplayName("Rechaza rangos de más de un año (366 días)")
    void parse_cuandoRangoSuperaUnAnio_debeLanzarIllegalArgumentException() {
        assertThatThrownBy(() -> RangoFechas.parse("2022-01-01,2023-01-03"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("año");
    }

    @Test
    @DisplayName("Acepta un rango de exactamente un año")
    void parse_cuandoRangoEsUnAnio_debeCrearRango() {
        RangoFechas rango = RangoFechas.parse("2024-01-01,2024-12-31");

        assertThat(rango.fin()).isEqualTo(LocalDate.of(2024, 12, 31));
    }

    @Test
    @DisplayName("Convierte el rango a instantes de días completos en la zona del banco")
    void desdeYHasta_cuandoZonaGuayaquil_debenCubrirDiasCompletos() {
        // Given
        RangoFechas rango = RangoFechas.parse("2022-02-01,2022-02-10");

        // When / Then: Guayaquil es UTC-5
        assertThat(rango.desde(GUAYAQUIL)).isEqualTo(Instant.parse("2022-02-01T05:00:00Z"));
        assertThat(rango.hasta(GUAYAQUIL)).isEqualTo(Instant.parse("2022-02-11T05:00:00Z"));
    }
}

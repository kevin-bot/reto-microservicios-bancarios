package com.banco.clientes.shared.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Validación @AlMenosUnCampo")
class AlMenosUnCampoValidatorTest {

    @AlMenosUnCampo
    record SolicitudParcial(String nombre, Integer edad, Boolean estado) {
    }

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void crearValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarFactory() {
        factory.close();
    }

    @Test
    @DisplayName("Es inválida si todos los campos son nulos")
    void validar_cuandoTodosLosCamposNulos_debeSerInvalida() {
        // Given
        SolicitudParcial solicitud = new SolicitudParcial(null, null, null);

        // When
        Set<ConstraintViolation<SolicitudParcial>> violaciones = validator.validate(solicitud);

        // Then
        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.iterator().next().getMessage()).isEqualTo("debe incluir al menos un campo");
    }

    @Test
    @DisplayName("Es válida si al menos un campo tiene valor")
    void validar_cuandoUnCampoTieneValor_debeSerValida() {
        // Given
        SolicitudParcial solicitud = new SolicitudParcial(null, null, false);

        // When / Then
        assertThat(validator.validate(solicitud)).isEmpty();
    }
}

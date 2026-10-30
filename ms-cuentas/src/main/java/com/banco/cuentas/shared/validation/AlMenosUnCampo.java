package com.banco.cuentas.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que un record tenga al menos un componente no nulo (p. ej. una actualización parcial).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AlMenosUnCampoValidator.class)
public @interface AlMenosUnCampo {

    String message() default "debe incluir al menos un campo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.banco.cuentas.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

public class AlMenosUnCampoValidator implements ConstraintValidator<AlMenosUnCampo, Record> {

    @Override
    public boolean isValid(Record valor, ConstraintValidatorContext context) {
        if (valor == null) {
            return true;
        }
        return Arrays.stream(valor.getClass().getRecordComponents())
                .anyMatch(componente -> leer(componente, valor) != null);
    }

    private static Object leer(RecordComponent componente, Record valor) {
        try {
            return componente.getAccessor().invoke(valor);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("No se pudo leer el campo " + componente.getName(), ex);
        }
    }
}

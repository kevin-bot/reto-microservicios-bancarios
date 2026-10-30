package com.banco.cuentas.shared.fecha;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Permite recibir {@code @RequestParam RangoFechas fecha}. Spring Boot registra automáticamente
 * los beans {@link Converter} en Spring MVC; un valor inválido termina en 400 "Parámetro inválido".
 */
@Component
public class RangoFechasConverter implements Converter<String, RangoFechas> {

    @Override
    public RangoFechas convert(@NonNull String texto) {
        return RangoFechas.parse(texto);
    }
}

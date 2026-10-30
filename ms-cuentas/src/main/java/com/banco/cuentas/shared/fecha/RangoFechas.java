package com.banco.cuentas.shared.fecha;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Rango de días completos, ambos extremos incluidos, recibido como "AAAA-MM-DD,AAAA-MM-DD".
 */
public record RangoFechas(LocalDate inicio, LocalDate fin) {

    private static final long DIAS_MAXIMOS = 366;
    private static final String FORMATO = "debe tener el formato AAAA-MM-DD,AAAA-MM-DD";

    public RangoFechas {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("el rango " + FORMATO);
        }
        if (inicio.isAfter(fin)) {
            throw new IllegalArgumentException("la fecha de inicio no puede ser posterior a la de fin");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > DIAS_MAXIMOS) {
            throw new IllegalArgumentException("el rango no puede superar un año");
        }
    }

    public static RangoFechas parse(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("el rango " + FORMATO);
        }
        String[] partes = texto.split(",", -1);
        if (partes.length != 2) {
            throw new IllegalArgumentException("el rango " + FORMATO);
        }
        try {
            return new RangoFechas(LocalDate.parse(partes[0].trim()), LocalDate.parse(partes[1].trim()));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("el rango " + FORMATO, ex);
        }
    }

    /** Inicio del primer día, inclusive. */
    public Instant desde(ZoneId zona) {
        return inicio.atStartOfDay(zona).toInstant();
    }

    /** Inicio del día siguiente al último, exclusivo. */
    public Instant hasta(ZoneId zona) {
        return fin.plusDays(1).atStartOfDay(zona).toInstant();
    }
}

package com.banco.cuentas.shared.exception;

/**
 * Violación de una regla de negocio. Lleva su propio título para que {@link GlobalExceptionHandler}
 * la traduzca sin conocer cada subclase. No conoce HTTP.
 */
public abstract class ReglaNegocioException extends RuntimeException {

    private final String titulo;

    protected ReglaNegocioException(String titulo, String mensaje) {
        super(mensaje);
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}

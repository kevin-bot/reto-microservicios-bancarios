package com.banco.cuentas.shared.exception;

public abstract class RecursoNoEncontradoException extends RuntimeException {

    private final String titulo;

    protected RecursoNoEncontradoException(String titulo, String mensaje) {
        super(mensaje);
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}

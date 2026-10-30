package com.banco.cuentas.shared.exception;

public abstract class ConflictoException extends RuntimeException {

    private final String titulo;

    protected ConflictoException(String titulo, String mensaje) {
        super(mensaje);
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}

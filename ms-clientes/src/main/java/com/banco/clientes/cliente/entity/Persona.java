package com.banco.clientes.cliente.entity;

import com.banco.clientes.shared.exception.DomainValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Entity
@Table(name = "persona")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Persona {

    private static final int EDAD_MINIMA = 18;
    private static final int EDAD_MAXIMA = 120;
    private static final int NOMBRE_MAX = 100;
    private static final int DIRECCION_MAX = 200;
    private static final Pattern IDENTIFICACION = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9]{7,15}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NOMBRE_MAX)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Genero genero;

    @Column(nullable = false)
    private Integer edad;

    @Column(nullable = false, unique = true, length = 20)
    private String identificacion;

    @Column(nullable = false, length = DIRECCION_MAX)
    private String direccion;

    @Column(nullable = false, length = 15)
    private String telefono;

    protected Persona(String nombre, Genero genero, Integer edad, String identificacion,
                      String direccion, String telefono) {
        asignarDatosPersonales(nombre, genero, edad, identificacion);
        asignarContacto(direccion, telefono);
    }

    public void actualizarDatosPersonales(String nombre, Genero genero, Integer edad, String identificacion) {
        asignarDatosPersonales(nombre, genero, edad, identificacion);
    }

    public void actualizarContacto(String direccion, String telefono) {
        asignarContacto(direccion, telefono);
    }

    private void asignarDatosPersonales(String nombre, Genero genero, Integer edad, String identificacion) {
        validarTexto(nombre, "nombre", NOMBRE_MAX);
        if (genero == null) {
            throw new DomainValidationException("El género es obligatorio");
        }
        if (edad == null || edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
            throw new DomainValidationException(
                    "La edad debe estar entre " + EDAD_MINIMA + " y " + EDAD_MAXIMA);
        }
        validarPatron(identificacion, IDENTIFICACION,
                "La identificación debe ser alfanumérica de 5 a 20 caracteres");
        this.nombre = nombre.trim();
        this.genero = genero;
        this.edad = edad;
        this.identificacion = identificacion;
    }

    private void asignarContacto(String direccion, String telefono) {
        validarTexto(direccion, "dirección", DIRECCION_MAX);
        validarPatron(telefono, TELEFONO, "El teléfono debe tener solo dígitos, entre 7 y 15");
        this.direccion = direccion.trim();
        this.telefono = telefono;
    }

    private static void validarTexto(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new DomainValidationException("El campo " + campo + " es obligatorio");
        }
        if (valor.trim().length() > longitudMaxima) {
            throw new DomainValidationException(
                    "El campo " + campo + " no puede superar " + longitudMaxima + " caracteres");
        }
    }

    private static void validarPatron(String valor, Pattern patron, String mensaje) {
        if (valor == null || !patron.matcher(valor).matches()) {
            throw new DomainValidationException(mensaje);
        }
    }
}

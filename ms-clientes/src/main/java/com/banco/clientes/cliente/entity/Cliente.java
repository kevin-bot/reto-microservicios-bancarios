package com.banco.clientes.cliente.entity;

import com.banco.clientes.cliente.exception.ClienteEliminadoException;
import com.banco.clientes.shared.exception.DomainValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cliente")
@PrimaryKeyJoinColumn(name = "persona_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cliente extends Persona {

    @Column(name = "cliente_id", nullable = false, unique = true, updatable = false)
    private UUID clienteId;

    /** Siempre un hash BCrypt; nunca texto plano. */
    @Column(nullable = false, length = 100)
    private String contrasena;

    @Column(nullable = false)
    private boolean estado;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean eliminado;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    private Cliente(String nombre, Genero genero, Integer edad, String identificacion,
                    String direccion, String telefono, String contrasenaHash, boolean estado) {
        super(nombre, genero, edad, identificacion, direccion, telefono);
        validarHash(contrasenaHash);
        this.clienteId = UUID.randomUUID();
        this.contrasena = contrasenaHash;
        this.estado = estado;
        this.eliminado = false;
    }

    public static Cliente crear(String nombre, Genero genero, Integer edad, String identificacion,
                                String direccion, String telefono, String contrasenaHash, boolean estado) {
        return new Cliente(nombre, genero, edad, identificacion, direccion, telefono, contrasenaHash, estado);
    }

    public void activar() {
        verificarNoEliminado();
        this.estado = true;
    }

    public void desactivar() {
        verificarNoEliminado();
        this.estado = false;
    }

    public void cambiarContrasena(String contrasenaHash) {
        validarHash(contrasenaHash);
        this.contrasena = contrasenaHash;
    }

    public void eliminar() {
        verificarNoEliminado();
        this.eliminado = true;
        this.estado = false;
    }

    public boolean estaEliminado() {
        return eliminado;
    }

    private void verificarNoEliminado() {
        if (eliminado) {
            throw new ClienteEliminadoException(clienteId);
        }
    }

    private static void validarHash(String contrasenaHash) {
        if (contrasenaHash == null || contrasenaHash.isBlank()) {
            throw new DomainValidationException("La contraseña es obligatoria");
        }
    }
}

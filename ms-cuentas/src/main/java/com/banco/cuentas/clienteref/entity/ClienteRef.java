package com.banco.cuentas.clienteref.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Copia local de solo lectura de un cliente de ms-clientes, alimentada por sus eventos.
 * Nunca se modifica por la API de ms-cuentas.
 */
@Entity
@Table(name = "cliente_ref")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClienteRef {

    @Id
    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private boolean estado;

    @Column(name = "ultima_actualizacion", nullable = false)
    private Instant ultimaActualizacion;

    private ClienteRef(UUID clienteId, String nombre, boolean estado, Instant fechaOcurrencia) {
        this.clienteId = clienteId;
        this.nombre = nombre;
        this.estado = estado;
        this.ultimaActualizacion = fechaOcurrencia;
    }

    public static ClienteRef crear(UUID clienteId, String nombre, boolean estado, Instant fechaOcurrencia) {
        return new ClienteRef(clienteId, nombre, estado, fechaOcurrencia);
    }

    /**
     * Aplica un evento solo si es más reciente que el último aplicado.
     *
     * @return {@code false} si el evento llegó fuera de orden y se descartó
     */
    public boolean aplicar(String nombre, boolean estado, Instant fechaOcurrencia) {
        if (!fechaOcurrencia.isAfter(ultimaActualizacion)) {
            return false;
        }
        this.nombre = nombre;
        this.estado = estado;
        this.ultimaActualizacion = fechaOcurrencia;
        return true;
    }

    public boolean estaActivo() {
        return estado;
    }
}

package com.banco.clientes.cliente.dto;

import com.banco.clientes.cliente.entity.Genero;

import java.util.UUID;

/**
 * Representación pública del cliente. Nunca incluye la contraseña.
 */
public record ClienteResponse(
        UUID clienteId,
        String nombre,
        Genero genero,
        Integer edad,
        String identificacion,
        String direccion,
        String telefono,
        boolean estado) {
}

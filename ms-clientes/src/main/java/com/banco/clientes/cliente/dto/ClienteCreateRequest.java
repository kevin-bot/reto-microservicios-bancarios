package com.banco.clientes.cliente.dto;

import com.banco.clientes.cliente.entity.Genero;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteCreateRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull Genero genero,
        @NotNull @Min(18) @Max(120) Integer edad,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9]{5,20}$", message = "debe ser alfanumérica de 5 a 20 caracteres")
        String identificacion,
        @NotBlank @Size(max = 200) String direccion,
        @NotBlank
        @Pattern(regexp = "^[0-9]{7,15}$", message = "debe tener solo dígitos, entre 7 y 15")
        String telefono,
        @NotBlank @Size(min = 4, max = 72) String contrasena,
        Boolean estado) {

    /** Si no se envía, el cliente se registra activo (FR-002). */
    public boolean estadoOPorDefecto() {
        return estado == null || estado;
    }
}

package com.banco.clientes.cliente.dto;

import com.banco.clientes.cliente.entity.Genero;
import com.banco.clientes.shared.validation.AlMenosUnCampo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Actualización parcial (PATCH): los campos omitidos o nulos no cambian. Es el único medio para
 * cambiar la contraseña.
 */
@AlMenosUnCampo
public record ClientePatchRequest(
        @Size(min = 1, max = 100) String nombre,
        Genero genero,
        @Min(18) @Max(120) Integer edad,
        @Pattern(regexp = "^[A-Za-z0-9]{5,20}$", message = "debe ser alfanumérica de 5 a 20 caracteres")
        String identificacion,
        @Size(min = 1, max = 200) String direccion,
        @Pattern(regexp = "^[0-9]{7,15}$", message = "debe tener solo dígitos, entre 7 y 15")
        String telefono,
        @Size(min = 4, max = 72) String contrasena,
        Boolean estado) {
}

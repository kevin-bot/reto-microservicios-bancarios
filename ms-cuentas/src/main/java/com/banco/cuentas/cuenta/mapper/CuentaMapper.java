package com.banco.cuentas.cuenta.mapper;

import com.banco.cuentas.cuenta.dto.CuentaResponse;
import com.banco.cuentas.cuenta.entity.Cuenta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Solo entidad → respuesta: las cuentas se crean y modifican con sus métodos de dominio.
 */
@Mapper
public interface CuentaMapper {

    @Mapping(target = "clienteId", source = "cliente.clienteId")
    @Mapping(target = "nombreCliente", source = "cliente.nombre")
    CuentaResponse toResponse(Cuenta cuenta);
}

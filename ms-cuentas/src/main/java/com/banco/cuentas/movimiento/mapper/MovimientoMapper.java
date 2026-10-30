package com.banco.cuentas.movimiento.mapper;

import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.movimiento.dto.MovimientoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface MovimientoMapper {

    @Mapping(target = "clienteId", source = "cuenta.cliente.clienteId")
    @Mapping(target = "nombreCliente", source = "cuenta.cliente.nombre")
    @Mapping(target = "numeroCuenta", source = "cuenta.numeroCuenta")
    @Mapping(target = "tipoCuenta", source = "cuenta.tipo")
    @Mapping(target = "saldoInicial", source = "cuenta.saldoInicial")
    @Mapping(target = "estadoCuenta", source = "cuenta.estado")
    MovimientoResponse toResponse(Movimiento movimiento);
}

package com.banco.cuentas.reporte.mapper;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.reporte.dto.ClienteResumen;
import com.banco.cuentas.reporte.dto.CuentaReporte;
import com.banco.cuentas.reporte.dto.MovimientoReporte;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface ReporteMapper {

    ClienteResumen toClienteResumen(ClienteRef cliente);

    CuentaReporte toCuentaReporte(Cuenta cuenta, List<MovimientoReporte> movimientos);

    MovimientoReporte toMovimientoReporte(Movimiento movimiento);
}

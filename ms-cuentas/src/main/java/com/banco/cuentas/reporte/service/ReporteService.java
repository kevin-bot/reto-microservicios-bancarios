package com.banco.cuentas.reporte.service;

import com.banco.cuentas.reporte.dto.ReporteResponse;
import com.banco.cuentas.shared.fecha.RangoFechas;

import java.util.UUID;

public interface ReporteService {

    ReporteResponse generar(UUID clienteId, RangoFechas rango);
}

package com.banco.cuentas.movimiento.service;

import com.banco.cuentas.movimiento.dto.MovimientoCreateRequest;
import com.banco.cuentas.movimiento.dto.MovimientoPatchRequest;
import com.banco.cuentas.movimiento.dto.MovimientoResponse;
import com.banco.cuentas.movimiento.dto.MovimientoUpdateRequest;
import com.banco.cuentas.shared.dto.PaginaResponse;
import com.banco.cuentas.shared.fecha.RangoFechas;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MovimientoService {

    MovimientoResponse registrar(MovimientoCreateRequest request);

    MovimientoResponse obtener(UUID movimientoId);

    PaginaResponse<MovimientoResponse> listar(UUID clienteId, String numeroCuenta, RangoFechas rango, Pageable pageable);

    MovimientoResponse corregir(UUID movimientoId, MovimientoUpdateRequest request);

    MovimientoResponse corregirParcial(UUID movimientoId, MovimientoPatchRequest request);
}

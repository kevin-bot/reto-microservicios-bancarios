package com.banco.cuentas.cuenta.service;

import com.banco.cuentas.cuenta.dto.CuentaCreateRequest;
import com.banco.cuentas.cuenta.dto.CuentaPatchRequest;
import com.banco.cuentas.cuenta.dto.CuentaResponse;
import com.banco.cuentas.cuenta.dto.CuentaUpdateRequest;
import com.banco.cuentas.shared.dto.PaginaResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CuentaService {

    CuentaResponse crear(CuentaCreateRequest request);

    CuentaResponse obtener(String numeroCuenta);

    PaginaResponse<CuentaResponse> listar(UUID clienteId, Pageable pageable);

    CuentaResponse actualizar(String numeroCuenta, CuentaUpdateRequest request);

    CuentaResponse actualizarParcial(String numeroCuenta, CuentaPatchRequest request);
}

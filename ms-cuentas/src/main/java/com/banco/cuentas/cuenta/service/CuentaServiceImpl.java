package com.banco.cuentas.cuenta.service;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.cuenta.dto.CuentaCreateRequest;
import com.banco.cuentas.cuenta.dto.CuentaPatchRequest;
import com.banco.cuentas.cuenta.dto.CuentaResponse;
import com.banco.cuentas.cuenta.dto.CuentaUpdateRequest;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.exception.ClienteNoDisponibleException;
import com.banco.cuentas.cuenta.exception.CuentaNoEncontradaException;
import com.banco.cuentas.cuenta.exception.NumeroCuentaDuplicadoException;
import com.banco.cuentas.cuenta.mapper.CuentaMapper;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.cuenta.repository.CuentaSpecifications;
import com.banco.cuentas.shared.dto.PaginaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaServiceImpl implements CuentaService {

    private final CuentaRepository cuentaRepository;
    private final ClienteRefRepository clienteRefRepository;
    private final CuentaMapper cuentaMapper;

    @Override
    @Transactional
    public CuentaResponse crear(CuentaCreateRequest request) {
        if (cuentaRepository.existsByNumeroCuenta(request.numeroCuenta())) {
            throw new NumeroCuentaDuplicadoException(request.numeroCuenta());
        }
        ClienteRef cliente = clienteRefRepository.findById(request.clienteId())
                .orElseThrow(ClienteNoDisponibleException::new);

        Cuenta cuenta = Cuenta.abrir(request.numeroCuenta(), request.tipo(), request.saldoInicial(),
                request.estadoOPorDefecto(), cliente);
        Cuenta guardada = cuentaRepository.save(cuenta);
        log.info("Cuenta abierta (numeroCuenta={}, clienteId={})", guardada.getNumeroCuenta(), cliente.getClienteId());
        return cuentaMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public CuentaResponse obtener(String numeroCuenta) {
        return cuentaMapper.toResponse(buscar(numeroCuenta));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<CuentaResponse> listar(UUID clienteId, Pageable pageable) {
        return PaginaResponse.de(cuentaRepository.findAll(CuentaSpecifications.deCliente(clienteId), pageable)
                .map(cuentaMapper::toResponse));
    }

    @Override
    @Transactional
    public CuentaResponse actualizar(String numeroCuenta, CuentaUpdateRequest request) {
        Cuenta cuenta = buscar(numeroCuenta);
        cuenta.cambiarTipo(request.tipo());
        aplicarEstado(cuenta, request.estado());
        return cuentaMapper.toResponse(cuenta);
    }

    @Override
    @Transactional
    public CuentaResponse actualizarParcial(String numeroCuenta, CuentaPatchRequest request) {
        Cuenta cuenta = buscar(numeroCuenta);
        if (request.tipo() != null) {
            cuenta.cambiarTipo(request.tipo());
        }
        if (request.estado() != null) {
            aplicarEstado(cuenta, request.estado());
        }
        return cuentaMapper.toResponse(cuenta);
    }

    private Cuenta buscar(String numeroCuenta) {
        return cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(numeroCuenta));
    }

    private static void aplicarEstado(Cuenta cuenta, boolean activa) {
        if (activa) {
            cuenta.activar();
        } else {
            cuenta.desactivar();
        }
    }
}

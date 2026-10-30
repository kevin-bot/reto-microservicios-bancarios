package com.banco.cuentas.movimiento.service;

import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.cuenta.exception.CuentaNoEncontradaException;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.movimiento.dto.MovimientoCreateRequest;
import com.banco.cuentas.movimiento.dto.MovimientoPatchRequest;
import com.banco.cuentas.movimiento.dto.MovimientoResponse;
import com.banco.cuentas.movimiento.dto.MovimientoUpdateRequest;
import com.banco.cuentas.movimiento.exception.MovimientoNoEncontradoException;
import com.banco.cuentas.movimiento.mapper.MovimientoMapper;
import com.banco.cuentas.movimiento.repository.MovimientoRepository;
import com.banco.cuentas.movimiento.repository.MovimientoSpecifications;
import com.banco.cuentas.shared.config.BancoProperties;
import com.banco.cuentas.shared.dto.PaginaResponse;
import com.banco.cuentas.shared.fecha.RangoFechas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovimientoServiceImpl implements MovimientoService {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final MovimientoMapper movimientoMapper;
    private final Clock clock;
    private final BancoProperties bancoProperties;

    /**
     * La cuenta se bloquea ({@code SELECT ... FOR UPDATE}) hasta el commit: dos movimientos
     * simultáneos sobre la misma cuenta se aplican uno después del otro.
     */
    @Override
    @Transactional
    public MovimientoResponse registrar(MovimientoCreateRequest request) {
        Cuenta cuenta = bloquearCuenta(request.numeroCuenta());
        Movimiento movimiento = cuenta.registrarMovimiento(request.valor(), Instant.now(clock));
        Movimiento guardado = movimientoRepository.save(movimiento);
        log.info("Movimiento {} registrado (movimientoId={}, numeroCuenta={})",
                guardado.getTipo(), guardado.getMovimientoId(), cuenta.getNumeroCuenta());
        return movimientoMapper.toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public MovimientoResponse obtener(UUID movimientoId) {
        return movimientoRepository.findByMovimientoId(movimientoId)
                .map(movimientoMapper::toResponse)
                .orElseThrow(() -> new MovimientoNoEncontradoException(movimientoId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<MovimientoResponse> listar(UUID clienteId, String numeroCuenta, RangoFechas rango,
                                                     Pageable pageable) {
        Specification<Movimiento> filtro = MovimientoSpecifications.deCliente(clienteId)
                .and(MovimientoSpecifications.deCuenta(numeroCuenta))
                .and(MovimientoSpecifications.enRango(rango, bancoProperties.zona()));
        return PaginaResponse.de(movimientoRepository.findAll(filtro, pageable).map(movimientoMapper::toResponse));
    }

    @Override
    @Transactional
    public MovimientoResponse corregir(UUID movimientoId, MovimientoUpdateRequest request) {
        return corregirValor(movimientoId, request.valor());
    }

    @Override
    @Transactional
    public MovimientoResponse corregirParcial(UUID movimientoId, MovimientoPatchRequest request) {
        return corregirValor(movimientoId, request.valor());
    }

    /**
     * Orden obligatorio (research R4): primero se bloquea la cuenta y recién después se lee el
     * movimiento y sus posteriores, para no recalcular sobre datos desactualizados.
     */
    private MovimientoResponse corregirValor(UUID movimientoId, BigDecimal nuevoValor) {
        String numeroCuenta = movimientoRepository.buscarNumeroCuenta(movimientoId)
                .orElseThrow(() -> new MovimientoNoEncontradoException(movimientoId));
        Cuenta cuenta = bloquearCuenta(numeroCuenta);
        Movimiento movimiento = movimientoRepository.findByMovimientoId(movimientoId)
                .orElseThrow(() -> new MovimientoNoEncontradoException(movimientoId));
        List<Movimiento> posteriores = movimientoRepository.findPosteriores(cuenta, movimiento.getFecha(),
                movimiento.getId());

        cuenta.corregirMovimiento(movimiento, nuevoValor, posteriores);
        log.info("Movimiento corregido (movimientoId={}, numeroCuenta={}, posterioresRecalculados={})",
                movimientoId, numeroCuenta, posteriores.size());
        return movimientoMapper.toResponse(movimiento);
    }

    private Cuenta bloquearCuenta(String numeroCuenta) {
        return cuentaRepository.buscarParaActualizar(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(numeroCuenta));
    }
}

package com.banco.cuentas.reporte.service;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.movimiento.repository.MovimientoRepository;
import com.banco.cuentas.reporte.dto.CuentaReporte;
import com.banco.cuentas.reporte.dto.MovimientoReporte;
import com.banco.cuentas.reporte.dto.ReporteResponse;
import com.banco.cuentas.reporte.exception.ClienteNoEncontradoException;
import com.banco.cuentas.reporte.mapper.ReporteMapper;
import com.banco.cuentas.shared.config.BancoProperties;
import com.banco.cuentas.shared.fecha.RangoFechas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Arma el estado de cuenta con 3 consultas, sin N+1: cliente, sus cuentas y, en una sola
 * consulta indexada, los movimientos de todas ellas en el rango (research R11).
 */
@Service
@RequiredArgsConstructor
public class ReporteServiceImpl implements ReporteService {

    private final ClienteRefRepository clienteRefRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final ReporteMapper reporteMapper;
    private final BancoProperties bancoProperties;

    @Override
    @Transactional(readOnly = true)
    public ReporteResponse generar(UUID clienteId, RangoFechas rango) {
        ClienteRef cliente = clienteRefRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException(clienteId));
        List<Cuenta> cuentas = cuentaRepository.findByClienteClienteId(clienteId).stream()
                .sorted(Comparator.comparing(Cuenta::getNumeroCuenta))
                .toList();

        Map<String, List<MovimientoReporte>> movimientosPorCuenta = movimientosDelRango(cuentas, rango);
        List<CuentaReporte> cuentasReporte = cuentas.stream()
                .map(cuenta -> reporteMapper.toCuentaReporte(cuenta,
                        movimientosPorCuenta.getOrDefault(cuenta.getNumeroCuenta(), List.of())))
                .toList();

        return new ReporteResponse(reporteMapper.toClienteResumen(cliente), rango.inicio(), rango.fin(), cuentasReporte);
    }

    /** Agrupa por número de cuenta conservando el orden cronológico de la consulta. */
    private Map<String, List<MovimientoReporte>> movimientosDelRango(List<Cuenta> cuentas, RangoFechas rango) {
        if (cuentas.isEmpty()) {
            return Map.of();
        }
        ZoneId zona = bancoProperties.zona();
        List<Long> cuentaIds = cuentas.stream().map(Cuenta::getId).toList();
        List<Movimiento> movimientos = movimientoRepository.findDelReporte(cuentaIds, rango.desde(zona), rango.hasta(zona));
        return movimientos.stream().collect(Collectors.groupingBy(
                movimiento -> movimiento.getCuenta().getNumeroCuenta(),
                Collectors.mapping(reporteMapper::toMovimientoReporte, Collectors.toList())));
    }
}

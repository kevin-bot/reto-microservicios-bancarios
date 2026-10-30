package com.banco.cuentas.movimiento.repository;

import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.shared.fecha.RangoFechas;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Filtros combinables del listado de movimientos. Un filtro sin valor no restringe la consulta.
 */
public final class MovimientoSpecifications {

    private MovimientoSpecifications() {
    }

    public static Specification<Movimiento> deCliente(UUID clienteId) {
        return (root, query, cb) -> clienteId == null
                ? null
                : cb.equal(root.get("cuenta").get("cliente").get("clienteId"), clienteId);
    }

    public static Specification<Movimiento> deCuenta(String numeroCuenta) {
        return (root, query, cb) -> numeroCuenta == null || numeroCuenta.isBlank()
                ? null
                : cb.equal(root.get("cuenta").get("numeroCuenta"), numeroCuenta);
    }

    public static Specification<Movimiento> enRango(RangoFechas rango, ZoneId zona) {
        return (root, query, cb) -> rango == null
                ? null
                : cb.and(cb.greaterThanOrEqualTo(root.get("fecha"), rango.desde(zona)),
                         cb.lessThan(root.get("fecha"), rango.hasta(zona)));
    }
}

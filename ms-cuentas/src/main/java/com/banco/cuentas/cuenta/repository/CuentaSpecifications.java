package com.banco.cuentas.cuenta.repository;

import com.banco.cuentas.cuenta.entity.Cuenta;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * Filtros del listado de cuentas. Un filtro sin valor no restringe la consulta.
 */
public final class CuentaSpecifications {

    private CuentaSpecifications() {
    }

    public static Specification<Cuenta> deCliente(UUID clienteId) {
        return (root, query, cb) -> clienteId == null
                ? null
                : cb.equal(root.get("cliente").get("clienteId"), clienteId);
    }
}

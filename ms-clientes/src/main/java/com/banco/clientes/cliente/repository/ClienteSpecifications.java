package com.banco.clientes.cliente.repository;

import com.banco.clientes.cliente.entity.Cliente;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros del listado de clientes. Los filtros opcionales sin valor no restringen la consulta.
 */
public final class ClienteSpecifications {

    private ClienteSpecifications() {
    }

    public static Specification<Cliente> noEliminado() {
        return (root, query, cb) -> cb.isFalse(root.get("eliminado"));
    }

    public static Specification<Cliente> conEstado(Boolean estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Cliente> conIdentificacion(String identificacion) {
        return (root, query, cb) -> identificacion == null || identificacion.isBlank()
                ? null
                : cb.equal(root.get("identificacion"), identificacion);
    }
}

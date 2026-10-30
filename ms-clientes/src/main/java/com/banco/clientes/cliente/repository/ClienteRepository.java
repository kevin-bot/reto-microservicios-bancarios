package com.banco.clientes.cliente.repository;

import com.banco.clientes.cliente.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByClienteIdAndEliminadoFalse(UUID clienteId);

    /** Incluye clientes eliminados: su identificación sigue reservada. */
    boolean existsByIdentificacion(String identificacion);

    boolean existsByIdentificacionAndClienteIdNot(String identificacion, UUID clienteId);
}

package com.banco.cuentas.cuenta.repository;

import com.banco.cuentas.cuenta.entity.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CuentaRepository extends JpaRepository<Cuenta, Long>, JpaSpecificationExecutor<Cuenta> {

    @EntityGraph(attributePaths = "cliente")
    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    /** Carga el cliente en la misma consulta para evitar N+1 al mapear el listado. */
    @Override
    @EntityGraph(attributePaths = "cliente")
    Page<Cuenta> findAll(Specification<Cuenta> spec, Pageable pageable);

    /**
     * {@code SELECT ... FOR UPDATE}: serializa los movimientos sobre una misma cuenta hasta el
     * fin de la transacción (Principio V).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c join fetch c.cliente where c.numeroCuenta = :numeroCuenta")
    Optional<Cuenta> buscarParaActualizar(@Param("numeroCuenta") String numeroCuenta);

    boolean existsByNumeroCuenta(String numeroCuenta);

    List<Cuenta> findByClienteClienteId(UUID clienteId);
}

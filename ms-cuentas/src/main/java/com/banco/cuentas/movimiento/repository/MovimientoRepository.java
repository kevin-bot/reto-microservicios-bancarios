package com.banco.cuentas.movimiento.repository;

import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long>, JpaSpecificationExecutor<Movimiento> {

    @EntityGraph(attributePaths = {"cuenta", "cuenta.cliente"})
    Optional<Movimiento> findByMovimientoId(UUID movimientoId);

    /**
     * Solo el número de cuenta, sin cargar la entidad: permite bloquear la cuenta antes de leer
     * el movimiento a corregir (research R4).
     */
    @Query("select m.cuenta.numeroCuenta from Movimiento m where m.movimientoId = :movimientoId")
    Optional<String> buscarNumeroCuenta(@Param("movimientoId") UUID movimientoId);

    /** Movimientos posteriores a uno dado, en el orden en que afectan al saldo. */
    @Query("""
            select m from Movimiento m
            where m.cuenta = :cuenta and (m.fecha > :fecha or (m.fecha = :fecha and m.id > :id))
            order by m.fecha, m.id
            """)
    List<Movimiento> findPosteriores(@Param("cuenta") Cuenta cuenta, @Param("fecha") Instant fecha,
                                     @Param("id") Long id);

    @Query("""
            select m from Movimiento m
            where m.cuenta.id in :cuentaIds and m.fecha >= :desde and m.fecha < :hasta
            order by m.fecha, m.id
            """)
    List<Movimiento> findDelReporte(@Param("cuentaIds") List<Long> cuentaIds, @Param("desde") Instant desde,
                                    @Param("hasta") Instant hasta);

    /** Carga cuenta y cliente en la misma consulta para armar las filas planas sin N+1. */
    @Override
    @EntityGraph(attributePaths = {"cuenta", "cuenta.cliente"})
    Page<Movimiento> findAll(Specification<Movimiento> spec, Pageable pageable);
}

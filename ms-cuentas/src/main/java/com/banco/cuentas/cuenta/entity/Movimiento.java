package com.banco.cuentas.cuenta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transacción sobre una cuenta. Pertenece al agregado {@link Cuenta}: el constructor y los
 * métodos que modifican valor o saldo son de paquete, así que solo la cuenta puede crearlo o
 * corregirlo, manteniendo la invariante del saldo.
 */
@Entity
@Table(name = "movimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movimiento_id", nullable = false, unique = true, updatable = false)
    private UUID movimientoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false, updatable = false)
    private Cuenta cuenta;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    /** Saldo de la cuenta inmediatamente después de este movimiento. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    Movimiento(Cuenta cuenta, Instant fecha, BigDecimal valor, BigDecimal saldo) {
        this.movimientoId = UUID.randomUUID();
        this.cuenta = cuenta;
        this.fecha = fecha;
        this.tipo = TipoMovimiento.desde(valor);
        this.valor = valor;
        this.saldo = saldo;
    }

    void corregirValor(BigDecimal nuevoValor) {
        this.valor = nuevoValor;
        this.tipo = TipoMovimiento.desde(nuevoValor);
    }

    void actualizarSaldo(BigDecimal nuevoSaldo) {
        this.saldo = nuevoSaldo;
    }
}

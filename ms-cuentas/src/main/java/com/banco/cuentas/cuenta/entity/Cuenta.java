package com.banco.cuentas.cuenta.entity;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.cuenta.exception.ClienteNoDisponibleException;
import com.banco.cuentas.cuenta.exception.CuentaInactivaException;
import com.banco.cuentas.cuenta.exception.SaldoNoDisponibleException;
import com.banco.cuentas.shared.exception.DomainValidationException;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Raíz del agregado de cuentas: es la única que modifica el saldo disponible.
 */
@Entity
@Table(name = "cuenta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cuenta {

    private static final Pattern NUMERO_CUENTA = Pattern.compile("^[0-9]{6,20}$");
    private static final int DECIMALES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 20, updatable = false)
    private String numeroCuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoCuenta tipo;

    @Column(name = "saldo_inicial", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal saldoInicial;

    @Column(name = "saldo_disponible", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoDisponible;

    @Column(nullable = false)
    private boolean estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private ClienteRef cliente;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    private Cuenta(String numeroCuenta, TipoCuenta tipo, BigDecimal saldoInicial, boolean estado, ClienteRef cliente) {
        this.numeroCuenta = numeroCuenta;
        this.tipo = tipo;
        this.saldoInicial = saldoInicial;
        this.saldoDisponible = saldoInicial;
        this.estado = estado;
        this.cliente = cliente;
    }

    public static Cuenta abrir(String numeroCuenta, TipoCuenta tipo, BigDecimal saldoInicial, boolean estado,
                               ClienteRef cliente) {
        if (numeroCuenta == null || !NUMERO_CUENTA.matcher(numeroCuenta).matches()) {
            throw new DomainValidationException("El número de cuenta debe tener solo dígitos, entre 6 y 20");
        }
        validarTipo(tipo);
        if (saldoInicial == null || saldoInicial.signum() < 0) {
            throw new DomainValidationException("El saldo inicial debe ser mayor o igual a 0");
        }
        if (cliente == null || !cliente.estaActivo()) {
            throw new ClienteNoDisponibleException();
        }
        return new Cuenta(numeroCuenta, tipo, monto(saldoInicial, "saldo inicial"), estado, cliente);
    }

    /** Reactivar exige un cliente activo; activar una cuenta ya activa no cambia nada. */
    public void activar() {
        if (estado) {
            return;
        }
        verificarClienteActivo();
        this.estado = true;
    }

    public void desactivar() {
        this.estado = false;
    }

    public void cambiarTipo(TipoCuenta tipo) {
        validarTipo(tipo);
        this.tipo = tipo;
    }

    /**
     * F2/F3: aplica un depósito (valor positivo) o un retiro (valor negativo) y devuelve el
     * movimiento con su saldo resultante. Si el saldo quedara negativo, no cambia nada.
     */
    public Movimiento registrarMovimiento(BigDecimal valor, Instant fecha) {
        verificarOperable();
        BigDecimal monto = validarValor(valor);
        BigDecimal nuevoSaldo = saldoDisponible.add(monto);
        if (nuevoSaldo.signum() < 0) {
            throw new SaldoNoDisponibleException();
        }
        this.saldoDisponible = nuevoSaldo;
        return new Movimiento(this, fecha, monto, nuevoSaldo);
    }

    /**
     * Corrige el valor de un movimiento y recalcula en cadena su saldo, el de todos los
     * posteriores (en orden) y el saldo disponible. Primero calcula y valida todo; solo si ningún
     * saldo queda negativo aplica los cambios (todo o nada).
     */
    public void corregirMovimiento(Movimiento movimiento, BigDecimal nuevoValor, List<Movimiento> posteriores) {
        verificarOperable();
        if (movimiento.getCuenta() != this) {
            throw new DomainValidationException("El movimiento no pertenece a la cuenta " + numeroCuenta);
        }
        BigDecimal monto = validarValor(nuevoValor);

        BigDecimal saldo = movimiento.getSaldo().subtract(movimiento.getValor()).add(monto);
        List<BigDecimal> saldosNuevos = new ArrayList<>();
        saldosNuevos.add(saldo);
        for (Movimiento posterior : posteriores) {
            saldo = saldo.add(posterior.getValor());
            saldosNuevos.add(saldo);
        }
        if (saldosNuevos.stream().anyMatch(nuevo -> nuevo.signum() < 0)) {
            throw new SaldoNoDisponibleException();
        }

        movimiento.corregirValor(monto);
        movimiento.actualizarSaldo(saldosNuevos.getFirst());
        for (int i = 0; i < posteriores.size(); i++) {
            posteriores.get(i).actualizarSaldo(saldosNuevos.get(i + 1));
        }
        this.saldoDisponible = saldo;
    }

    private void verificarOperable() {
        if (!estado) {
            throw new CuentaInactivaException(numeroCuenta);
        }
        verificarClienteActivo();
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        if (valor == null || valor.signum() == 0) {
            throw new DomainValidationException("El valor del movimiento debe ser distinto de 0");
        }
        return monto(valor, "valor del movimiento");
    }

    private void verificarClienteActivo() {
        if (!cliente.estaActivo()) {
            throw new ClienteNoDisponibleException();
        }
    }

    private static void validarTipo(TipoCuenta tipo) {
        if (tipo == null) {
            throw new DomainValidationException("El tipo de cuenta es obligatorio");
        }
    }

    /** Normaliza un monto a 2 decimales, rechazando valores con más precisión (nunca redondea). */
    static BigDecimal monto(BigDecimal valor, String campo) {
        if (valor.stripTrailingZeros().scale() > DECIMALES) {
            throw new DomainValidationException("El " + campo + " no puede tener más de 2 decimales");
        }
        return valor.setScale(DECIMALES, RoundingMode.UNNECESSARY);
    }
}

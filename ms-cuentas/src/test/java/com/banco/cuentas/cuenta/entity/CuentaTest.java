package com.banco.cuentas.cuenta.entity;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.cuenta.exception.ClienteNoDisponibleException;
import com.banco.cuentas.cuenta.exception.CuentaInactivaException;
import com.banco.cuentas.cuenta.exception.SaldoNoDisponibleException;
import com.banco.cuentas.shared.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Entidad de dominio Cuenta")
class CuentaTest {

    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

    private static ClienteRef clienteActivo() {
        return ClienteRef.crear(UUID.randomUUID(), "Jose Lema", true, T0);
    }

    private static ClienteRef clienteInactivo() {
        return ClienteRef.crear(UUID.randomUUID(), "Juan Osorio", false, T0);
    }

    static Cuenta cuenta(String saldoInicial, ClienteRef cliente) {
        return Cuenta.abrir("478758", TipoCuenta.AHORROS, new BigDecimal(saldoInicial), true, cliente);
    }

    @Nested
    @DisplayName("Apertura y estado")
    class AperturaYEstado {

        @Test
        @DisplayName("Al abrir, el saldo disponible es igual al saldo inicial")
        void abrir_cuandoDatosValidos_debeIgualarSaldoDisponibleAlInicial() {
            // When
            Cuenta cuenta = cuenta("2000", clienteActivo());

            // Then
            assertThat(cuenta.getNumeroCuenta()).isEqualTo("478758");
            assertThat(cuenta.getTipo()).isEqualTo(TipoCuenta.AHORROS);
            assertThat(cuenta.getSaldoInicial()).isEqualByComparingTo("2000.00");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("2000.00");
            assertThat(cuenta.isEstado()).isTrue();
        }

        @Test
        @DisplayName("Acepta saldo inicial 0")
        void abrir_cuandoSaldoInicialCero_debeCrearCuenta() {
            assertThat(cuenta("0", clienteActivo()).getSaldoDisponible()).isEqualByComparingTo("0");
        }

        @ParameterizedTest(name = "número \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"12ab56", "12345", "123456789012345678901"})
        @DisplayName("Rechaza números de cuenta que no tienen 6 a 20 dígitos")
        void abrir_cuandoNumeroInvalido_debeLanzarDomainValidationException(String numero) {
            assertThatThrownBy(() -> Cuenta.abrir(numero, TipoCuenta.AHORROS, BigDecimal.TEN, true, clienteActivo()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("número de cuenta");
        }

        @ParameterizedTest(name = "saldo inicial {0}")
        @ValueSource(strings = {"-1", "-0.01", "10.123"})
        @DisplayName("Rechaza saldos iniciales negativos o con más de 2 decimales")
        void abrir_cuandoSaldoInicialInvalido_debeLanzarDomainValidationException(String saldo) {
            assertThatThrownBy(() -> cuenta(saldo, clienteActivo()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("saldo inicial");
        }

        @Test
        @DisplayName("Rechaza tipo de cuenta nulo")
        void abrir_cuandoTipoNulo_debeLanzarDomainValidationException() {
            assertThatThrownBy(() -> Cuenta.abrir("478758", null, BigDecimal.TEN, true, clienteActivo()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("tipo");
        }

        @Test
        @DisplayName("No se puede abrir una cuenta a un cliente inactivo")
        void abrir_cuandoClienteInactivo_debeLanzarClienteNoDisponibleException() {
            assertThatThrownBy(() -> cuenta("100", clienteInactivo()))
                    .isInstanceOf(ClienteNoDisponibleException.class)
                    .hasMessage("Cliente no disponible");
        }

        @Test
        @DisplayName("desactivar y activar cambian el estado")
        void desactivarYActivar_cuandoClienteActivo_debenCambiarEstado() {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());

            // When
            cuenta.desactivar();

            // Then
            assertThat(cuenta.isEstado()).isFalse();

            // When
            cuenta.activar();

            // Then
            assertThat(cuenta.isEstado()).isTrue();
        }

        @Test
        @DisplayName("No se puede activar una cuenta si el cliente está inactivo")
        void activar_cuandoClienteInactivo_debeLanzarClienteNoDisponibleException() {
            // Given
            ClienteRef cliente = clienteActivo();
            Cuenta cuenta = cuenta("100", cliente);
            cuenta.desactivar();
            cliente.aplicar("Jose Lema", false, T0.plusSeconds(1));

            // When / Then
            assertThatThrownBy(cuenta::activar).isInstanceOf(ClienteNoDisponibleException.class);
            assertThat(cuenta.isEstado()).isFalse();
        }

        @Test
        @DisplayName("cambiarTipo actualiza el tipo y rechaza nulos")
        void cambiarTipo_cuandoTipoValido_debeCambiarlo() {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());

            // When
            cuenta.cambiarTipo(TipoCuenta.CORRIENTE);

            // Then
            assertThat(cuenta.getTipo()).isEqualTo(TipoCuenta.CORRIENTE);
            assertThatThrownBy(() -> cuenta.cambiarTipo(null)).isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("Registro de movimientos (F2, F3)")
    class RegistroDeMovimientos {

        @Test
        @DisplayName("Un retiro de 575 sobre 2000 deja saldo 1425 (caso de uso 4)")
        void registrarMovimiento_cuandoRetiroConSaldo_debeDescontarYRegistrarSaldo() {
            // Given
            Cuenta cuenta = cuenta("2000", clienteActivo());

            // When
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("-575"), T0);

            // Then
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("1425");
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.RETIRO);
            assertThat(movimiento.getValor()).isEqualByComparingTo("-575");
            assertThat(movimiento.getSaldo()).isEqualByComparingTo("1425");
            assertThat(movimiento.getFecha()).isEqualTo(T0);
            assertThat(movimiento.getMovimientoId()).isNotNull();
            assertThat(movimiento.getCuenta()).isSameAs(cuenta);
        }

        @Test
        @DisplayName("Un depósito de 600 sobre 100 deja saldo 700 (caso de uso 4)")
        void registrarMovimiento_cuandoDeposito_debeSumarYSerDeposito() {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());

            // When
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("600"), T0);

            // Then
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.DEPOSITO);
            assertThat(movimiento.getSaldo()).isEqualByComparingTo("700");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("700");
        }

        @Test
        @DisplayName("Retirar exactamente todo el saldo está permitido (540 - 540 = 0)")
        void registrarMovimiento_cuandoRetiroDejaSaldoCero_debePermitirlo() {
            // Given
            Cuenta cuenta = cuenta("540", clienteActivo());

            // When
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("-540"), T0);

            // Then
            assertThat(movimiento.getSaldo()).isEqualByComparingTo("0");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Un retiro sin saldo lanza \"Saldo no disponible\" y no altera el saldo (F3)")
        void registrarMovimiento_cuandoSaldoInsuficiente_debeLanzarSaldoNoDisponible() {
            // Given
            Cuenta cuenta = cuenta("0", clienteActivo());

            // When / Then
            assertThatThrownBy(() -> cuenta.registrarMovimiento(new BigDecimal("-1"), T0))
                    .isInstanceOf(SaldoNoDisponibleException.class)
                    .hasMessage("Saldo no disponible");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Una cuenta inactiva no acepta movimientos")
        void registrarMovimiento_cuandoCuentaInactiva_debeLanzarCuentaInactiva() {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());
            cuenta.desactivar();

            // When / Then
            assertThatThrownBy(() -> cuenta.registrarMovimiento(BigDecimal.TEN, T0))
                    .isInstanceOf(CuentaInactivaException.class);
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("Si el cliente está inactivo, la cuenta rechaza movimientos (FR-017a)")
        void registrarMovimiento_cuandoClienteInactivo_debeLanzarClienteNoDisponible() {
            // Given
            ClienteRef cliente = clienteActivo();
            Cuenta cuenta = cuenta("100", cliente);
            cliente.aplicar("Jose Lema", false, T0.plusSeconds(1));

            // When / Then
            assertThatThrownBy(() -> cuenta.registrarMovimiento(BigDecimal.TEN, T0))
                    .isInstanceOf(ClienteNoDisponibleException.class);
            assertThat(cuenta.isEstado()).isTrue();
        }

        @ParameterizedTest(name = "valor {0}")
        @ValueSource(strings = {"0", "0.00", "10.123"})
        @DisplayName("Rechaza valor 0 o con más de 2 decimales")
        void registrarMovimiento_cuandoValorInvalido_debeLanzarDomainValidationException(String valor) {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());

            // When / Then
            assertThatThrownBy(() -> cuenta.registrarMovimiento(new BigDecimal(valor), T0))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("valor");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("100");
        }
    }

    @Nested
    @DisplayName("Corrección de movimientos con recálculo en cadena")
    class CorreccionDeMovimientos {

        @Test
        @DisplayName("Corregir -575 a -500 recalcula los saldos posteriores y el disponible")
        void corregirMovimiento_cuandoValido_debeRecalcularSaldosEnCadena() {
            // Given: 2000 → -575 (1425) → +100 (1525)
            Cuenta cuenta = cuenta("2000", clienteActivo());
            Movimiento retiro = cuenta.registrarMovimiento(new BigDecimal("-575"), T0);
            Movimiento deposito = cuenta.registrarMovimiento(new BigDecimal("100"), T0.plusSeconds(60));

            // When
            cuenta.corregirMovimiento(retiro, new BigDecimal("-500"), List.of(deposito));

            // Then
            assertThat(retiro.getValor()).isEqualByComparingTo("-500");
            assertThat(retiro.getSaldo()).isEqualByComparingTo("1500");
            assertThat(deposito.getSaldo()).isEqualByComparingTo("1600");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("1600");
            assertThat(retiro.getFecha()).isEqualTo(T0);
        }

        @Test
        @DisplayName("Si un saldo intermedio quedaría negativo, rechaza sin modificar nada")
        void corregirMovimiento_cuandoSaldoIntermedioNegativo_debeLanzarSaldoNoDisponibleSinCambios() {
            // Given: 2000 → -575 (1425) → -1425 (0)
            Cuenta cuenta = cuenta("2000", clienteActivo());
            Movimiento primero = cuenta.registrarMovimiento(new BigDecimal("-575"), T0);
            Movimiento segundo = cuenta.registrarMovimiento(new BigDecimal("-1425"), T0.plusSeconds(60));

            // When / Then: -600 dejaría el segundo en -25
            assertThatThrownBy(() -> cuenta.corregirMovimiento(primero, new BigDecimal("-600"), List.of(segundo)))
                    .isInstanceOf(SaldoNoDisponibleException.class)
                    .hasMessage("Saldo no disponible");
            assertThat(primero.getValor()).isEqualByComparingTo("-575");
            assertThat(primero.getSaldo()).isEqualByComparingTo("1425");
            assertThat(segundo.getSaldo()).isEqualByComparingTo("0");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Cambiar el signo cambia el tipo del movimiento")
        void corregirMovimiento_cuandoCambiaSigno_debeCambiarTipo() {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("-50"), T0);

            // When
            cuenta.corregirMovimiento(movimiento, new BigDecimal("50"), List.of());

            // Then
            assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.DEPOSITO);
            assertThat(movimiento.getSaldo()).isEqualByComparingTo("150");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("150");
        }

        @Test
        @DisplayName("Una cuenta inactiva no admite correcciones")
        void corregirMovimiento_cuandoCuentaInactiva_debeLanzarCuentaInactiva() {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("-50"), T0);
            cuenta.desactivar();

            // When / Then
            assertThatThrownBy(() -> cuenta.corregirMovimiento(movimiento, new BigDecimal("-10"), List.of()))
                    .isInstanceOf(CuentaInactivaException.class);
        }

        @Test
        @DisplayName("Si el cliente está inactivo, no admite correcciones")
        void corregirMovimiento_cuandoClienteInactivo_debeLanzarClienteNoDisponible() {
            // Given
            ClienteRef cliente = clienteActivo();
            Cuenta cuenta = cuenta("100", cliente);
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("-50"), T0);
            cliente.aplicar("Jose Lema", false, T0.plusSeconds(1));

            // When / Then
            assertThatThrownBy(() -> cuenta.corregirMovimiento(movimiento, new BigDecimal("-10"), List.of()))
                    .isInstanceOf(ClienteNoDisponibleException.class);
        }

        @Test
        @DisplayName("Rechaza corregir a valor 0")
        void corregirMovimiento_cuandoValorCero_debeLanzarDomainValidationException() {
            // Given
            Cuenta cuenta = cuenta("100", clienteActivo());
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("-50"), T0);

            // When / Then
            assertThatThrownBy(() -> cuenta.corregirMovimiento(movimiento, BigDecimal.ZERO, List.of()))
                    .isInstanceOf(DomainValidationException.class);
            assertThat(movimiento.getValor()).isEqualByComparingTo("-50");
        }
    }
}

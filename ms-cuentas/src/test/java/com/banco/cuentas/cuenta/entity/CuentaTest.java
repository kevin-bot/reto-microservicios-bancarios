package com.banco.cuentas.cuenta.entity;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.cuenta.exception.ClienteNoDisponibleException;
import com.banco.cuentas.shared.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
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
}

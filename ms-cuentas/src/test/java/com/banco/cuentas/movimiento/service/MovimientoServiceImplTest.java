package com.banco.cuentas.movimiento.service;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.entity.Movimiento;
import com.banco.cuentas.cuenta.entity.TipoCuenta;
import com.banco.cuentas.cuenta.entity.TipoMovimiento;
import com.banco.cuentas.cuenta.exception.CuentaNoEncontradaException;
import com.banco.cuentas.cuenta.exception.SaldoNoDisponibleException;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import com.banco.cuentas.movimiento.dto.MovimientoCreateRequest;
import com.banco.cuentas.movimiento.dto.MovimientoPatchRequest;
import com.banco.cuentas.movimiento.dto.MovimientoResponse;
import com.banco.cuentas.movimiento.dto.MovimientoUpdateRequest;
import com.banco.cuentas.movimiento.exception.MovimientoNoEncontradoException;
import com.banco.cuentas.movimiento.mapper.MovimientoMapper;
import com.banco.cuentas.movimiento.repository.MovimientoRepository;
import com.banco.cuentas.shared.config.BancoProperties;
import com.banco.cuentas.shared.dto.PaginaResponse;
import com.banco.cuentas.shared.fecha.RangoFechas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de movimientos")
class MovimientoServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T15:00:00Z");

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private MovimientoRepository movimientoRepository;

    private MovimientoServiceImpl movimientoService;

    @BeforeEach
    void crearServicio() {
        movimientoService = new MovimientoServiceImpl(cuentaRepository, movimientoRepository,
                Mappers.getMapper(MovimientoMapper.class), Clock.fixed(AHORA, ZoneOffset.UTC),
                new BancoProperties("America/Guayaquil"));
    }

    private static Cuenta cuenta478758() {
        ClienteRef jose = ClienteRef.crear(UUID.randomUUID(), "Jose Lema", true, AHORA.minusSeconds(3600));
        return Cuenta.abrir("478758", TipoCuenta.AHORROS, new BigDecimal("2000"), true, jose);
    }

    @Nested
    @DisplayName("registrar")
    class Registrar {

        @Test
        @DisplayName("Bloquea la cuenta, aplica el movimiento con la fecha del sistema y lo guarda")
        void registrar_cuandoCuentaExiste_debeAplicarYGuardarConFechaDelSistema() {
            // Given
            Cuenta cuenta = cuenta478758();
            when(cuentaRepository.buscarParaActualizar("478758")).thenReturn(Optional.of(cuenta));
            when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

            // When
            MovimientoResponse respuesta = movimientoService.registrar(
                    new MovimientoCreateRequest("478758", new BigDecimal("-575")));

            // Then
            assertThat(respuesta.tipo()).isEqualTo(TipoMovimiento.RETIRO);
            assertThat(respuesta.saldo()).isEqualByComparingTo("1425");
            assertThat(respuesta.fecha()).isEqualTo(AHORA);
            assertThat(respuesta.numeroCuenta()).isEqualTo("478758");
            assertThat(respuesta.nombreCliente()).isEqualTo("Jose Lema");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("1425");
        }

        @Test
        @DisplayName("Lanza CuentaNoEncontradaException si la cuenta no existe")
        void registrar_cuandoCuentaNoExiste_debeLanzarCuentaNoEncontrada() {
            // Given
            when(cuentaRepository.buscarParaActualizar("999999")).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> movimientoService.registrar(
                    new MovimientoCreateRequest("999999", BigDecimal.TEN)))
                    .isInstanceOf(CuentaNoEncontradaException.class);
        }

        @Test
        @DisplayName("Sin saldo lanza \"Saldo no disponible\" y no guarda nada")
        void registrar_cuandoSaldoInsuficiente_noDebeGuardar() {
            // Given
            when(cuentaRepository.buscarParaActualizar("478758")).thenReturn(Optional.of(cuenta478758()));

            // When / Then
            assertThatThrownBy(() -> movimientoService.registrar(
                    new MovimientoCreateRequest("478758", new BigDecimal("-2000.01"))))
                    .isInstanceOf(SaldoNoDisponibleException.class);
            verify(movimientoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("consultas")
    class Consultas {

        @Test
        @DisplayName("obtener lanza MovimientoNoEncontradoException si no existe")
        void obtener_cuandoNoExiste_debeLanzarMovimientoNoEncontrado() {
            // Given
            UUID movimientoId = UUID.randomUUID();
            when(movimientoRepository.findByMovimientoId(movimientoId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> movimientoService.obtener(movimientoId))
                    .isInstanceOf(MovimientoNoEncontradoException.class);
        }

        @Test
        @DisplayName("listar aplica los filtros y devuelve filas planas")
        @SuppressWarnings("unchecked")
        void listar_cuandoHayFiltros_debeDevolverPagina() {
            // Given
            Cuenta cuenta = cuenta478758();
            Movimiento movimiento = cuenta.registrarMovimiento(new BigDecimal("-575"), AHORA);
            PageRequest pagina = PageRequest.of(0, 20);
            when(movimientoRepository.findAll(any(Specification.class), eq(pagina)))
                    .thenReturn(new PageImpl<>(List.of(movimiento), pagina, 1));

            // When
            PaginaResponse<MovimientoResponse> resultado = movimientoService.listar(UUID.randomUUID(), "478758",
                    RangoFechas.parse("2026-10-29,2026-10-29"), pagina);

            // Then
            assertThat(resultado.totalElementos()).isEqualTo(1);
            MovimientoResponse fila = resultado.contenido().getFirst();
            assertThat(fila.tipoCuenta()).isEqualTo(TipoCuenta.AHORROS);
            assertThat(fila.saldoInicial()).isEqualByComparingTo("2000");
            assertThat(fila.estadoCuenta()).isTrue();
            assertThat(fila.valor()).isEqualByComparingTo("-575");
        }
    }

    @Nested
    @DisplayName("corregir")
    class Corregir {

        @Test
        @DisplayName("Bloquea la cuenta antes de cargar el movimiento y recalcula en cadena")
        void corregir_cuandoMovimientoExiste_debeBloquearAntesDeCargarYRecalcular() {
            // Given: 2000 → -575 (1425) → +100 (1525)
            Cuenta cuenta = cuenta478758();
            Movimiento retiro = cuenta.registrarMovimiento(new BigDecimal("-575"), AHORA);
            Movimiento deposito = cuenta.registrarMovimiento(new BigDecimal("100"), AHORA.plusSeconds(60));
            UUID movimientoId = retiro.getMovimientoId();
            when(movimientoRepository.buscarNumeroCuenta(movimientoId)).thenReturn(Optional.of("478758"));
            when(cuentaRepository.buscarParaActualizar("478758")).thenReturn(Optional.of(cuenta));
            when(movimientoRepository.findByMovimientoId(movimientoId)).thenReturn(Optional.of(retiro));
            when(movimientoRepository.findPosteriores(cuenta, retiro.getFecha(), retiro.getId()))
                    .thenReturn(List.of(deposito));

            // When
            MovimientoResponse respuesta = movimientoService.corregir(movimientoId,
                    new MovimientoUpdateRequest(new BigDecimal("-500")));

            // Then: orden obligatorio (research R4)
            InOrder orden = inOrder(movimientoRepository, cuentaRepository);
            orden.verify(movimientoRepository).buscarNumeroCuenta(movimientoId);
            orden.verify(cuentaRepository).buscarParaActualizar("478758");
            orden.verify(movimientoRepository).findByMovimientoId(movimientoId);
            orden.verify(movimientoRepository).findPosteriores(cuenta, retiro.getFecha(), retiro.getId());

            assertThat(respuesta.saldo()).isEqualByComparingTo("1500");
            assertThat(deposito.getSaldo()).isEqualByComparingTo("1600");
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("1600");
        }

        @Test
        @DisplayName("PATCH usa el mismo flujo de corrección")
        void corregirParcial_cuandoValor_debeCorregir() {
            // Given
            Cuenta cuenta = cuenta478758();
            Movimiento retiro = cuenta.registrarMovimiento(new BigDecimal("-575"), AHORA);
            UUID movimientoId = retiro.getMovimientoId();
            when(movimientoRepository.buscarNumeroCuenta(movimientoId)).thenReturn(Optional.of("478758"));
            when(cuentaRepository.buscarParaActualizar("478758")).thenReturn(Optional.of(cuenta));
            when(movimientoRepository.findByMovimientoId(movimientoId)).thenReturn(Optional.of(retiro));
            when(movimientoRepository.findPosteriores(any(), any(), any())).thenReturn(List.of());

            // When
            MovimientoResponse respuesta = movimientoService.corregirParcial(movimientoId,
                    new MovimientoPatchRequest(new BigDecimal("-75")));

            // Then
            assertThat(respuesta.saldo()).isEqualByComparingTo("1925");
        }

        @Test
        @DisplayName("Un movimiento inexistente lanza 404 sin bloquear ninguna cuenta")
        void corregir_cuandoMovimientoNoExiste_debeLanzarMovimientoNoEncontrado() {
            // Given
            UUID movimientoId = UUID.randomUUID();
            when(movimientoRepository.buscarNumeroCuenta(movimientoId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> movimientoService.corregir(movimientoId,
                    new MovimientoUpdateRequest(BigDecimal.TEN)))
                    .isInstanceOf(MovimientoNoEncontradoException.class);
            verify(cuentaRepository, never()).buscarParaActualizar(any());
        }
    }
}

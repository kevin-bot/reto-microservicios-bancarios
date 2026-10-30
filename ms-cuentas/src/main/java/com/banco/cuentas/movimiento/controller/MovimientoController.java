package com.banco.cuentas.movimiento.controller;

import com.banco.cuentas.movimiento.dto.MovimientoCreateRequest;
import com.banco.cuentas.movimiento.dto.MovimientoPatchRequest;
import com.banco.cuentas.movimiento.dto.MovimientoResponse;
import com.banco.cuentas.movimiento.dto.MovimientoUpdateRequest;
import com.banco.cuentas.movimiento.service.MovimientoService;
import com.banco.cuentas.shared.dto.PaginaResponse;
import com.banco.cuentas.shared.fecha.RangoFechas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/movimientos")
@RequiredArgsConstructor
@Tag(name = "Movimientos", description = "Depósitos, retiros y correcciones")
public class MovimientoController {

    private final MovimientoService movimientoService;

    @PostMapping
    @Operation(summary = "Registrar un movimiento (positivo = depósito, negativo = retiro)")
    @ApiResponse(responseCode = "201", description = "Movimiento registrado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "404", description = "Cuenta no encontrada")
    @ApiResponse(responseCode = "422", description = "Saldo no disponible, cuenta inactiva o cliente no disponible")
    public ResponseEntity<MovimientoResponse> registrar(@Valid @RequestBody MovimientoCreateRequest request) {
        MovimientoResponse registrado = movimientoService.registrar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{movimientoId}")
                .buildAndExpand(registrado.movimientoId())
                .toUri();
        return ResponseEntity.created(location).body(registrado);
    }

    @GetMapping("/{movimientoId}")
    @Operation(summary = "Consultar un movimiento")
    @ApiResponse(responseCode = "200", description = "Movimiento encontrado")
    @ApiResponse(responseCode = "404", description = "Movimiento no encontrado")
    public MovimientoResponse obtener(@PathVariable UUID movimientoId) {
        return movimientoService.obtener(movimientoId);
    }

    @GetMapping
    @Operation(summary = "Listar movimientos por cliente, cuenta y/o rango de fechas (caso de uso 5)")
    @ApiResponse(responseCode = "200", description = "Página de movimientos (más recientes primero)")
    @ApiResponse(responseCode = "400", description = "Parámetro inválido")
    public PaginaResponse<MovimientoResponse> listar(
            @RequestParam(name = "cliente", required = false) UUID clienteId,
            @RequestParam(required = false) String numeroCuenta,
            @Parameter(description = "Rango AAAA-MM-DD,AAAA-MM-DD", example = "2022-02-01,2022-02-10")
            @RequestParam(name = "fecha", required = false) RangoFechas rango,
            @ParameterObject @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return movimientoService.listar(clienteId, numeroCuenta, rango, pageable);
    }

    @PutMapping("/{movimientoId}")
    @Operation(summary = "Corregir el valor de un movimiento (recalcula los saldos posteriores)")
    @ApiResponse(responseCode = "200", description = "Movimiento corregido")
    @ApiResponse(responseCode = "404", description = "Movimiento no encontrado")
    @ApiResponse(responseCode = "422", description = "Saldo no disponible, cuenta inactiva o cliente no disponible")
    public MovimientoResponse corregir(@PathVariable UUID movimientoId,
                                       @Valid @RequestBody MovimientoUpdateRequest request) {
        return movimientoService.corregir(movimientoId, request);
    }

    @PatchMapping("/{movimientoId}")
    @Operation(summary = "Corregir parcialmente un movimiento (único campo editable: valor)")
    @ApiResponse(responseCode = "200", description = "Movimiento corregido")
    @ApiResponse(responseCode = "400", description = "Cuerpo sin campos")
    @ApiResponse(responseCode = "404", description = "Movimiento no encontrado")
    @ApiResponse(responseCode = "422", description = "Saldo no disponible, cuenta inactiva o cliente no disponible")
    public MovimientoResponse corregirParcial(@PathVariable UUID movimientoId,
                                              @Valid @RequestBody MovimientoPatchRequest request) {
        return movimientoService.corregirParcial(movimientoId, request);
    }
}

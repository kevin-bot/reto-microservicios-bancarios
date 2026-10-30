package com.banco.cuentas.cuenta.controller;

import com.banco.cuentas.cuenta.dto.CuentaCreateRequest;
import com.banco.cuentas.cuenta.dto.CuentaPatchRequest;
import com.banco.cuentas.cuenta.dto.CuentaResponse;
import com.banco.cuentas.cuenta.dto.CuentaUpdateRequest;
import com.banco.cuentas.cuenta.service.CuentaService;
import com.banco.cuentas.shared.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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

/**
 * CRU de cuentas: el enunciado no pide eliminarlas (se desactivan), por eso no hay DELETE.
 */
@RestController
@RequestMapping("/cuentas")
@RequiredArgsConstructor
@Tag(name = "Cuentas", description = "Apertura y administración de cuentas")
public class CuentaController {

    private final CuentaService cuentaService;

    @PostMapping
    @Operation(summary = "Abrir una cuenta")
    @ApiResponse(responseCode = "201", description = "Cuenta creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "409", description = "Número de cuenta duplicado")
    @ApiResponse(responseCode = "422", description = "Cliente no disponible")
    public ResponseEntity<CuentaResponse> crear(@Valid @RequestBody CuentaCreateRequest request) {
        CuentaResponse creada = cuentaService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{numeroCuenta}")
                .buildAndExpand(creada.numeroCuenta())
                .toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping("/{numeroCuenta}")
    @Operation(summary = "Consultar una cuenta")
    @ApiResponse(responseCode = "200", description = "Cuenta encontrada")
    @ApiResponse(responseCode = "404", description = "Cuenta no encontrada")
    public CuentaResponse obtener(@PathVariable String numeroCuenta) {
        return cuentaService.obtener(numeroCuenta);
    }

    @GetMapping
    @Operation(summary = "Listar cuentas (paginado, filtro opcional por cliente)")
    @ApiResponse(responseCode = "200", description = "Página de cuentas")
    public PaginaResponse<CuentaResponse> listar(
            @RequestParam(required = false) UUID clienteId,
            @ParameterObject @PageableDefault(size = 20, sort = "numeroCuenta") Pageable pageable) {
        return cuentaService.listar(clienteId, pageable);
    }

    @PutMapping("/{numeroCuenta}")
    @Operation(summary = "Actualizar tipo y estado de una cuenta")
    @ApiResponse(responseCode = "200", description = "Cuenta actualizada")
    @ApiResponse(responseCode = "404", description = "Cuenta no encontrada")
    @ApiResponse(responseCode = "422", description = "Cliente no disponible al reactivar")
    public CuentaResponse actualizar(@PathVariable String numeroCuenta,
                                     @Valid @RequestBody CuentaUpdateRequest request) {
        return cuentaService.actualizar(numeroCuenta, request);
    }

    @PatchMapping("/{numeroCuenta}")
    @Operation(summary = "Actualizar parcialmente tipo o estado de una cuenta")
    @ApiResponse(responseCode = "200", description = "Cuenta actualizada")
    @ApiResponse(responseCode = "400", description = "Cuerpo sin campos")
    @ApiResponse(responseCode = "404", description = "Cuenta no encontrada")
    @ApiResponse(responseCode = "422", description = "Cliente no disponible al reactivar")
    public CuentaResponse actualizarParcial(@PathVariable String numeroCuenta,
                                            @Valid @RequestBody CuentaPatchRequest request) {
        return cuentaService.actualizarParcial(numeroCuenta, request);
    }
}

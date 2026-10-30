package com.banco.clientes.cliente.controller;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClientePatchRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.dto.ClienteUpdateRequest;
import com.banco.clientes.cliente.service.ClienteService;
import com.banco.clientes.shared.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Gestión de clientes")
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    @Operation(summary = "Registrar un cliente")
    @ApiResponse(responseCode = "201", description = "Cliente creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o solicitud mal formada")
    @ApiResponse(responseCode = "409", description = "Identificación duplicada")
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteCreateRequest request) {
        ClienteResponse creado = clienteService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{clienteId}")
                .buildAndExpand(creado.clienteId())
                .toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @GetMapping("/{clienteId}")
    @Operation(summary = "Consultar un cliente")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente o eliminado")
    public ClienteResponse obtener(@PathVariable UUID clienteId) {
        return clienteService.obtener(clienteId);
    }

    @GetMapping
    @Operation(summary = "Listar clientes activos e inactivos (paginado, filtros opcionales)")
    @ApiResponse(responseCode = "200", description = "Página de clientes")
    public PaginaResponse<ClienteResponse> listar(
            @RequestParam(required = false) Boolean estado,
            @RequestParam(required = false) String identificacion,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return clienteService.listar(estado, identificacion, pageable);
    }

    @PutMapping("/{clienteId}")
    @Operation(summary = "Reemplazar todos los datos editables de un cliente (sin contraseña)")
    @ApiResponse(responseCode = "200", description = "Cliente actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o campo no permitido (contraseña)")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente o eliminado")
    @ApiResponse(responseCode = "409", description = "Identificación duplicada")
    public ClienteResponse actualizar(@PathVariable UUID clienteId,
                                      @Valid @RequestBody ClienteUpdateRequest request) {
        return clienteService.actualizar(clienteId, request);
    }

    @PatchMapping("/{clienteId}")
    @Operation(summary = "Actualizar parcialmente un cliente (incluye cambio de contraseña y estado)")
    @ApiResponse(responseCode = "200", description = "Cliente actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o cuerpo sin campos")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente o eliminado")
    @ApiResponse(responseCode = "409", description = "Identificación duplicada")
    public ClienteResponse actualizarParcial(@PathVariable UUID clienteId,
                                             @Valid @RequestBody ClientePatchRequest request) {
        return clienteService.actualizarParcial(clienteId, request);
    }

    @DeleteMapping("/{clienteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar (lógicamente) un cliente")
    @ApiResponse(responseCode = "204", description = "Cliente eliminado")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente o ya eliminado")
    public void eliminar(@PathVariable UUID clienteId) {
        clienteService.eliminar(clienteId);
    }
}

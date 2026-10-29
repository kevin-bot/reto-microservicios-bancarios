package com.banco.clientes.cliente.controller;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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
}

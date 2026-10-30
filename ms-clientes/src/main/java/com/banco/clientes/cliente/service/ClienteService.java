package com.banco.clientes.cliente.service;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClientePatchRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.dto.ClienteUpdateRequest;
import com.banco.clientes.shared.dto.PaginaResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ClienteService {

    ClienteResponse crear(ClienteCreateRequest request);

    ClienteResponse obtener(UUID clienteId);

    PaginaResponse<ClienteResponse> listar(Boolean estado, String identificacion, Pageable pageable);

    ClienteResponse actualizar(UUID clienteId, ClienteUpdateRequest request);

    ClienteResponse actualizarParcial(UUID clienteId, ClientePatchRequest request);
}

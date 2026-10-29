package com.banco.clientes.cliente.service;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;

public interface ClienteService {

    ClienteResponse crear(ClienteCreateRequest request);
}

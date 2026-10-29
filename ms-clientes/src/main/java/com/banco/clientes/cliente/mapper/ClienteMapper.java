package com.banco.clientes.cliente.mapper;

import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.entity.Cliente;
import org.mapstruct.Mapper;

/**
 * Solo entidad → respuesta: la creación y modificación de entidades pasa por sus métodos de
 * dominio, que protegen las invariantes (research R11).
 */
@Mapper
public interface ClienteMapper {

    ClienteResponse toResponse(Cliente cliente);
}

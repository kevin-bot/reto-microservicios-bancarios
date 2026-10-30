package com.banco.clientes.cliente.service;

import com.banco.clientes.cliente.dto.ClienteCreateRequest;
import com.banco.clientes.cliente.dto.ClienteResponse;
import com.banco.clientes.cliente.entity.Cliente;
import com.banco.clientes.cliente.event.ClienteEvento;
import com.banco.clientes.cliente.event.ClienteEventoDominio;
import com.banco.clientes.cliente.event.TipoEventoCliente;
import com.banco.clientes.cliente.exception.ClienteNoEncontradoException;
import com.banco.clientes.cliente.exception.IdentificacionDuplicadaException;
import com.banco.clientes.cliente.mapper.ClienteMapper;
import com.banco.clientes.cliente.repository.ClienteRepository;
import com.banco.clientes.cliente.repository.ClienteSpecifications;
import com.banco.clientes.shared.dto.PaginaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final ClienteMapper clienteMapper;

    @Override
    @Transactional
    public ClienteResponse crear(ClienteCreateRequest request) {
        if (clienteRepository.existsByIdentificacion(request.identificacion())) {
            throw new IdentificacionDuplicadaException(request.identificacion());
        }
        Cliente cliente = Cliente.crear(request.nombre(), request.genero(), request.edad(),
                request.identificacion(), request.direccion(), request.telefono(),
                passwordEncoder.encode(request.contrasena()), request.estadoOPorDefecto());

        Cliente guardado = clienteRepository.save(cliente);
        publicarEvento(guardado, TipoEventoCliente.CLIENTE_CREADO);
        log.info("Cliente creado (clienteId={})", guardado.getClienteId());
        return clienteMapper.toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtener(UUID clienteId) {
        return clienteMapper.toResponse(buscarVigente(clienteId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> listar(Boolean estado, String identificacion, Pageable pageable) {
        Specification<Cliente> filtro = ClienteSpecifications.noEliminado()
                .and(ClienteSpecifications.conEstado(estado))
                .and(ClienteSpecifications.conIdentificacion(identificacion));
        return PaginaResponse.de(clienteRepository.findAll(filtro, pageable).map(clienteMapper::toResponse));
    }

    private Cliente buscarVigente(UUID clienteId) {
        return clienteRepository.findByClienteIdAndEliminadoFalse(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException(clienteId));
    }

    private void publicarEvento(Cliente cliente, TipoEventoCliente tipo) {
        eventPublisher.publishEvent(new ClienteEventoDominio(ClienteEvento.desde(cliente, tipo)));
    }
}

package com.banco.cuentas.clienteref.service;

import com.banco.cuentas.clienteref.entity.ClienteRef;
import com.banco.cuentas.clienteref.entity.EventoProcesado;
import com.banco.cuentas.clienteref.event.ClienteEventoRecibido;
import com.banco.cuentas.clienteref.event.TipoEventoCliente;
import com.banco.cuentas.clienteref.repository.ClienteRefRepository;
import com.banco.cuentas.clienteref.repository.EventoProcesadoRepository;
import com.banco.cuentas.cuenta.entity.Cuenta;
import com.banco.cuentas.cuenta.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Mantiene la réplica de clientes. Todo ocurre en una transacción: si algo falla, no se registra
 * el evento y el reintento vuelve a procesarlo desde cero.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteEventoProcessorImpl implements ClienteEventoProcessor {

    private final ClienteRefRepository clienteRefRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final CuentaRepository cuentaRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void procesar(ClienteEventoRecibido evento) {
        if (eventoProcesadoRepository.existsById(evento.eventId())) {
            log.info("Evento {} descartado: ya fue procesado (clienteId={})", evento.eventId(), evento.clienteId());
            return;
        }
        boolean estado = evento.tipo() != TipoEventoCliente.CLIENTE_ELIMINADO && evento.estado();

        clienteRefRepository.findById(evento.clienteId()).ifPresentOrElse(
                cliente -> aplicar(cliente, evento, estado),
                () -> clienteRefRepository.save(ClienteRef.crear(
                        evento.clienteId(), evento.nombre(), estado, evento.fechaOcurrencia())));

        eventoProcesadoRepository.save(EventoProcesado.registrar(evento.eventId(), Instant.now(clock)));
        log.info("Evento {} aplicado (eventId={}, clienteId={})", evento.tipo(), evento.eventId(), evento.clienteId());
    }

    private void aplicar(ClienteRef cliente, ClienteEventoRecibido evento, boolean estado) {
        if (!cliente.aplicar(evento.nombre(), estado, evento.fechaOcurrencia())) {
            log.info("Evento {} descartado: llegó fuera de orden (clienteId={})", evento.eventId(), evento.clienteId());
            return;
        }
        if (evento.tipo() == TipoEventoCliente.CLIENTE_ELIMINADO) {
            cuentaRepository.findByClienteClienteId(evento.clienteId()).forEach(Cuenta::desactivar);
        }
    }
}

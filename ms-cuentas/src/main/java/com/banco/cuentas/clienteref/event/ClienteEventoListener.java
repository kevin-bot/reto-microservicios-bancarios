package com.banco.cuentas.clienteref.event;

import com.banco.cuentas.clienteref.service.ClienteEventoProcessor;
import com.banco.cuentas.shared.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada de mensajería: solo delega. Reintentos y DLQ los gestiona la
 * configuración del listener (RabbitConfig y application.yml).
 */
@Component
@RequiredArgsConstructor
public class ClienteEventoListener {

    private final ClienteEventoProcessor clienteEventoProcessor;

    @RabbitListener(queues = RabbitConfig.COLA_CLIENTES)
    public void recibir(ClienteEventoRecibido evento) {
        clienteEventoProcessor.procesar(evento);
    }
}

package com.banco.cuentas.clienteref.service;

import com.banco.cuentas.clienteref.event.ClienteEventoRecibido;

public interface ClienteEventoProcessor {

    void procesar(ClienteEventoRecibido evento);
}

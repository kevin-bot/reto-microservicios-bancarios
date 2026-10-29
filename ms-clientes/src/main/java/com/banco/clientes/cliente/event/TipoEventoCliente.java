package com.banco.clientes.cliente.event;

public enum TipoEventoCliente {
    CLIENTE_CREADO("cliente.creado"),
    CLIENTE_ACTUALIZADO("cliente.actualizado"),
    CLIENTE_ELIMINADO("cliente.eliminado");

    private final String routingKey;

    TipoEventoCliente(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}

package com.banco.clientes.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El productor solo declara el exchange. Colas, bindings, DLQ y reintentos son responsabilidad
 * de cada consumidor (constitución, Principio IV).
 */
@Configuration
public class RabbitConfig {

    public static final String CLIENTES_EXCHANGE = "clientes.exchange";

    @Bean
    public TopicExchange clientesExchange() {
        return new TopicExchange(CLIENTES_EXCHANGE, true, false);
    }

    /** Spring Boot asigna este converter al RabbitTemplate autoconfigurado. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}

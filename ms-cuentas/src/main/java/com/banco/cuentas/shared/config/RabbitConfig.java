package com.banco.cuentas.shared.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Topología del consumidor (contracts/eventos-consumidos.md): cola durable enlazada al exchange
 * de clientes, con dead letter exchange y DLQ para los mensajes que fallan definitivamente.
 */
@Slf4j
@Configuration
public class RabbitConfig {

    public static final String CLIENTES_EXCHANGE = "clientes.exchange";
    public static final String COLA_CLIENTES = "cuentas.clientes.queue";
    public static final String DLX_CLIENTES = "cuentas.clientes.dlx";
    public static final String DLQ_CLIENTES = "cuentas.clientes.dlq";
    private static final String EVENTOS_DE_CLIENTE = "cliente.*";
    private static final int MAXIMO_CUERPO_EN_LOG = 300;

    /** Se declara también aquí (idempotente) para que ms-cuentas pueda arrancar antes que el productor. */
    @Bean
    public TopicExchange clientesExchange() {
        return new TopicExchange(CLIENTES_EXCHANGE, true, false);
    }

    @Bean
    public Queue colaClientes() {
        return QueueBuilder.durable(COLA_CLIENTES)
                .deadLetterExchange(DLX_CLIENTES)
                .deadLetterRoutingKey(COLA_CLIENTES)
                .build();
    }

    @Bean
    public Binding bindingClientes(Queue colaClientes, TopicExchange clientesExchange) {
        return BindingBuilder.bind(colaClientes).to(clientesExchange).with(EVENTOS_DE_CLIENTE);
    }

    @Bean
    public DirectExchange dlxClientes() {
        return new DirectExchange(DLX_CLIENTES, true, false);
    }

    @Bean
    public Queue dlqClientes() {
        return QueueBuilder.durable(DLQ_CLIENTES).build();
    }

    @Bean
    public Binding bindingDlqClientes(Queue dlqClientes, DirectExchange dlxClientes) {
        return BindingBuilder.bind(dlqClientes).to(dlxClientes).with(COLA_CLIENTES);
    }

    /**
     * Ignora la cabecera de tipo del productor (su clase no existe aquí) y tolera campos nuevos en
     * el evento, que son cambios compatibles. La API REST sigue siendo estricta.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        ObjectMapper tolerante = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(tolerante);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    /**
     * Un mensaje que no se puede convertir fallará siempre: no se reintenta y va directo a la DLQ.
     * El resto de errores mantiene los intentos configurados en {@code spring.rabbitmq.listener}.
     */
    @Bean
    public RabbitRetryTemplateCustomizer sinReintentarErroresDeConversion(RabbitProperties rabbitProperties) {
        int intentos = rabbitProperties.getListener().getSimple().getRetry().getMaxAttempts();
        return (destino, retryTemplate) -> {
            if (destino == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                retryTemplate.setRetryPolicy(new SimpleRetryPolicy(intentos,
                        Map.of(MessageConversionException.class, false), true, true));
            }
        };
    }

    /** Rechaza sin reencolar (RabbitMQ lo envía a la DLQ) y lo deja registrado en el log. */
    @Bean
    public MessageRecoverer enviarADlq() {
        return new RejectAndDontRequeueRecoverer() {
            @Override
            public void recover(Message message, Throwable causa) {
                log.error("Mensaje enviado a {} tras agotar los intentos: {}", DLQ_CLIENTES, resumen(message), causa);
                super.recover(message, causa);
            }
        };
    }

    private static String resumen(Message message) {
        String cuerpo = new String(message.getBody(), StandardCharsets.UTF_8);
        return cuerpo.length() > MAXIMO_CUERPO_EN_LOG ? cuerpo.substring(0, MAXIMO_CUERPO_EN_LOG) + "…" : cuerpo;
    }
}

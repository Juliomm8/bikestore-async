package com.bikestore.asyncapp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {
    public static final String ORDER_EXCHANGE = "bikestore.orders";
    public static final String PAYMENT_QUEUE = "bikestore.payment";
    public static final String PAYMENT_KEY = "payment.requested";

    @Bean
    DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    Queue paymentQueue() {
        return new Queue(PAYMENT_QUEUE, true);
    }

    @Bean
    Binding paymentBinding(Queue paymentQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(paymentQueue).to(orderExchange).with(PAYMENT_KEY);
    }
}

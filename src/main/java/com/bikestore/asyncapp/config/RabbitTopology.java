package com.bikestore.asyncapp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {
    public static final String ORDER_EXCHANGE = "bikestore.orders";
    public static final String PAYMENT_QUEUE = "bikestore.payment";
    public static final String PAYMENT_KEY = "payment.requested";
    public static final String RETRY_EXCHANGE = "bikestore.retry";
    public static final String RETRY_QUEUE = "bikestore.payment.retry";
    public static final String RETRY_KEY = "payment.retry";
    public static final String DEAD_EXCHANGE = "bikestore.dead";
    public static final String DEAD_QUEUE = "bikestore.payment.dead";
    public static final String DEAD_KEY = "payment.failed";
    public static final String EVENT_EXCHANGE = "bikestore.events";
    public static final String PAID_QUEUE = "bikestore.email";
    public static final String PAID_KEY = "payment.paid";

    @Bean
    DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    Queue paymentQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_KEY)
                .build();
    }

    @Bean
    Binding paymentBinding(Queue paymentQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(paymentQueue).to(orderExchange).with(PAYMENT_KEY);
    }

    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    Queue retryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(750)
                .deadLetterExchange(ORDER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_KEY)
                .build();
    }

    @Bean
    Binding retryBinding(Queue retryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue).to(retryExchange).with(RETRY_KEY);
    }

    @Bean
    DirectExchange deadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    Binding deadBinding(Queue deadQueue, DirectExchange deadExchange) {
        return BindingBuilder.bind(deadQueue).to(deadExchange).with(DEAD_KEY);
    }

    @Bean
    DirectExchange eventExchange() {
        return new DirectExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    Queue paidQueue() {
        return QueueBuilder.durable(PAID_QUEUE).build();
    }

    @Bean
    Binding paidBinding(Queue paidQueue, DirectExchange eventExchange) {
        return BindingBuilder.bind(paidQueue).to(eventExchange).with(PAID_KEY);
    }
}

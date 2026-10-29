package com.bikestore.asyncapp.payment;

import java.io.IOException;
import java.time.Instant;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.messaging.ConfirmedPublisher;
import com.bikestore.asyncapp.messaging.OrderEventLogger;
import com.bikestore.asyncapp.model.FailedPayment;
import com.bikestore.asyncapp.model.OrderMessage;
import com.bikestore.asyncapp.model.PaymentResult;
import com.rabbitmq.client.Channel;

import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentWorker {
    private static final int MAX_ATTEMPTS = 4;

    private final ObjectMapper objectMapper;
    private final PaymentDecision decision;
    private final ConfirmedPublisher publisher;
    private final OrderEventLogger events;

    public PaymentWorker(ObjectMapper objectMapper, PaymentDecision decision,
            ConfirmedPublisher publisher, OrderEventLogger events) {
        this.objectMapper = objectMapper;
        this.decision = decision;
        this.publisher = publisher;
        this.events = events;
    }

    @RabbitListener(queues = RabbitTopology.PAYMENT_QUEUE)
    public void process(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        OrderMessage order;
        try {
            order = objectMapper.readValue(message.getBody(), OrderMessage.class);
        } catch (RuntimeException malformed) {
            events.error(message.getMessageProperties().getMessageId(), "PAYMENT_INVALID",
                    "Mensaje inválido enviado a la DLQ por RabbitMQ", malformed);
            channel.basicReject(tag, false);
            return;
        }

        int failedAttempts = failedAttempts(message);
        int attempt = failedAttempts + 1;
        if (attempt < 1 || attempt > MAX_ATTEMPTS) {
            events.info(order.pedidoId(), "PAYMENT_INVALID_ATTEMPT", "Cabecera fuera de rango: " + failedAttempts);
            channel.basicReject(tag, false);
            return;
        }

        events.info(order.pedidoId(), "PAYMENT_ATTEMPT", "Intento " + attempt + "/" + MAX_ATTEMPTS);
        if (decision.approved(order, attempt)) {
            onPaid(order, attempt);
            channel.basicAck(tag, false);
            return;
        }

        events.info(order.pedidoId(), "PAYMENT_FAILED", "Cobro simulado rechazado en intento " + attempt);
        if (attempt < MAX_ATTEMPTS) {
            publisher.publish(RabbitTopology.RETRY_EXCHANGE, RabbitTopology.RETRY_KEY,
                    order.pedidoId(), order, attempt);
            events.info(order.pedidoId(), "PAYMENT_RETRY_SCHEDULED", "Siguiente intento " + (attempt + 1));
        } else {
            FailedPayment failure = new FailedPayment(order, attempt,
                    "Cobro simulado rechazado en cuatro intentos", Instant.now());
            publisher.publish(RabbitTopology.DEAD_EXCHANGE, RabbitTopology.DEAD_KEY,
                    order.pedidoId(), failure, attempt);
            events.info(order.pedidoId(), "PAYMENT_DEAD_LETTER", "Agotados cuatro intentos; DLQ confirmada");
        }
        channel.basicAck(tag, false);
    }

    private int failedAttempts(Message message) {
        Object value = message.getMessageProperties().getHeaders().get("failedAttempts");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void onPaid(OrderMessage order, int attempt) {
        PaymentResult result = new PaymentResult(order.pedidoId(), order.customerEmail(),
                order.bikeModel(), "PAID", attempt, Instant.now());
        publisher.publish(RabbitTopology.EVENT_EXCHANGE, RabbitTopology.PAID_KEY,
                order.pedidoId(), result, null);
        events.info(order.pedidoId(), "PAYMENT_PAID", "Cobro simulado exitoso en intento " + attempt);
    }
}

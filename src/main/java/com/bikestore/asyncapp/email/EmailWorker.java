package com.bikestore.asyncapp.email;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.messaging.OrderEventLogger;
import com.bikestore.asyncapp.model.PaymentResult;
import com.rabbitmq.client.Channel;

import tools.jackson.databind.ObjectMapper;

@Component
public class EmailWorker {
    private final ObjectMapper mapper;
    private final EmailSender sender;
    private final OrderEventLogger events;

    public EmailWorker(ObjectMapper mapper, EmailSender sender, OrderEventLogger events) {
        this.mapper = mapper;
        this.sender = sender;
        this.events = events;
    }

    @RabbitListener(queues = RabbitTopology.PAID_QUEUE)
    public void process(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        PaymentResult payment;
        try {
            payment = mapper.readValue(message.getBody(), PaymentResult.class);
        } catch (RuntimeException malformed) {
            events.error(message.getMessageProperties().getMessageId(), "EMAIL_INVALID",
                    "Evento de pago inválido", malformed);
            channel.basicReject(tag, false);
            return;
        }

        if (!"PAID".equals(payment.paymentStatus())) {
            events.info(payment.pedidoId(), "EMAIL_SKIPPED",
                    "Estado de pago " + payment.paymentStatus());
            channel.basicAck(tag, false);
            return;
        }

        sender.sendConfirmation(payment);
        channel.basicAck(tag, false);
    }
}

package com.bikestore.asyncapp.messaging;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.model.OrderMessage;
import com.bikestore.asyncapp.model.OrderRequest;

import tools.jackson.databind.ObjectMapper;

@Service
public class OrderProducer {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OrderEventLogger events;

    public OrderProducer(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, OrderEventLogger events) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    public String submit(OrderRequest request) {
        String pedidoId = UUID.randomUUID().toString();
        OrderMessage order = new OrderMessage(pedidoId, request.customerEmail(), request.bikeModel(),
                request.quantity(), request.unitPrice(), Instant.now());
        publish(order);
        events.info(pedidoId, "ORDER_ACCEPTED", "Pedido JSON confirmado por RabbitMQ");
        return pedidoId;
    }

    private void publish(OrderMessage order) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(order.pedidoId());
        Message message = new Message(objectMapper.writeValueAsBytes(order), properties);
        CorrelationData correlation = new CorrelationData(order.pedidoId());
        rabbitTemplate.send(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.PAYMENT_KEY, message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ no confirmó el pedido " + order.pedidoId());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo publicar el pedido " + order.pedidoId(), exception);
        }
    }
}

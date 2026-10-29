package com.bikestore.asyncapp.messaging;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.model.OrderMessage;
import com.bikestore.asyncapp.model.OrderRequest;

@Service
public class OrderProducer {
    private final ConfirmedPublisher publisher;
    private final OrderEventLogger events;

    public OrderProducer(ConfirmedPublisher publisher, OrderEventLogger events) {
        this.publisher = publisher;
        this.events = events;
    }

    public String submit(OrderRequest request) {
        String pedidoId = UUID.randomUUID().toString();
        OrderMessage order = new OrderMessage(pedidoId, request.customerEmail(), request.bikeModel(),
                request.quantity(), request.unitPrice(), Instant.now());
        publisher.publish(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.PAYMENT_KEY,
                pedidoId, order, null);
        events.info(pedidoId, "ORDER_ACCEPTED", "Pedido JSON confirmado por RabbitMQ");
        return pedidoId;
    }

}

package com.bikestore.asyncapp.messaging;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.model.OrderItem;
import com.bikestore.asyncapp.model.OrderMessage;

@Service
public class OrderProducer {
    private final ConfirmedPublisher publisher;
    private final OrderEventLogger events;

    public OrderProducer(ConfirmedPublisher publisher, OrderEventLogger events) {
        this.publisher = publisher;
        this.events = events;
    }

    public String submit(String customerEmail, List<OrderItem> items) {
        String pedidoId = UUID.randomUUID().toString();
        BigDecimal total = items.stream().map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderMessage order = new OrderMessage(pedidoId, customerEmail, items, total, Instant.now());
        publisher.publish(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.PAYMENT_KEY,
                pedidoId, order, null);
        events.info(pedidoId, "ORDER_ACCEPTED", "Pedido JSON confirmado por RabbitMQ");
        return pedidoId;
    }

}

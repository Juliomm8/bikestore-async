package com.bikestore.asyncapp.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record OrderMessage(String pedidoId, String customerEmail, List<OrderItem> items,
        BigDecimal total, Instant createdAt) {
    public OrderMessage {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
        Objects.requireNonNull(total, "total");
    }
}

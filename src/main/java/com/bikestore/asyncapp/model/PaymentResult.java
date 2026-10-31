package com.bikestore.asyncapp.model;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

public record PaymentResult(String pedidoId, String customerEmail, List<OrderItem> items,
        BigDecimal total, String paymentStatus, int attempts, Instant paidAt) {
    public PaymentResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

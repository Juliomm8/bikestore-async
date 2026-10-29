package com.bikestore.asyncapp.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderMessage(String pedidoId, String customerEmail, String bikeModel,
        int quantity, BigDecimal unitPrice, Instant createdAt) {
}

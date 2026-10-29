package com.bikestore.asyncapp.model;

import java.time.Instant;

public record PaymentResult(String pedidoId, String customerEmail, String bikeModel,
        String paymentStatus, int attempts, Instant paidAt) {
}

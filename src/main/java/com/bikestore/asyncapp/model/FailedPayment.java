package com.bikestore.asyncapp.model;

import java.time.Instant;

public record FailedPayment(OrderMessage order, int attempts, String reason, Instant failedAt) {
}

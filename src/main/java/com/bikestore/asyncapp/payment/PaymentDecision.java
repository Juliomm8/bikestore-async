package com.bikestore.asyncapp.payment;

import com.bikestore.asyncapp.model.OrderMessage;

@FunctionalInterface
public interface PaymentDecision {
    boolean approved(OrderMessage order, int attempt);
}

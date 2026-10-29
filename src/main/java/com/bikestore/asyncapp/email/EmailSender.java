package com.bikestore.asyncapp.email;

import com.bikestore.asyncapp.model.PaymentResult;

@FunctionalInterface
public interface EmailSender {
    void sendConfirmation(PaymentResult payment);
}

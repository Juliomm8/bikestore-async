package com.bikestore.asyncapp.email;

import org.springframework.stereotype.Component;

import com.bikestore.asyncapp.messaging.OrderEventLogger;
import com.bikestore.asyncapp.model.PaymentResult;

@Component
public class SimulatedEmailSender implements EmailSender {
    private final OrderEventLogger events;

    public SimulatedEmailSender(OrderEventLogger events) {
        this.events = events;
    }

    @Override
    public void sendConfirmation(PaymentResult payment) {
        events.info(payment.pedidoId(), "EMAIL_SENT",
                "Confirmación simulada para " + payment.customerEmail());
    }
}

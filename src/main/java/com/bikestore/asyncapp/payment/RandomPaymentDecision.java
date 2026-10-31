package com.bikestore.asyncapp.payment;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import com.bikestore.asyncapp.model.OrderMessage;

@Component
@Profile("(integrated | payment) & !evaluation")
public class RandomPaymentDecision implements PaymentDecision {
    @Override
    public boolean approved(OrderMessage order, int attempt) {
        return ThreadLocalRandom.current().nextBoolean();
    }
}

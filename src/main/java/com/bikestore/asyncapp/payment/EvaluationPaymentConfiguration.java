package com.bikestore.asyncapp.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.bikestore.asyncapp.model.OrderMessage;

@Configuration(proxyBeanMethods = false)
@Profile("evaluation")
class EvaluationPaymentConfiguration {
    @Bean
    PaymentDecision evaluationPaymentDecision() {
        return new EvaluationPaymentDecision();
    }

    private static final class EvaluationPaymentDecision implements PaymentDecision {
        @Override
        public boolean approved(OrderMessage order, int attempt) {
            return !order.customerEmail().startsWith("demo-fail-");
        }
    }
}

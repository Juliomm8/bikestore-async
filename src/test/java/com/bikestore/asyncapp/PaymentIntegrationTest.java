package com.bikestore.asyncapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.messaging.ConfirmedPublisher;
import com.bikestore.asyncapp.model.FailedPayment;
import com.bikestore.asyncapp.model.OrderMessage;
import com.bikestore.asyncapp.model.PaymentResult;
import com.bikestore.asyncapp.payment.PaymentDecision;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(PaymentIntegrationTest.DeterministicConfig.class)
class PaymentIntegrationTest {
    @Autowired
    RabbitAdmin admin;

    @Autowired
    RabbitTemplate template;

    @Autowired
    ConfirmedPublisher publisher;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ScriptedDecision decision;

    @BeforeEach
    void clearQueues() {
        for (String queue : new String[] {RabbitTopology.PAYMENT_QUEUE, RabbitTopology.RETRY_QUEUE,
                RabbitTopology.PAID_QUEUE, RabbitTopology.DEAD_QUEUE}) {
            admin.purgeQueue(queue, true);
        }
        decision.calls.clear();
    }

    @Test
    void firstAttemptPublishesPaidEvent() {
        String id = submit("PASS1");
        PaymentResult paid = readPaid();
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.paymentStatus()).isEqualTo("PAID");
        assertThat(paid.attempts()).isEqualTo(1);
        assertThat(decision.count(id)).isEqualTo(1);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
    }

    @Test
    void failedAttemptIsRetriedAndThirdAttemptPays() {
        String id = submit("PASS3");
        PaymentResult paid = readPaid();
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.paymentStatus()).isEqualTo("PAID");
        assertThat(paid.attempts()).isEqualTo(3);
        assertThat(decision.count(id)).isEqualTo(3);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
    }

    @Test
    void fourFailuresReachDeadLetterQueue() {
        String id = submit("FAIL4");
        Message message = template.receive(RabbitTopology.DEAD_QUEUE, 10000);
        assertThat(message).isNotNull();
        FailedPayment failed = mapper.readValue(message.getBody(), FailedPayment.class);
        assertThat(failed.order().pedidoId()).isEqualTo(id);
        assertThat(failed.attempts()).isEqualTo(4);
        assertThat(failed.reason()).contains("cuatro intentos");
        assertThat(message.getMessageProperties().getHeaders().get("failedAttempts")).isEqualTo(4);
        assertThat(decision.count(id)).isEqualTo(4);
        assertThat(template.receive(RabbitTopology.PAID_QUEUE, 100)).isNull();
    }

    private String submit(String model) {
        String id = UUID.randomUUID().toString();
        OrderMessage order = new OrderMessage(id, "test@example.com", model,
                1, new BigDecimal("100.00"), Instant.now());
        publisher.publish(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.PAYMENT_KEY, id, order, null);
        return id;
    }

    private PaymentResult readPaid() {
        Message message = template.receive(RabbitTopology.PAID_QUEUE, 10000);
        assertThat(message).isNotNull();
        return mapper.readValue(message.getBody(), PaymentResult.class);
    }

    @TestConfiguration
    static class DeterministicConfig {
        @Bean
        @Primary
        ScriptedDecision scriptedDecision() {
            return new ScriptedDecision();
        }
    }

    static class ScriptedDecision implements PaymentDecision {
        final ConcurrentHashMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();

        @Override
        public boolean approved(OrderMessage order, int attempt) {
            calls.computeIfAbsent(order.pedidoId(), ignored -> new AtomicInteger()).incrementAndGet();
            return switch (order.bikeModel()) {
                case "PASS1" -> attempt == 1;
                case "PASS3" -> attempt == 3;
                default -> false;
            };
        }

        int count(String id) {
            return calls.get(id).get();
        }
    }
}

package com.bikestore.asyncapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.email.EmailSender;
import com.bikestore.asyncapp.messaging.ConfirmedPublisher;
import com.bikestore.asyncapp.messaging.OrderEventLogger;
import com.bikestore.asyncapp.model.FailedPayment;
import com.bikestore.asyncapp.model.OrderItem;
import com.bikestore.asyncapp.model.OrderMessage;
import com.bikestore.asyncapp.model.PaymentResult;
import com.bikestore.asyncapp.payment.PaymentDecision;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(PaymentIntegrationTest.DeterministicConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentIntegrationTest {
    private final String auditQueue = "bikestore.test.paid." + UUID.randomUUID();

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

    @Autowired
    RecordingEmailSender sender;

    @BeforeAll
    void bindAuditQueue() {
        admin.declareQueue(new Queue(auditQueue, true));
        admin.declareBinding(new Binding(auditQueue, Binding.DestinationType.QUEUE,
                RabbitTopology.EVENT_EXCHANGE, RabbitTopology.PAID_KEY, null));
    }

    @AfterAll
    void removeAuditQueue() {
        admin.deleteQueue(auditQueue);
    }

    @BeforeEach
    void clearQueues() {
        for (String queue : new String[] {RabbitTopology.PAYMENT_QUEUE, RabbitTopology.RETRY_QUEUE,
                RabbitTopology.PAID_QUEUE, RabbitTopology.DEAD_QUEUE}) {
            admin.purgeQueue(queue, true);
        }
        admin.purgeQueue(auditQueue, true);
        decision.calls.clear();
        sender.sent.clear();
    }

    @Test
    void firstAttemptPublishesPaidEvent() throws Exception {
        String id = submit("PASS1");
        PaymentResult paid = readPaid();
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.paymentStatus()).isEqualTo("PAID");
        assertThat(paid.attempts()).isEqualTo(1);
        assertThat(decision.count(id)).isEqualTo(1);
        awaitEmail(id);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
    }

    @Test
    void failedAttemptIsRetriedAndThirdAttemptPays() throws Exception {
        String id = submit("PASS3");
        PaymentResult paid = readPaid();
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.paymentStatus()).isEqualTo("PAID");
        assertThat(paid.attempts()).isEqualTo(3);
        assertThat(decision.count(id)).isEqualTo(3);
        awaitEmail(id);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
    }

    @Test
    void fourthAttemptCanPayWithoutEnteringDeadLetterQueue() throws Exception {
        String id = submit("PASS4");
        PaymentResult paid = readPaid();
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.attempts()).isEqualTo(4);
        assertThat(decision.count(id)).isEqualTo(4);
        awaitEmail(id);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
    }

    @Test
    void fourFailuresReachDeadLetterQueue() throws Exception {
        String id = submit("FAIL4");
        Message message = template.receive(RabbitTopology.DEAD_QUEUE, 10000);
        assertThat(message).isNotNull();
        FailedPayment failed = mapper.readValue(message.getBody(), FailedPayment.class);
        assertThat(failed.order().pedidoId()).isEqualTo(id);
        assertThat(failed.attempts()).isEqualTo(4);
        assertThat(failed.reason()).contains("cuatro intentos");
        assertThat(message.getMessageProperties().getHeaders().get("failedAttempts")).isEqualTo(4);
        assertThat(decision.count(id)).isEqualTo(4);
        assertThat(template.receive(auditQueue, 100)).isNull();
        Thread.sleep(200);
        assertThat(sender.sent).doesNotContain(id);
    }

    @Test
    void emailIgnoresPendingPayment() throws Exception {
        String id = UUID.randomUUID().toString();
        PaymentResult pending = new PaymentResult(id, "test@example.com",
                List.of(new OrderItem("Ruta Clara", 1, new BigDecimal("1050.00"))),
                new BigDecimal("1050.00"), "PENDING", 1, Instant.now());
        publisher.publish(RabbitTopology.EVENT_EXCHANGE, RabbitTopology.PAID_KEY,
                id, pending, null);
        assertThat(template.receive(auditQueue, 5000)).isNotNull();
        Thread.sleep(300);
        assertThat(sender.sent).doesNotContain(id);
    }

    private String submit(String model) {
        String id = UUID.randomUUID().toString();
        OrderMessage order = new OrderMessage(id, "test@example.com",
                List.of(new OrderItem(model, 1, new BigDecimal("100.00"))),
                new BigDecimal("100.00"), Instant.now());
        publisher.publish(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.PAYMENT_KEY, id, order, null);
        return id;
    }

    private PaymentResult readPaid() {
        Message message = template.receive(auditQueue, 10000);
        assertThat(message).isNotNull();
        return mapper.readValue(message.getBody(), PaymentResult.class);
    }

    private void awaitEmail(String id) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!sender.sent.contains(id) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(sender.sent).contains(id);
    }

    @TestConfiguration
    static class DeterministicConfig {
        @Bean
        @Primary
        ScriptedDecision scriptedDecision() {
            return new ScriptedDecision();
        }

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender(OrderEventLogger events) {
            return new RecordingEmailSender(events);
        }
    }

    static class ScriptedDecision implements PaymentDecision {
        final ConcurrentHashMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();

        @Override
        public boolean approved(OrderMessage order, int attempt) {
            calls.computeIfAbsent(order.pedidoId(), ignored -> new AtomicInteger()).incrementAndGet();
                return switch (order.items().get(0).bikeModel()) {
                case "PASS1" -> attempt == 1;
                case "PASS3" -> attempt == 3;
                case "PASS4" -> attempt == 4;
                default -> false;
            };
        }

        int count(String id) {
            return calls.get(id).get();
        }
    }

    static class RecordingEmailSender implements EmailSender {
        final ConcurrentLinkedQueue<String> sent = new ConcurrentLinkedQueue<>();
        final OrderEventLogger events;

        RecordingEmailSender(OrderEventLogger events) {
            this.events = events;
        }

        @Override
        public void sendConfirmation(PaymentResult payment) {
            sent.add(payment.pedidoId());
            events.info(payment.pedidoId(), "EMAIL_SENT", "Confirmación simulada en prueba");
        }
    }
}

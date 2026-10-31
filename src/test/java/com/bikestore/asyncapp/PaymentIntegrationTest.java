package com.bikestore.asyncapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Value;
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

@SpringBootTest(properties = "spring.rabbitmq.virtual-host=" + RabbitDemoVhost.NAME)
@Import(PaymentIntegrationTest.DeterministicConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentIntegrationTest {
    private final String auditQueue = "bikestore.test.paid." + UUID.randomUUID();
    private final String orderAuditQueue = "bikestore.test.orders." + UUID.randomUUID();

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

    @Value("${spring.rabbitmq.virtual-host:/}")
    String virtualHost;

    @BeforeAll
    void bindAuditQueue() {
        RabbitDemoVhost.require(virtualHost);
        admin.declareQueue(new Queue(auditQueue, true));
        admin.declareBinding(new Binding(auditQueue, Binding.DestinationType.QUEUE,
                RabbitTopology.EVENT_EXCHANGE, RabbitTopology.PAID_KEY, null));
        admin.declareQueue(new Queue(orderAuditQueue, true));
        admin.declareBinding(new Binding(orderAuditQueue, Binding.DestinationType.QUEUE,
                RabbitTopology.ORDER_EXCHANGE, RabbitTopology.PAYMENT_KEY, null));
    }

    @AfterAll
    void removeAuditQueue() {
        admin.deleteQueue(auditQueue);
        admin.deleteQueue(orderAuditQueue);
    }

    @BeforeEach
    void clearQueues() {
        RabbitDemoVhost.require(virtualHost);
        for (String queue : new String[] {RabbitTopology.PAYMENT_QUEUE, RabbitTopology.RETRY_QUEUE,
                RabbitTopology.PAID_QUEUE, RabbitTopology.DEAD_QUEUE}) {
            admin.purgeQueue(queue, true);
        }
        admin.purgeQueue(auditQueue, true);
        admin.purgeQueue(orderAuditQueue, true);
        decision.calls.clear();
        sender.sent.clear();
    }

    @Test
    void firstAttemptPublishesPaidEvent() throws Exception {
        String id = submit("pago-inmediato");
        PaymentResult paid = readPaid("pago-inmediato");
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.paymentStatus()).isEqualTo("PAID");
        assertThat(paid.attempts()).isEqualTo(1);
        assertThat(decision.count(id)).isEqualTo(1);
        awaitEmail(id);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
        recordScenario("pago-inmediato", id, "PAID", paid.attempts(), sender.sent.contains(id), false);
    }

    @Test
    void failedAttemptIsRetriedAndThirdAttemptPays() throws Exception {
        String id = submit("pago-despues-de-reintentos");
        PaymentResult paid = readPaid("pago-despues-de-reintentos");
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.paymentStatus()).isEqualTo("PAID");
        assertThat(paid.attempts()).isEqualTo(3);
        assertThat(decision.count(id)).isEqualTo(3);
        awaitEmail(id);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
        recordScenario("pago-despues-de-reintentos", id, "PAID", paid.attempts(), sender.sent.contains(id), false);
    }

    @Test
    void fourthAttemptCanPayWithoutEnteringDeadLetterQueue() throws Exception {
        String id = submit("pago-en-cuarto-intento");
        PaymentResult paid = readPaid("pago-en-cuarto-intento");
        assertThat(paid.pedidoId()).isEqualTo(id);
        assertThat(paid.attempts()).isEqualTo(4);
        assertThat(decision.count(id)).isEqualTo(4);
        awaitEmail(id);
        assertThat(template.receive(RabbitTopology.DEAD_QUEUE, 100)).isNull();
        recordScenario("pago-en-cuarto-intento", id, "PAID", paid.attempts(), sender.sent.contains(id), false);
    }

    @Test
    void fourFailuresReachDeadLetterQueue() throws Exception {
        String id = submit("fallo-definitivo");
        Message message = template.receive(RabbitTopology.DEAD_QUEUE, 10000);
        assertThat(message).isNotNull();
        FailedPayment failed = mapper.readValue(message.getBody(), FailedPayment.class);
        recordDeadLetter(message, failed);
        assertThat(failed.order().pedidoId()).isEqualTo(id);
        assertThat(failed.attempts()).isEqualTo(4);
        assertThat(failed.reason()).contains("cuatro intentos");
        assertThat(message.getMessageProperties().getHeaders().get("failedAttempts")).isEqualTo(4);
        assertThat(decision.count(id)).isEqualTo(4);
        assertThat(template.receive(auditQueue, 100)).isNull();
        Thread.sleep(200);
        assertThat(sender.sent).doesNotContain(id);
        recordScenario("fallo-definitivo", id, "FAILED", failed.attempts(), sender.sent.contains(id), true);
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

    private String submit(String scenario) {
        String id = UUID.randomUUID().toString();
        OrderMessage order = new OrderMessage(id, scenario + "@example.test", List.of(
                new OrderItem("Alerce", 2, new BigDecimal("1560.00")),
                new OrderItem("Ruta Clara", 1, new BigDecimal("1050.00"))),
                new BigDecimal("4170.00"), Instant.now());
        publisher.publish(RabbitTopology.ORDER_EXCHANGE, RabbitTopology.PAYMENT_KEY, id, order, null);
        Message message = template.receive(orderAuditQueue, 5000);
        assertThat(message).as("el exchange debe entregar el JSON original a la cola de auditoría").isNotNull();
        recordEvidence("mensaje-json-rabbitmq", scenario, id,
                mapper.readValue(message.getBody(), Object.class));
        return id;
    }

    private PaymentResult readPaid(String scenario) {
        Message message = template.receive(auditQueue, 10000);
        assertThat(message).isNotNull();
        PaymentResult paid = mapper.readValue(message.getBody(), PaymentResult.class);
        recordEvidence("evento-pago", scenario, paid.pedidoId(), paid);
        return paid;
    }

    private void recordScenario(String scenario, String pedidoId, String status,
            int attempts, boolean emailSent, boolean deadLetter) {
        System.out.println("BIKESTORE_DEMO " + mapper.writeValueAsString(Map.of(
                "tipo", "resultado-escenario",
                "escenario", scenario,
                "pedidoId", pedidoId,
                "paymentStatus", status,
                "intentos", attempts,
                "confirmacionRegistrada", emailSent,
                "enDlq", deadLetter)));
    }

    private void recordEvidence(String kind, String scenario, String pedidoId, Object payload) {
        System.out.println("BIKESTORE_DEMO " + mapper.writeValueAsString(Map.of(
                "tipo", kind,
                "escenario", scenario,
                "pedidoId", pedidoId,
                "payload", payload)));
    }

    private void recordDeadLetter(Message message, FailedPayment failed) {
        System.out.println("BIKESTORE_DEMO " + mapper.writeValueAsString(Map.of(
                "tipo", "mensaje-dlq",
                "escenario", "fallo-definitivo",
                "pedidoId", failed.order().pedidoId(),
                "messageId", message.getMessageProperties().getMessageId(),
                "failedAttempts", message.getMessageProperties().getHeaders().get("failedAttempts"),
                "payload", failed)));
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
            return switch (order.customerEmail()) {
                case "pago-inmediato@example.test" -> attempt == 1;
                case "pago-despues-de-reintentos@example.test" -> attempt == 3;
                case "pago-en-cuarto-intento@example.test" -> attempt == 4;
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

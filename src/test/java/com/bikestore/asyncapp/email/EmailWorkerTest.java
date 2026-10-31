package com.bikestore.asyncapp.email;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.bikestore.asyncapp.messaging.OrderEventLogger;
import com.bikestore.asyncapp.model.OrderItem;
import com.bikestore.asyncapp.model.PaymentResult;
import com.rabbitmq.client.Channel;

import tools.jackson.databind.ObjectMapper;

class EmailWorkerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EmailSender sender = mock(EmailSender.class);
    private final OrderEventLogger events = mock(OrderEventLogger.class);
    private EmailWorker worker;

    @BeforeEach
    void setUp() {
        worker = new EmailWorker(mapper, sender, events);
    }

    @Test
    void confirmsOnlyPaidOrdersAndPassesTheCartSummary() throws Exception {
        Channel channel = mock(Channel.class);
        PaymentResult paid = payment("PAID");

        worker.process(delivery(paid), channel);

        org.mockito.ArgumentCaptor<PaymentResult> sent = org.mockito.ArgumentCaptor.forClass(PaymentResult.class);
        verify(sender).sendConfirmation(sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().items()).containsExactlyElementsOf(paid.items());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().total()).isEqualByComparingTo("4170.00");
        verify(channel).basicAck(24L, false);
    }

    @Test
    void pendingOrderIsAcknowledgedWithoutSendingEmail() throws Exception {
        Channel channel = mock(Channel.class);

        worker.process(delivery(payment("PENDING")), channel);

        verify(sender, never()).sendConfirmation(org.mockito.ArgumentMatchers.any());
        verify(channel).basicAck(24L, false);
    }

    private PaymentResult payment(String status) {
        return new PaymentResult("pedido-123", "ana@example.com", List.of(
                new OrderItem("Alerce", 2, new BigDecimal("1560.00")),
                new OrderItem("Ruta Clara", 1, new BigDecimal("1050.00"))),
                new BigDecimal("4170.00"), status, 3, Instant.now());
    }

    private Message delivery(PaymentResult payment) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(24L);
        return new Message(mapper.writeValueAsBytes(payment), properties);
    }
}

package com.bikestore.asyncapp.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.messaging.ConfirmedPublisher;
import com.bikestore.asyncapp.messaging.OrderEventLogger;
import com.bikestore.asyncapp.model.FailedPayment;
import com.bikestore.asyncapp.model.OrderItem;
import com.bikestore.asyncapp.model.OrderMessage;
import com.bikestore.asyncapp.model.PaymentResult;
import com.rabbitmq.client.Channel;

import tools.jackson.databind.ObjectMapper;

class PaymentWorkerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PaymentDecision decision = mock(PaymentDecision.class);
    private final ConfirmedPublisher publisher = mock(ConfirmedPublisher.class);
    private final OrderEventLogger events = mock(OrderEventLogger.class);
    private PaymentWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PaymentWorker(mapper, decision, publisher, events);
    }

    @Test
    void retryKeepsAllCartItemsAndSchedulesExactlyOneNextAttempt() throws Exception {
        OrderMessage order = order();
        when(decision.approved(order, 1)).thenReturn(false);
        Channel channel = mock(Channel.class);

        worker.process(delivery(order, null), channel);

        verify(decision, times(1)).approved(order, 1);
        verify(publisher).publish(eq(RabbitTopology.RETRY_EXCHANGE), eq(RabbitTopology.RETRY_KEY),
                eq(order.pedidoId()), eq(order), eq(1));
        verify(channel).basicAck(23L, false);
    }

    @Test
    void paymentAfterRetryPublishesOnePaidResultForTheWholeCart() throws Exception {
        OrderMessage order = order();
        when(decision.approved(order, 3)).thenReturn(true);
        Channel channel = mock(Channel.class);

        worker.process(delivery(order, 2), channel);

        ArgumentCaptor<Object> result = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(1)).publish(eq(RabbitTopology.EVENT_EXCHANGE), eq(RabbitTopology.PAID_KEY),
                eq(order.pedidoId()), result.capture(), org.mockito.ArgumentMatchers.isNull());
        org.assertj.core.api.Assertions.assertThat(result.getValue()).isInstanceOf(PaymentResult.class);
        PaymentResult paid = (PaymentResult) result.getValue();
        org.assertj.core.api.Assertions.assertThat(paid.paymentStatus()).isEqualTo("PAID");
        org.assertj.core.api.Assertions.assertThat(paid.attempts()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(paid.items()).containsExactlyElementsOf(order.items());
        org.assertj.core.api.Assertions.assertThat(paid.total()).isEqualByComparingTo("4170.00");
        verify(decision, times(1)).approved(order, 3);
        verify(channel).basicAck(23L, false);
    }

    @Test
    void fourthFailurePublishesTheWholeOrderToDeadLetterQueue() throws Exception {
        OrderMessage order = order();
        when(decision.approved(order, 4)).thenReturn(false);
        Channel channel = mock(Channel.class);

        worker.process(delivery(order, 3), channel);

        ArgumentCaptor<Object> failurePayload = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(1)).publish(eq(RabbitTopology.DEAD_EXCHANGE), eq(RabbitTopology.DEAD_KEY),
                eq(order.pedidoId()), failurePayload.capture(), eq(4));
        org.assertj.core.api.Assertions.assertThat(failurePayload.getValue()).isInstanceOf(FailedPayment.class);
        FailedPayment failure = (FailedPayment) failurePayload.getValue();
        org.assertj.core.api.Assertions.assertThat(failure.order().items()).containsExactlyElementsOf(order.items());
        org.assertj.core.api.Assertions.assertThat(failure.order().total()).isEqualByComparingTo("4170.00");
        org.assertj.core.api.Assertions.assertThat(failure.attempts()).isEqualTo(4);
        verify(decision, times(1)).approved(order, 4);
        verify(channel).basicAck(23L, false);
    }

    private OrderMessage order() {
        return new OrderMessage("pedido-123", "ana@example.com", List.of(
                new OrderItem("Alerce", 2, new BigDecimal("1560.00")),
                new OrderItem("Ruta Clara", 1, new BigDecimal("1050.00"))),
                new BigDecimal("4170.00"), Instant.now());
    }

    private Message delivery(OrderMessage order, Integer failedAttempts) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(23L);
        if (failedAttempts != null) {
            properties.setHeader("failedAttempts", failedAttempts);
        }
        return new Message(mapper.writeValueAsBytes(order), properties);
    }
}

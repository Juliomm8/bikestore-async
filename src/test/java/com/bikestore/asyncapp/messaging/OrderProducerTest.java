package com.bikestore.asyncapp.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.model.OrderItem;
import com.bikestore.asyncapp.model.OrderMessage;

class OrderProducerTest {
    @Test
    void publishesOnePersistentOrderPayloadWithEveryItemAndTheCalculatedTotal() {
        ConfirmedPublisher publisher = mock(ConfirmedPublisher.class);
        OrderEventLogger events = mock(OrderEventLogger.class);
        OrderProducer producer = new OrderProducer(publisher, events);
        List<OrderItem> items = List.of(
                new OrderItem("Alerce", 2, new BigDecimal("1560.00")),
                new OrderItem("Ruta Clara", 1, new BigDecimal("1050.00")));

        String pedidoId = producer.submit("ana@example.com", items);

        ArgumentCaptor<OrderMessage> message = ArgumentCaptor.forClass(OrderMessage.class);
        verify(publisher, times(1)).publish(eq(RabbitTopology.ORDER_EXCHANGE), eq(RabbitTopology.PAYMENT_KEY),
                eq(pedidoId), message.capture(), isNull());
        assertThat(message.getValue().pedidoId()).isEqualTo(pedidoId);
        assertThat(message.getValue().items()).containsExactlyElementsOf(items);
        assertThat(message.getValue().total()).isEqualByComparingTo("4170.00");
        assertThat(message.getValue().createdAt()).isNotNull();
    }
}

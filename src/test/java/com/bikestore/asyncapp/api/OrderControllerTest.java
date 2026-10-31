package com.bikestore.asyncapp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.bikestore.asyncapp.model.OrderItemRequest;
import com.bikestore.asyncapp.model.OrderRequest;

class OrderControllerTest {
    @Test
    void returnsAcceptedWithOneOrderIdAndServerCalculatedTotal() {
        OrderSubmissionService submissions = mock(OrderSubmissionService.class);
        OrderRequest request = new OrderRequest("ana@example.com", List.of(
                new OrderItemRequest("Alerce", 2), new OrderItemRequest("Ruta Clara", 1)), null, null, null);
        when(submissions.submit(request)).thenReturn(new OrderSubmissionResult("pedido-123", new BigDecimal("4170.00")));

        var response = new OrderController(submissions).submit(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().pedidoId()).isEqualTo("pedido-123");
        assertThat(response.getBody().total()).isEqualByComparingTo("4170.00");
        verify(submissions).submit(request);
    }
}

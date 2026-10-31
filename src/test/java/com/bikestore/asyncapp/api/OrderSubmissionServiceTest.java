package com.bikestore.asyncapp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bikestore.asyncapp.catalog.CatalogPriceService;
import com.bikestore.asyncapp.messaging.OrderProducer;
import com.bikestore.asyncapp.model.OrderItem;
import com.bikestore.asyncapp.model.OrderItemRequest;
import com.bikestore.asyncapp.model.OrderRequest;

class OrderSubmissionServiceTest {
    private OrderProducer producer;
    private OrderSubmissionService submissions;
    private Validator validator;

    @BeforeEach
    void setUp() {
        producer = mock(OrderProducer.class);
        when(producer.submit(anyString(), anyList())).thenReturn("pedido-123");
        submissions = new OrderSubmissionService(producer, new CatalogPriceService());
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validatesCartAndCompatibleSingleItemRequest() {
        OrderRequest cart = new OrderRequest("ana@example.com", List.of(
                new OrderItemRequest("Alerce", 2), new OrderItemRequest("Ruta Clara", 1)),
                null, null, null);
        OrderRequest legacy = new OrderRequest("ana@example.com", null, "Ruta Clara", 1,
                new BigDecimal("1050.00"));

        assertThat(validator.validate(cart)).isEmpty();
        assertThat(validator.validate(legacy)).isEmpty();
    }

    @Test
    void rejectsEmptyInvalidOrOversizedCart() {
        OrderRequest empty = new OrderRequest("ana@example.com", List.of(), null, null, null);
        OrderRequest invalidItem = new OrderRequest("ana@example.com",
                List.of(new OrderItemRequest("Ruta Clara", 0)), null, null, null);
        OrderRequest oversized = new OrderRequest("ana@example.com", java.util.Collections.nCopies(13,
                new OrderItemRequest("Ruta Clara", 1)), null, null, null);

        assertThat(validator.validate(empty)).isNotEmpty();
        assertThat(validator.validate(invalidItem)).isNotEmpty();
        assertThat(validator.validate(oversized)).isNotEmpty();
    }

    @Test
    void usesServerPricesAndCombinesRepeatedModelsIntoOneOrder() {
        OrderRequest request = new OrderRequest("ana@example.com", List.of(
                new OrderItemRequest("Alerce", 2), new OrderItemRequest(" ruta clara ", 1),
                new OrderItemRequest("RUTA CLARA", 2)), null, null,
                new BigDecimal("0.01"));

        var result = submissions.submit(request);
        assertThat(result.pedidoId()).isEqualTo("pedido-123");
        assertThat(result.total()).isEqualByComparingTo("6270.00");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<OrderItem>> items = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(producer).submit(org.mockito.ArgumentMatchers.eq("ana@example.com"), items.capture());
        assertThat(items.getValue()).containsExactly(
                new OrderItem("Alerce", 2, new BigDecimal("1560.00")),
                new OrderItem("Ruta Clara", 3, new BigDecimal("1050.00")));
    }

    @Test
    void ignoresLegacyBrowserPriceAndStillUsesCatalogPrice() {
        OrderRequest request = new OrderRequest("ana@example.com", null, "Ruta Carmesí", 2,
                new BigDecimal("0.01"));

        submissions.submit(request);

        verify(producer).submit(org.mockito.ArgumentMatchers.eq("ana@example.com"),
                org.mockito.ArgumentMatchers.eq(List.of(
                        new OrderItem("Ruta Carmesí", 2, new BigDecimal("890.00")))));
    }

    @Test
    void rejectsUnknownModelsAndExcessiveCombinedQuantities() {
        OrderRequest unknown = new OrderRequest("ana@example.com",
                List.of(new OrderItemRequest("Modelo inventado", 1)), null, null, null);
        OrderRequest excessive = new OrderRequest("ana@example.com", List.of(
                new OrderItemRequest("Mirador", 99), new OrderItemRequest("Mirador", 1)), null, null, null);

        assertThatThrownBy(() -> submissions.submit(unknown)).isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> submissions.submit(excessive)).isInstanceOf(InvalidOrderException.class);
    }
}

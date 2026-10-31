package com.bikestore.asyncapp.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.bikestore.asyncapp.catalog.CatalogPrice;
import com.bikestore.asyncapp.catalog.CatalogPriceService;
import com.bikestore.asyncapp.messaging.OrderProducer;
import com.bikestore.asyncapp.model.OrderItem;
import com.bikestore.asyncapp.model.OrderItemRequest;
import com.bikestore.asyncapp.model.OrderRequest;

@Service
public class OrderSubmissionService {
    private static final int MAX_QUANTITY_PER_MODEL = 99;

    private final OrderProducer producer;
    private final CatalogPriceService catalog;

    public OrderSubmissionService(OrderProducer producer, CatalogPriceService catalog) {
        this.producer = producer;
        this.catalog = catalog;
    }

    public OrderSubmissionResult submit(OrderRequest request) {
        List<OrderItemRequest> requestedItems = request.items() != null && !request.items().isEmpty()
                ? request.items()
                : List.of(new OrderItemRequest(request.bikeModel(), request.quantity()));

        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : requestedItems) {
            CatalogPrice price = catalog.resolve(item.bikeModel());
            int quantity = item.quantity() == null ? 0 : item.quantity();
            int combined = quantities.getOrDefault(price.bikeModel(), 0) + quantity;
            if (combined > MAX_QUANTITY_PER_MODEL) {
                throw new InvalidOrderException("La cantidad máxima por modelo es 99: " + price.bikeModel());
            }
            quantities.put(price.bikeModel(), combined);
        }

        List<OrderItem> items = quantities.entrySet().stream()
                .map(entry -> {
                    CatalogPrice price = catalog.resolve(entry.getKey());
                    return new OrderItem(price.bikeModel(), entry.getValue(), price.unitPrice());
                })
                .toList();
        if (items.isEmpty()) {
            throw new InvalidOrderException("El carrito no contiene bicicletas");
        }
        String pedidoId = producer.submit(request.customerEmail(), items);
        BigDecimal total = items.stream().map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderSubmissionResult(pedidoId, total);
    }
}

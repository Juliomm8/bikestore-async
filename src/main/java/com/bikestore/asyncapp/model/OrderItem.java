package com.bikestore.asyncapp.model;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderItem(String bikeModel, int quantity, BigDecimal unitPrice) {
    public OrderItem {
        Objects.requireNonNull(bikeModel, "bikeModel");
        Objects.requireNonNull(unitPrice, "unitPrice");
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

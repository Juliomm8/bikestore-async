package com.bikestore.asyncapp.catalog;

import java.math.BigDecimal;

public record CatalogPrice(String bikeModel, BigDecimal unitPrice) {
}

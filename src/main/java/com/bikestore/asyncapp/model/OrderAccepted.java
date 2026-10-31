package com.bikestore.asyncapp.model;

import java.math.BigDecimal;

public record OrderAccepted(String pedidoId, String status, BigDecimal total) {
}

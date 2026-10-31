package com.bikestore.asyncapp.api;

import java.math.BigDecimal;

public record OrderSubmissionResult(String pedidoId, BigDecimal total) {
}

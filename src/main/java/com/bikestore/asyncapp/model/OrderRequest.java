package com.bikestore.asyncapp.model;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotBlank @Email String customerEmail,
        @NotBlank String bikeModel,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {
}

package com.bikestore.asyncapp.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotBlank String bikeModel,
        @NotNull @Min(1) @Max(99) Integer quantity) {
}

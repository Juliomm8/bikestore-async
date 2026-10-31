package com.bikestore.asyncapp.model;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @NotBlank @Email String customerEmail,
        @Size(max = 12) List<@NotNull @Valid OrderItemRequest> items,
        String bikeModel,
        @Min(1) @Max(99) Integer quantity,
        @DecimalMin("0.01") BigDecimal unitPrice) {

    @AssertTrue(message = "El pedido debe incluir artículos o los campos del formato anterior")
    public boolean isOrderDetailsValid() {
        if (items != null && !items.isEmpty()) {
            return true;
        }
        return bikeModel != null && !bikeModel.isBlank()
                && quantity != null && quantity >= 1 && quantity <= 99
                && unitPrice != null && unitPrice.signum() > 0;
    }
}

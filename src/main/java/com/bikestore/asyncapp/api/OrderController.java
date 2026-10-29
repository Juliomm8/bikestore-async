package com.bikestore.asyncapp.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bikestore.asyncapp.messaging.OrderProducer;
import com.bikestore.asyncapp.model.OrderAccepted;
import com.bikestore.asyncapp.model.OrderRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderProducer producer;

    public OrderController(OrderProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    public ResponseEntity<OrderAccepted> submit(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.accepted().body(new OrderAccepted(producer.submit(request), "ACCEPTED"));
    }
}

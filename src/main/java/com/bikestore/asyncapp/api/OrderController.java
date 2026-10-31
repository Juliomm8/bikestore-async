package com.bikestore.asyncapp.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bikestore.asyncapp.model.OrderAccepted;
import com.bikestore.asyncapp.model.OrderRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderSubmissionService submissions;

    public OrderController(OrderSubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping
    public ResponseEntity<OrderAccepted> submit(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.accepted().body(new OrderAccepted(submissions.submit(request), "ACCEPTED"));
    }
}

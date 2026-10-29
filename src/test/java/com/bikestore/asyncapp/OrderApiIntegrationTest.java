package com.bikestore.asyncapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.bikestore.asyncapp.config.RabbitTopology;
import com.bikestore.asyncapp.model.OrderAccepted;
import com.bikestore.asyncapp.model.OrderMessage;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class OrderApiIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void clearQueue() {
        rabbitAdmin.purgeQueue(RabbitTopology.PAYMENT_QUEUE, true);
    }

    @Test
    void validOrderIsAcceptedAndPublishedAsPersistentJson() throws Exception {
        HttpResponse<String> response = post("""
                {"customerEmail":"ana@example.com","bikeModel":"Montaña X","quantity":2,"unitPrice":450.50}
                """);

        assertThat(response.statusCode()).isEqualTo(202);
        OrderAccepted accepted = objectMapper.readValue(response.body(), OrderAccepted.class);
        assertThat(accepted.pedidoId()).isNotBlank();
        assertThat(accepted.status()).isEqualTo("ACCEPTED");

        Message message = rabbitTemplate.receive(RabbitTopology.PAYMENT_QUEUE, 5000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        OrderMessage order = objectMapper.readValue(message.getBody(), OrderMessage.class);
        assertThat(order.pedidoId()).isEqualTo(accepted.pedidoId());
        assertThat(order.bikeModel()).isEqualTo("Montaña X");
        assertThat(order.quantity()).isEqualTo(2);
        assertThat(order.createdAt()).isNotNull();
    }

    @Test
    void invalidOrderIsRejected() throws Exception {
        HttpResponse<String> response = post("""
                {"customerEmail":"incorrecto","bikeModel":"","quantity":0,"unitPrice":0}
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(rabbitTemplate.receive(RabbitTopology.PAYMENT_QUEUE, 1000)).isNull();
    }

    @Test
    void orderFormIsServed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                .GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("BikeStore", "product-grid", "order-form",
                "/css/store.css", "/js/store.js");

        HttpRequest scriptRequest = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/js/store.js")).GET().build();
        HttpResponse<String> script = client.send(scriptRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(script.statusCode()).isEqualTo(200);
        assertThat(script.body()).contains("/api/orders", "customerEmail", "bikeModel", "unitPrice");

        HttpRequest stylesheetRequest = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/css/store.css")).GET().build();
        HttpResponse<String> stylesheet = client.send(stylesheetRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(stylesheet.statusCode()).isEqualTo(200);
        assertThat(stylesheet.body()).contains(".product-card", ".order-panel");

        HttpRequest imageRequest = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/images/ruta-clara.jpg")).GET().build();
        HttpResponse<byte[]> image = client.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body().length).isGreaterThan(10_000);
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

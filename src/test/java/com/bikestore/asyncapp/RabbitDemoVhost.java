package com.bikestore.asyncapp;

final class RabbitDemoVhost {
    static final String NAME = "bikestore-evaluation-demo";

    private RabbitDemoVhost() {
    }

    static void require(String actual) {
        if (!NAME.equals(actual)) {
            throw new IllegalStateException("Las pruebas con RabbitMQ requieren el vhost aislado " + NAME);
        }
    }
}

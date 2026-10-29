package com.bikestore.asyncapp.messaging;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderEventLogger {
    private static final Logger LOG = LoggerFactory.getLogger(OrderEventLogger.class);

    public void info(String pedidoId, String event, String detail) {
        LOG.info("pedidoId={} timestamp={} thread={} evento={} detalle={}",
                pedidoId, Instant.now(), Thread.currentThread().getName(), event, detail);
    }

    public void error(String pedidoId, String event, String detail, Throwable cause) {
        LOG.error("pedidoId={} timestamp={} thread={} evento={} detalle={}",
                pedidoId, Instant.now(), Thread.currentThread().getName(), event, detail, cause);
    }
}

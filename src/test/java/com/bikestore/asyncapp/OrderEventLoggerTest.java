package com.bikestore.asyncapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.bikestore.asyncapp.messaging.OrderEventLogger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class OrderEventLoggerTest {
    @Test
    void everyBusinessLogContainsOrderIdTimestampAndThread() {
        Logger logger = (Logger) LoggerFactory.getLogger(OrderEventLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new OrderEventLogger().info("pedido-123", "PAYMENT_PAID", "Aprobado");
            String line = appender.list.get(0).getFormattedMessage();
            assertThat(line).contains("pedidoId=pedido-123", "thread=" + Thread.currentThread().getName(),
                    "evento=PAYMENT_PAID", "timestamp=");
            assertThat(line).containsPattern("timestamp=\\d{4}-\\d{2}-\\d{2}T");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}

package com.enterprise.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Kafka Consumer demonstrating W3C TraceContext Extraction (Pillars 1, 2, 3)
 * Extracts trace headers to continue transaction waterfalls from upstream producers.
 */
@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("com.enterprise.observability.kafka");

    private final OrderService orderService;

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    // TextMapGetter implementation to extract traceparent from Kafka Record Headers
    private static final TextMapGetter<Headers> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers carrier) {
            List<String> keys = new ArrayList<>();
            for (Header header : carrier) {
                keys.add(header.key());
            }
            return keys;
        }

        @Override
        public String get(Headers carrier, String key) {
            Header header = carrier.lastHeader(key);
            if (header != null && header.value() != null) {
                return new String(header.value(), StandardCharsets.UTF_8);
            }
            return null;
        }
    };

    @KafkaListener(topics = "orders.events", groupId = "inventory-processing-group")
    public void consume(ConsumerRecord<String, String> record) {
        // Extract parent context from Kafka record headers
        Context extractedContext = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), record.headers(), GETTER);

        Span span = tracer.spanBuilder("KafkaConsume: " + record.key())
                .setParent(extractedContext)
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination", record.topic())
                .setAttribute("messaging.kafka.partition", record.partition())
                .setAttribute("messaging.kafka.offset", record.offset())
                .startSpan();

        try (var scope = span.makeCurrent()) {
            log.info("Processing order {} from partition {} offset {} with propagated traceId={}",
                    record.key(), record.partition(), record.offset(), span.getSpanContext().getTraceId());

            // Execute database transaction in SQL Server
            orderService.updateInventoryInDatabase(record.key());

            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            log.error("Failed to process inventory for order {}", record.key(), e);
            throw e;
        } finally {
            span.end();
        }
    }
}

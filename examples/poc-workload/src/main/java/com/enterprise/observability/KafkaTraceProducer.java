package com.enterprise.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Advanced Kafka Producer demonstrating W3C TraceContext Header Injection (Pillar 3: APM)
 * Enables distributed tracing across asynchronous messaging fabrics.
 */
@Component
public class KafkaTraceProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaTraceProducer.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("com.enterprise.observability.kafka");

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaTraceProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // TextMapSetter implementation to inject W3C traceparent into Kafka Record Headers
    private static final TextMapSetter<Headers> SETTER = (carrier, key, value) -> {
        if (carrier != null && value != null) {
            carrier.remove(key); // Remove stale headers if re-trying
            carrier.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    };

    /**
     * Sends an order event to Kafka while injecting the active distributed trace context into headers.
     */
    public void sendOrderCreatedEvent(String orderId, String payload) {
        Span span = tracer.spanBuilder("KafkaProduce: " + orderId)
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination", "orders.events")
                .setAttribute("order.id", orderId)
                .startSpan();

        try (var scope = span.makeCurrent()) {
            ProducerRecord<String, String> record = new ProducerRecord<>("orders.events", orderId, payload);

            // Inject W3C TraceContext (traceparent & tracestate) into Kafka headers
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(
                    Context.current(),
                    record.headers(),
                    SETTER
            );

            log.info("Emitting Kafka OrderCreatedEvent for orderId={} with traceId={}", orderId, span.getSpanContext().getTraceId());

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    span.recordException(ex);
                    log.error("Failed to produce Kafka message for orderId={}", orderId, ex);
                } else {
                    span.setAttribute("messaging.kafka.partition", result.getRecordMetadata().partition());
                    span.setAttribute("messaging.kafka.offset", result.getRecordMetadata().offset());
                }
            });
        } finally {
            span.end();
        }
    }
}

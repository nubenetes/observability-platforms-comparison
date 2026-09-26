package com.enterprise.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enterprise Mission-Critical Core Service
 * Demonstrating the 7 Pillars of Observability:
 * - W3C TraceContext Propagation over Apache Kafka
 * - OpenTelemetry / Dynatrace / Instana APM Bytecode Instrumentation
 * - JVM Flight Recorder & eBPF Continuous Profiling Integration
 * - Microsoft SQL Server Traced JDBC Queries
 */
@SpringBootApplication
@EnableAsync
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}

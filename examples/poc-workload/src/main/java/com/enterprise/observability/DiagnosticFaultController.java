package com.enterprise.observability;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Diagnostic Fault Injection Controller
 * Provides controlled failure scenarios to evaluate candidate observability platforms
 * during Phase 1 Proof-of-Concept (PoC) testing.
 */
@RestController
@RequestMapping("/api")
public class DiagnosticFaultController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticFaultController.class);

    private final OrderService orderService;
    private final KafkaTraceProducer kafkaProducer;
    private final JdbcTemplate jdbcTemplate;

    public DiagnosticFaultController(OrderService orderService,
                                     KafkaTraceProducer kafkaProducer,
                                     JdbcTemplate jdbcTemplate) {
        this.orderService = orderService;
        this.kafkaProducer = kafkaProducer;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Standard End-to-End Transaction Endpoint:
     * Ingress -> HTTP -> DB Insert -> Kafka Event -> Consumer -> DB Update
     */
    @PostMapping("/orders")
    @WithSpan("API.createOrderFlow")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestParam(defaultValue = "99.50") double amount) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Initiating order flow for {}", orderId);

        // Step 1: SQL Server Insert
        orderService.createOrder(orderId, amount);

        // Step 2: Kafka Asynchronous Event
        kafkaProducer.sendOrderCreatedEvent(orderId, "{\"orderId\":\"" + orderId + "\",\"amount\":" + amount + "}");

        return ResponseEntity.ok(Map.of(
                "status", "ORDER_CREATED",
                "orderId", orderId,
                "amount", amount
        ));
    }

    /**
     * Fault 1: CPU Saturation & Catastrophic Backtracking (Tests Pillar 4: Continuous Profiling)
     * Generates a dense CPU flame graph without taking down the node.
     */
    @GetMapping("/faults/cpu-lock")
    @WithSpan("Fault.cpuLock")
    public ResponseEntity<String> triggerCpuSaturation(@RequestParam(defaultValue = "3000") int durationMs) {
        log.warn("Triggering CPU Saturation fault for {} ms", durationMs);
        long endTime = System.currentTimeMillis() + durationMs;

        // Catastrophic backtracking regex calculation
        Pattern pattern = Pattern.compile("(a+)+b");
        long iterations = 0;
        while (System.currentTimeMillis() < endTime) {
            pattern.matcher("aaaaaaaaaaaaaaaaaaaaaaaaaaaaac").matches();
            iterations++;
        }

        return ResponseEntity.ok("CPU fault completed. Total regex iterations: " + iterations);
    }

    /**
     * Fault 2: Java Thread Contention (Tests JVM Metrics & Profiler Thread States)
     * Multiple worker threads fighting for the same synchronized monitor.
     */
    private final Object lockA = new Object();

    @GetMapping("/faults/thread-contention")
    @WithSpan("Fault.threadContention")
    public ResponseEntity<String> triggerThreadContention(@RequestParam(defaultValue = "10") int threadCount) {
        log.warn("Spawning {} threads for lock contention", threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                synchronized (lockA) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ignored) {
                    }
                }
                latch.countDown();
            });
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return ResponseEntity.ok("Thread contention test finished.");
    }

    /**
     * Fault 3: Simulated SQL Server Blocking / Table Lock (Tests Database Monitoring & APM trace spans)
     */
    @GetMapping("/faults/db-delay")
    @WithSpan("Fault.databaseDelay")
    public ResponseEntity<String> triggerDatabaseDelay(@RequestParam(defaultValue = "4") int seconds) {
        log.warn("Executing artificial database delay of {} seconds in SQL Server", seconds);
        String sql = "WAITFOR DELAY '00:00:0" + Math.min(seconds, 9) + "'; SELECT @@VERSION;";
        String version = jdbcTemplate.queryForObject(sql, String.class);
        return ResponseEntity.ok("DB Delay finished. SQL Server version: " + version);
    }
}

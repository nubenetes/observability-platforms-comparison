package com.enterprise.observability;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enterprise Order Service
 * Demonstrates:
 * - Traced Microsoft SQL Server interactions (Pillar 3 & Database Monitoring)
 * - Custom Java Flight Recorder (JFR) Event generation (Pillar 4: Continuous Profiling)
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final JdbcTemplate jdbcTemplate;

    public OrderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Custom JFR event captured by continuous profilers (Pyroscope, Dynatrace, Instana, Elastic)
    @Label("Order Processing Execution")
    @Description("Custom JVM Flight Recorder event tracking business logic duration")
    @Category({"Business", "Orders"})
    static class OrderExecutionJfrEvent extends Event {
        @Label("Order ID")
        String orderId;

        @Label("Status")
        String status;
    }

    @WithSpan("OrderService.createOrder")
    @Transactional
    public void createOrder(String orderId, double amount) {
        OrderExecutionJfrEvent jfrEvent = new OrderExecutionJfrEvent();
        jfrEvent.orderId = orderId;
        jfrEvent.status = "STARTING";
        jfrEvent.begin();

        try {
            log.info("Persisting orderId={} with amount={}", orderId, amount);

            // Execute sanitized parameterized SQL against Microsoft SQL Server
            String sql = "INSERT INTO enterprise_orders (order_id, amount, status, created_at) VALUES (?, ?, 'CREATED', CURRENT_TIMESTAMP)";
            jdbcTemplate.update(sql, orderId, amount);

            jfrEvent.status = "SUCCESS";
        } catch (Exception e) {
            jfrEvent.status = "FAILED";
            log.error("Failed to insert order into SQL Server: {}", orderId, e);
            throw e;
        } finally {
            jfrEvent.commit();
        }
    }

    @WithSpan("OrderService.updateInventoryInDatabase")
    @Transactional
    public void updateInventoryInDatabase(String orderId) {
        log.info("Updating inventory for orderId={} in SQL Server", orderId);

        // SQL Server update query captured by APM & DBM
        String sql = "UPDATE inventory_stock SET reserved_units = reserved_units + 1, updated_at = CURRENT_TIMESTAMP WHERE item_category = 'CORE_PRODUCT'";
        jdbcTemplate.update(sql);
    }
}

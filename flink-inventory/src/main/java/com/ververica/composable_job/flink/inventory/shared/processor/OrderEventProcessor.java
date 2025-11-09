package com.ververica.composable_job.flink.inventory.shared.processor;

import com.ververica.composable_job.flink.inventory.shared.model.InventoryEvent;
import com.ververica.composable_job.flink.inventory.shared.model.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order Event Processor - Deducts Inventory for Order Items
 *
 * This function processes CDC events from PostgreSQL orders table and deducts
 * inventory for each product in the order.
 *
 * INPUT: CDC JSON from order-events topic (Debezium format)
 * OUTPUT: InventoryEvent with inventory deduction
 *
 * STATE MANAGEMENT:
 * - currentInventory: Current inventory level for each product
 *
 * PATTERN: Keyed State for Per-Product Inventory Tracking
 */
public class OrderEventProcessor extends KeyedProcessFunction<String, String, InventoryEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(OrderEventProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private transient ValueState<Integer> currentInventoryState;

    @Override
    public void open(Configuration parameters) {
        currentInventoryState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("current-inventory", TypeInformation.of(Integer.class))
        );
    }

    @Override
    public void processElement(
            String cdcEvent,
            Context ctx,
            Collector<InventoryEvent> out) throws Exception {

        try {
            // Parse Debezium CDC JSON
            JsonNode root = MAPPER.readTree(cdcEvent);

            // Extract operation type
            String op = root.path("op").asText();
            if (!"c".equals(op)) {
                // Only process CREATE (INSERT) operations for new orders
                return;
            }

            // Extract order data from "after" field (new row state)
            JsonNode after = root.path("after");
            if (after.isMissingNode()) {
                LOG.warn("⚠️  CDC event missing 'after' field: {}", cdcEvent.substring(0, Math.min(100, cdcEvent.length())));
                return;
            }

            String orderId = after.path("order_id").asText();
            String status = after.path("status").asText();

            // For this demo, we'll need to get order items separately
            // In production, you might join orders with order_items or process order_items CDC separately

            LOG.info("📦 Processing order: {} (status: {})", orderId, status);

            // NOTE: This is a simplified version. In production, you would:
            // 1. Either join with order_items CDC events
            // 2. Or query order_items from a state store
            // 3. Or process order_items CDC stream separately

            // For now, we'll emit a placeholder event
            InventoryEvent event = InventoryEvent.builder()
                .productId("ORDER_" + orderId)
                .productName("Order Processing Event")
                .eventType(EventType.INVENTORY_DECREASED)
                .changeReason("Order placed: " + orderId)
                .build();

            out.collect(event);

        } catch (Exception e) {
            LOG.error("❌ Failed to process CDC event: {}", e.getMessage(), e);
        }
    }
}

package com.ververica.composable_job.flink.inventory.shared.processor;

import com.ververica.composable_job.flink.inventory.shared.model.OrderItemDeduction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order Item Parser - Parses order-events JSON to OrderItemDeduction objects
 *
 * INPUT: JSON from order-events topic
 * <pre>
 * {
 *   "orderId": "order_123",
 *   "productId": "PROD_0001",
 *   "quantity": 2,
 *   "timestamp": 1234567890
 * }
 * </pre>
 *
 * OUTPUT: OrderItemDeduction POJO
 *
 * PATTERN: Map/Parse function for data transformation
 */
public class OrderItemParser extends ProcessFunction<String, OrderItemDeduction> {

    private static final Logger LOG = LoggerFactory.getLogger(OrderItemParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void processElement(
            String json,
            Context ctx,
            Collector<OrderItemDeduction> out) throws Exception {

        try {
            JsonNode root = MAPPER.readTree(json);

            String orderId = root.path("orderId").asText();
            String productId = root.path("productId").asText();
            int quantity = root.path("quantity").asInt();
            long timestamp = root.path("timestamp").asLong();

            if (orderId.isEmpty() || productId.isEmpty() || quantity <= 0) {
                LOG.warn("⚠️  Invalid order item event: {}", json);
                return;
            }

            OrderItemDeduction deduction = new OrderItemDeduction(
                orderId,
                productId,
                quantity,
                timestamp
            );

            out.collect(deduction);

            LOG.debug("📦 Parsed order item: {} x{} (order: {})",
                productId, quantity, orderId);

        } catch (Exception e) {
            LOG.error("❌ Failed to parse order item JSON: {}", json, e);
        }
    }
}

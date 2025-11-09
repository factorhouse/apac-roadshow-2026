package com.ververica.composable_job.flink.ordercdc;

import org.apache.flink.api.common.functions.MapFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Flink MapFunction to transform a raw Debezium CDC event for an 'order_item'
 * into a simple, clean JSON format for downstream consumers.
 */
public class OrderItemEventMapper implements MapFunction<String, String> {
    private static final Logger LOG = LoggerFactory.getLogger(OrderItemEventMapper.class);
    // Create ObjectMapper once to be efficient and thread-safe
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String map(String cdcJson) throws Exception {
        try {
            // 1. Parse the incoming Debezium JSON
            JsonNode root = objectMapper.readTree(cdcJson);
            JsonNode after = root.path("after");

            // If there's no "after" block, it's not an insert, so we can skip it.
            if (after.isMissingNode()) {
                return null;
            }

            // 2. Extract the required fields from the 'after' block
            int quantity = after.path("quantity").asInt();
            String productId = after.path("product_id").asText();
            String orderId = after.path("order_id").asText();
            
            // Extract the event timestamp from the root of the source object
            long timestamp = root.path("ts_ms").asLong();

            // 3. Build the new, clean JSON object
            ObjectNode outputNode = objectMapper.createObjectNode();
            outputNode.put("quantity", quantity);
            outputNode.put("productId", productId);
            outputNode.put("orderId", orderId);
            outputNode.put("timestamp", timestamp);

            // 4. Return the new JSON as a string
            return objectMapper.writeValueAsString(outputNode);
        } catch (Exception e) {
            LOG.error("Failed to parse or transform CDC event: {}", cdcJson, e);
            return null; // Return null for events that can't be parsed, to be filtered out later
        }
    }
}
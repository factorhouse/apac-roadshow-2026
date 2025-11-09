package com.ververica.composable_job.flink.inventory.shared.config;

/**
 * Constants class containing all Kafka topic names used by the Inventory Management Job.
 *
 * <p>This centralized definition ensures consistency across the application
 * and makes it easy to update topic names when needed.</p>
 *
 * @author Ververica
 * @since 1.0
 */
public final class KafkaTopics {

    /**
     * Topic for the main product catalog data.
     * Contains complete product information including inventory levels.
     */
    public static final String PRODUCTS = "products";

    /**
     * Topic for real-time product updates.
     * Used for continuous streaming of product changes after initial load.
     */
    public static final String PRODUCT_UPDATES = "product-updates";

    /**
     * Topic for inventory state change events.
     * Contains detailed inventory events including stock changes, price updates, etc.
     */
    public static final String INVENTORY_EVENTS = "inventory-events";

    /**
     * Topic for inventory alerts and notifications.
     * Contains critical alerts like low stock, out of stock, etc.
     */
    public static final String INVENTORY_ALERTS = "inventory-alerts";

    /**
     * Topic for WebSocket fanout events.
     * Used to broadcast inventory updates to connected WebSocket clients.
     */
    public static final String WEBSOCKET_FANOUT = "websocket_fanout";

    // Private constructor to prevent instantiation
    private KafkaTopics() {
        throw new AssertionError("KafkaTopics is a utility class and should not be instantiated");
    }
}

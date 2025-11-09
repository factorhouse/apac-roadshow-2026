package com.ververica.composable_job.flink.inventory.patterns.keyed_state;

import com.ververica.composable_job.model.ecommerce.Product;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * FLINK PATTERN: Keyed State (Per-Key State Management)
 *
 * PURPOSE:
 * Maintain state independently for each key in a stream.
 * Essential for tracking entities over time (users, products, sessions, devices).
 *
 * KEY CONCEPTS:
 * 1. State is scoped to a specific key (e.g., product ID)
 * 2. Flink manages state distribution across parallel instances
 * 3. State survives failures via checkpoints
 * 4. Different state types: ValueState, ListState, MapState, ReducingState
 *
 * WHEN TO USE:
 * - Track per-entity metrics (inventory per product)
 * - Detect changes per key (price changes, stock levels)
 * - Maintain session data (shopping carts per user)
 * - Implement stateful business logic (order processing per customer)
 *
 * STATE TYPES:
 * - ValueState<T>      - Single value per key
 * - ListState<T>       - List of values per key
 * - MapState<K,V>      - Map per key
 * - ReducingState<T>   - Aggregated value per key
 * - AggregatingState<T>- Custom aggregation per key
 */
public class KeyedStateExample {

    private static final Logger LOG = LoggerFactory.getLogger(KeyedStateExample.class);

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30000);  // Checkpoint every 30 seconds

        // Simulate product update stream
        DataStream<Product> productStream = createProductStream(env);

        // Apply keyed state processing
        DataStream<InventoryChange> changes = productStream
            .keyBy(product -> product.productId)  // Partition by product ID
            .process(new InventoryStateTracker()) // Stateful processing
            .name("Track Inventory Changes");

        changes.print();

        env.execute("Keyed State Pattern Example");
    }

    /**
     * KeyedProcessFunction that tracks inventory changes using ValueState.
     *
     * PATTERN EXPLANATION:
     * - Each product ID gets its own state instance
     * - State is automatically partitioned across parallel tasks
     * - Flink ensures state is checkpointed for fault tolerance
     * - State survives job restarts when using savepoints
     */
    public static class InventoryStateTracker
            extends KeyedProcessFunction<String, Product, InventoryChange> {

        // ValueState: Stores the last seen product for each product ID
        private transient ValueState<Product> lastProductState;

        // ValueState: Stores timestamp of last update
        private transient ValueState<Long> lastUpdateTimeState;

        /**
         * Called once per parallel instance when job starts.
         * Initialize state descriptors here.
         */
        @Override
        public void open(Configuration parameters) {
            // Create state descriptor for last product
            ValueStateDescriptor<Product> productDescriptor = new ValueStateDescriptor<>(
                "last-product",                    // State name (for debugging)
                TypeInformation.of(Product.class)  // Type information
            );

            // Get state handle from runtime context
            lastProductState = getRuntimeContext().getState(productDescriptor);

            // Create state descriptor for last update time
            ValueStateDescriptor<Long> timeDescriptor = new ValueStateDescriptor<>(
                "last-update-time",
                TypeInformation.of(Long.class)
            );

            lastUpdateTimeState = getRuntimeContext().getState(timeDescriptor);

            LOG.info("✅ State initialized for task: {}",
                getRuntimeContext().getIndexOfThisSubtask());
        }

        /**
         * Process each product update.
         * This is called for EVERY record with the SAME key.
         */
        @Override
        public void processElement(
                Product currentProduct,
                Context ctx,
                Collector<InventoryChange> out) throws Exception {

            // Retrieve previous state for THIS product ID
            Product previousProduct = lastProductState.value();
            Long previousUpdateTime = lastUpdateTimeState.value();

            long currentTime = System.currentTimeMillis();

            // First time seeing this product?
            if (previousProduct == null) {
                LOG.info("🆕 New product tracked: {}", currentProduct.productId);

                InventoryChange change = new InventoryChange();
                change.productId = currentProduct.productId;
                change.changeType = "NEW_PRODUCT";
                change.previousInventory = 0;
                change.currentInventory = currentProduct.inventory;
                change.timestamp = currentTime;

                out.collect(change);

            } else {
                // Product exists - check for changes
                if (previousProduct.inventory != currentProduct.inventory) {
                    int delta = currentProduct.inventory - previousProduct.inventory;

                    LOG.info("📊 Inventory change for {}: {} → {} (delta: {})",
                        currentProduct.productId,
                        previousProduct.inventory,
                        currentProduct.inventory,
                        delta);

                    InventoryChange change = new InventoryChange();
                    change.productId = currentProduct.productId;
                    change.changeType = delta > 0 ? "RESTOCK" : "SALE";
                    change.previousInventory = previousProduct.inventory;
                    change.currentInventory = currentProduct.inventory;
                    change.delta = delta;
                    change.timestamp = currentTime;

                    if (previousUpdateTime != null) {
                        change.timeSinceLastUpdate = currentTime - previousUpdateTime;
                    }

                    out.collect(change);
                }

                // Check for price changes
                if (Math.abs(previousProduct.price - currentProduct.price) > 0.01) {
                    LOG.info("💰 Price change for {}: ${} → ${}",
                        currentProduct.productId,
                        previousProduct.price,
                        currentProduct.price);

                    InventoryChange change = new InventoryChange();
                    change.productId = currentProduct.productId;
                    change.changeType = "PRICE_CHANGE";
                    change.previousPrice = previousProduct.price;
                    change.currentPrice = currentProduct.price;
                    change.timestamp = currentTime;

                    out.collect(change);
                }
            }

            // Update state with new values
            // IMPORTANT: State updates are local until next checkpoint
            lastProductState.update(currentProduct);
            lastUpdateTimeState.update(currentTime);
        }
    }

    /**
     * Event representing an inventory change
     */
    public static class InventoryChange implements Serializable {
        public String productId;
        public String changeType;
        public int previousInventory;
        public int currentInventory;
        public int delta;
        public double previousPrice;
        public double currentPrice;
        public long timestamp;
        public long timeSinceLastUpdate;

        @Override
        public String toString() {
            return String.format("[%s] %s: inventory=%d→%d, delta=%d, time=%dms",
                changeType, productId, previousInventory, currentInventory,
                delta, timeSinceLastUpdate);
        }
    }

    /**
     * Helper to create a sample product stream for testing
     */
    private static DataStream<Product> createProductStream(StreamExecutionEnvironment env) {
        return env.fromElements(
            createProduct("LAPTOP_001", "Laptop", 10, 999.99),
            createProduct("MOUSE_001", "Mouse", 50, 29.99),
            createProduct("LAPTOP_001", "Laptop", 8, 999.99),   // Sale: 10 → 8
            createProduct("LAPTOP_001", "Laptop", 15, 999.99),  // Restock: 8 → 15
            createProduct("LAPTOP_001", "Laptop", 15, 899.99),  // Price drop
            createProduct("MOUSE_001", "Mouse", 45, 29.99),     // Sale: 50 → 45
            createProduct("KEYBOARD_001", "Keyboard", 30, 79.99) // New product
        );
    }

    private static Product createProduct(String id, String name, int inventory, double price) {
        Product p = new Product();
        p.productId = id;
        p.name = name;
        p.inventory = inventory;
        p.price = price;
        p.category = "Electronics";
        return p;
    }
}

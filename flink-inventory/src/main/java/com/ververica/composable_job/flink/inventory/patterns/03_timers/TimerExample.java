package com.ververica.composable_job.flink.inventory.patterns.timers;

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
 * FLINK PATTERN: Timers (Processing Time & Event Time)
 *
 * PURPOSE:
 * Detect timeout conditions, schedule periodic actions, and implement time-based cleanup.
 * Timers allow you to trigger actions based on time progression, essential for:
 * - Detecting stale data (no updates for N time)
 * - Session timeout detection (user inactivity)
 * - Periodic reporting (emit snapshot every 5 minutes)
 * - State cleanup (remove old keys after TTL)
 *
 * KEY CONCEPTS:
 * 1. Processing Time Timers - Fire based on wall-clock time (machine time)
 *    - Use for: Timeouts, periodic tasks, cleanup
 *    - Behavior: Not deterministic, faster, simpler
 *
 * 2. Event Time Timers - Fire based on watermark progression (event timestamps)
 *    - Use for: Event-driven logic, accurate time windows
 *    - Behavior: Deterministic, replayable, handles late data
 *
 * 3. Timer Registration - Register timer in processElement()
 * 4. Timer Callback - onTimer() called when timer fires
 * 5. Timer Cleanup - Delete timer when no longer needed
 *
 * WHEN TO USE:
 * - Detect products with no inventory updates (stale data)
 * - Implement session windows with custom timeout
 * - Periodic aggregation and reporting
 * - State TTL and cleanup for memory management
 *
 * ALTERNATIVES:
 * - Window functions - Better for fixed-size windows
 * - State TTL - Simpler for automatic cleanup
 * - External scheduling - For cross-job coordination
 */
public class TimerExample {

    private static final Logger LOG = LoggerFactory.getLogger(TimerExample.class);

    // Configuration: Alert if no product update for 1 hour
    private static final long STALE_THRESHOLD_MS = 60 * 60 * 1000; // 1 hour

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(30000); // Checkpoint every 30 seconds

        // Simulate product update stream
        DataStream<Product> productStream = createProductStream(env);

        // Apply timer-based stale detection
        DataStream<StaleInventoryAlert> alerts = productStream
            .keyBy(product -> product.productId)  // Partition by product ID
            .process(new StaleInventoryDetector()) // Detect stale inventory with timers
            .name("Detect Stale Inventory");

        alerts.print();

        env.execute("Timer Pattern Example - Stale Inventory Detection");
    }

    /**
     * KeyedProcessFunction that uses timers to detect stale inventory.
     *
     * PATTERN EXPLANATION:
     * - Each product update RESETS the timer (cancels old, registers new)
     * - If no update arrives before timer fires, emit stale alert
     * - Timer is automatically cleaned up when it fires
     * - State tracks last update timestamp and timer timestamp
     *
     * TIMER LIFECYCLE:
     * 1. processElement() → Register timer for current_time + threshold
     * 2. New element arrives → Delete old timer, register new one
     * 3. Timer fires → onTimer() called → Emit alert
     * 4. Cleanup → State removed when key is deleted
     */
    public static class StaleInventoryDetector
            extends KeyedProcessFunction<String, Product, StaleInventoryAlert> {

        // State: Last time we saw an update for this product
        private transient ValueState<Long> lastUpdateTimeState;

        // State: Timestamp of currently registered timer (for cleanup)
        private transient ValueState<Long> timerState;

        // State: Last known product data (for alert details)
        private transient ValueState<Product> lastProductState;

        /**
         * Initialize state descriptors.
         * Called once per parallel instance when job starts.
         */
        @Override
        public void open(Configuration parameters) throws Exception {
            // State for tracking last update time
            ValueStateDescriptor<Long> lastUpdateDescriptor = new ValueStateDescriptor<>(
                "last-update-time",
                TypeInformation.of(Long.class)
            );
            lastUpdateTimeState = getRuntimeContext().getState(lastUpdateDescriptor);

            // State for tracking registered timer timestamp
            ValueStateDescriptor<Long> timerDescriptor = new ValueStateDescriptor<>(
                "timer-timestamp",
                TypeInformation.of(Long.class)
            );
            timerState = getRuntimeContext().getState(timerDescriptor);

            // State for storing last product data
            ValueStateDescriptor<Product> productDescriptor = new ValueStateDescriptor<>(
                "last-product",
                TypeInformation.of(Product.class)
            );
            lastProductState = getRuntimeContext().getState(productDescriptor);

            LOG.info("✅ Timer state initialized for task: {}",
                getRuntimeContext().getIndexOfThisSubtask());
        }

        /**
         * Process each product update.
         * RESETS the timer every time we see an update.
         */
        @Override
        public void processElement(
                Product product,
                Context ctx,
                Collector<StaleInventoryAlert> out) throws Exception {

            long currentTime = ctx.timerService().currentProcessingTime();

            // Delete existing timer if one is registered
            Long existingTimer = timerState.value();
            if (existingTimer != null) {
                ctx.timerService().deleteProcessingTimeTimer(existingTimer);
                LOG.debug("🔄 Cancelled old timer for product: {} (was at: {})",
                    product.productId, existingTimer);
            }

            // Register NEW timer: Fire if no update received within threshold
            long timerTimestamp = currentTime + STALE_THRESHOLD_MS;
            ctx.timerService().registerProcessingTimeTimer(timerTimestamp);

            // Save timer timestamp for future cleanup
            timerState.update(timerTimestamp);

            // Update last update time
            lastUpdateTimeState.update(currentTime);

            // Save product data for alert details
            lastProductState.update(product);

            LOG.info("⏰ Timer registered for product: {} - Will fire at: {} (in {} min)",
                product.productId,
                timerTimestamp,
                STALE_THRESHOLD_MS / 1000 / 60);
        }

        /**
         * Called when timer fires.
         * This means NO update was received within the threshold period.
         *
         * IMPORTANT:
         * - Timer fires exactly once
         * - Timer is automatically removed after firing
         * - onTimer() is called with the REGISTERED timestamp, not current time
         */
        @Override
        public void onTimer(
                long timestamp,
                OnTimerContext ctx,
                Collector<StaleInventoryAlert> out) throws Exception {

            // Retrieve state
            Long lastUpdateTime = lastUpdateTimeState.value();
            Product lastProduct = lastProductState.value();

            if (lastProduct == null) {
                LOG.warn("⚠️ Timer fired but no product data found for key: {}",
                    ctx.getCurrentKey());
                return;
            }

            long currentTime = ctx.timerService().currentProcessingTime();
            long staleDuration = currentTime - (lastUpdateTime != null ? lastUpdateTime : currentTime);

            // Emit stale inventory alert
            StaleInventoryAlert alert = new StaleInventoryAlert();
            alert.productId = lastProduct.productId;
            alert.productName = lastProduct.name;
            alert.lastInventory = lastProduct.inventory;
            alert.lastUpdateTime = lastUpdateTime;
            alert.alertTime = currentTime;
            alert.staleDurationMs = staleDuration;
            alert.severity = staleDuration > (STALE_THRESHOLD_MS * 2) ? "HIGH" : "MEDIUM";

            out.collect(alert);

            LOG.warn("🚨 STALE INVENTORY ALERT: {} - No update for {} minutes",
                lastProduct.productId,
                staleDuration / 1000 / 60);

            // Clean up state for this timer
            // NOTE: Keep product state in case updates resume
            timerState.clear();
        }
    }

    /**
     * Alert event representing stale inventory detection
     */
    public static class StaleInventoryAlert implements Serializable {
        public String productId;
        public String productName;
        public int lastInventory;
        public Long lastUpdateTime;
        public long alertTime;
        public long staleDurationMs;
        public String severity;

        @Override
        public String toString() {
            return String.format(
                "[%s] STALE INVENTORY: %s (%s) - Last update: %d min ago, Last inventory: %d",
                severity,
                productId,
                productName,
                staleDurationMs / 1000 / 60,
                lastInventory
            );
        }
    }

    /**
     * Helper to create a sample product stream for testing.
     * Simulates products with varying update frequencies.
     */
    private static DataStream<Product> createProductStream(StreamExecutionEnvironment env) {
        // Create products with different update patterns
        Product laptop1 = createProduct("LAPTOP_001", "Gaming Laptop", 10, 1499.99);
        Product laptop2 = createProduct("LAPTOP_001", "Gaming Laptop", 8, 1499.99);
        Product laptop3 = createProduct("LAPTOP_001", "Gaming Laptop", 5, 1399.99);

        Product mouse1 = createProduct("MOUSE_001", "Wireless Mouse", 50, 29.99);
        // Note: Mouse only updated once - should trigger stale alert

        Product keyboard1 = createProduct("KEYBOARD_001", "Mechanical Keyboard", 30, 129.99);
        Product keyboard2 = createProduct("KEYBOARD_001", "Mechanical Keyboard", 25, 129.99);

        return env.fromElements(
            laptop1,    // t=0
            mouse1,     // t=0 (will become stale)
            keyboard1,  // t=0
            laptop2,    // t=0+delta
            keyboard2,  // t=0+delta
            laptop3     // t=0+delta (keeps laptop fresh)
            // Mouse never updated again → Timer will fire
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

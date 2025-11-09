package com.ververica.composable_job.flink.inventory.patterns.side_outputs;

import com.ververica.composable_job.model.ecommerce.Product;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;

/**
 * FLINK PATTERN: Side Outputs (Multi-Way Data Routing)
 *
 * PURPOSE:
 * Route events to different streams based on business logic WITHOUT reprocessing data.
 * Essential for splitting streams efficiently while maintaining single processing path.
 *
 * KEY CONCEPTS:
 * 1. OutputTag defines a typed side output stream
 * 2. ProcessFunction can emit to multiple outputs in one pass
 * 3. Main output + multiple side outputs = efficient multi-way split
 * 4. Type-safe: Each output tag has its own type parameter
 *
 * WHEN TO USE:
 * - Split stream by condition (alerts, errors, normal flow)
 * - Route to different sinks (database, cache, notifications)
 * - Separate processing paths (batch vs real-time)
 * - Error handling (main path vs error path)
 *
 * WHY NOT JUST FILTER?
 * ❌ Multiple filters = process same data multiple times
 * ❌ .filter().filter().filter() = 3x processing
 * ✅ Side outputs = process once, route to many
 *
 * ALTERNATIVES:
 * - Multiple filters: Inefficient, reprocesses data N times
 * - Split operator (deprecated): Old approach, use side outputs instead
 * - Union after filters: Still processes data multiple times
 */
public class SideOutputExample {

    private static final Logger LOG = LoggerFactory.getLogger(SideOutputExample.class);

    // Define OutputTags for different alert types
    // IMPORTANT: OutputTags must be static final and have unique names

    // Side output 1: Products with low inventory (< 10 units)
    public static final OutputTag<InventoryAlert> LOW_STOCK_ALERTS =
        new OutputTag<InventoryAlert>("low-stock-alerts") {};

    // Side output 2: Products that are out of stock (= 0 units)
    public static final OutputTag<InventoryAlert> OUT_OF_STOCK_ALERTS =
        new OutputTag<InventoryAlert>("out-of-stock-alerts") {};

    // Side output 3: Products with significant price drops (>10% discount)
    public static final OutputTag<PriceAlert> PRICE_DROP_ALERTS =
        new OutputTag<PriceAlert>("price-drop-alerts") {};

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Create sample product stream
        DataStream<Product> productStream = createProductStream(env);

        // PATTERN: Single ProcessFunction routes to multiple outputs
        // This processes each product ONCE, but routes to MULTIPLE streams
        SingleOutputStreamOperator<Product> mainStream = productStream
            .process(new InventoryRouter())
            .name("Route Inventory Events");

        // Access side output streams
        DataStream<InventoryAlert> lowStockAlerts = mainStream.getSideOutput(LOW_STOCK_ALERTS);
        DataStream<InventoryAlert> outOfStockAlerts = mainStream.getSideOutput(OUT_OF_STOCK_ALERTS);
        DataStream<PriceAlert> priceDropAlerts = mainStream.getSideOutput(PRICE_DROP_ALERTS);

        // Process each stream independently
        mainStream
            .map(p -> "✅ Regular update: " + p.name + " (inventory=" + p.inventory + ")")
            .print("MAIN");

        lowStockAlerts
            .map(alert -> "⚠️  LOW STOCK: " + alert.productName + " (" + alert.currentInventory + " units)")
            .print("LOW-STOCK");

        outOfStockAlerts
            .map(alert -> "🚨 OUT OF STOCK: " + alert.productName)
            .print("OUT-OF-STOCK");

        priceDropAlerts
            .map(alert -> String.format("💰 PRICE DROP: %s: $%.2f → $%.2f (%.1f%% off)",
                alert.productName, alert.originalPrice, alert.newPrice, alert.discountPercent))
            .print("PRICE-DROP");

        env.execute("Side Output Pattern Example");
    }

    /**
     * ProcessFunction that routes products to different outputs based on business rules.
     *
     * PATTERN EXPLANATION:
     * - processElement() is called ONCE per record
     * - Can emit to main output (collect()) and side outputs (ctx.output())
     * - Each output can have a different type
     * - Routing logic is centralized in one place
     */
    public static class InventoryRouter extends ProcessFunction<Product, Product> {

        @Override
        public void processElement(
                Product product,
                Context ctx,
                Collector<Product> out) throws Exception {

            // ROUTE 1: Check for out of stock (highest priority)
            if (product.inventory == 0) {
                InventoryAlert alert = new InventoryAlert();
                alert.productId = product.productId;
                alert.productName = product.name;
                alert.alertType = "OUT_OF_STOCK";
                alert.currentInventory = 0;
                alert.threshold = 0;
                alert.timestamp = System.currentTimeMillis();

                // Emit to OUT_OF_STOCK side output
                ctx.output(OUT_OF_STOCK_ALERTS, alert);

                LOG.info("🚨 OUT OF STOCK alert: {}", product.name);
            }
            // ROUTE 2: Check for low stock
            else if (product.inventory < 10) {
                InventoryAlert alert = new InventoryAlert();
                alert.productId = product.productId;
                alert.productName = product.name;
                alert.alertType = "LOW_STOCK";
                alert.currentInventory = product.inventory;
                alert.threshold = 10;
                alert.timestamp = System.currentTimeMillis();

                // Emit to LOW_STOCK side output
                ctx.output(LOW_STOCK_ALERTS, alert);

                LOG.info("⚠️  LOW STOCK alert: {} ({} units)", product.name, product.inventory);
            }

            // ROUTE 3: Check for price drops (simulated with rating > 4.5)
            // In real scenario, you'd compare with previous price from state
            if (product.rating > 4.5 && product.price < 100) {
                double originalPrice = product.price * 1.15; // Simulate 15% discount
                double discountPercent = ((originalPrice - product.price) / originalPrice) * 100;

                if (discountPercent > 10) {
                    PriceAlert alert = new PriceAlert();
                    alert.productId = product.productId;
                    alert.productName = product.name;
                    alert.originalPrice = originalPrice;
                    alert.newPrice = product.price;
                    alert.discountPercent = discountPercent;
                    alert.timestamp = System.currentTimeMillis();

                    // Emit to PRICE_DROP side output
                    ctx.output(PRICE_DROP_ALERTS, alert);

                    LOG.info("💰 PRICE DROP alert: {} ({}% off)", product.name, (int)discountPercent);
                }
            }

            // ALWAYS emit to main output (regular processing continues)
            // This ensures all products flow through for normal processing
            out.collect(product);
        }
    }

    /**
     * Alert event for inventory issues
     */
    public static class InventoryAlert implements Serializable {
        public String productId;
        public String productName;
        public String alertType;
        public int currentInventory;
        public int threshold;
        public long timestamp;

        @Override
        public String toString() {
            return String.format("[%s] %s: inventory=%d (threshold=%d)",
                alertType, productName, currentInventory, threshold);
        }
    }

    /**
     * Alert event for price changes
     */
    public static class PriceAlert implements Serializable {
        public String productId;
        public String productName;
        public double originalPrice;
        public double newPrice;
        public double discountPercent;
        public long timestamp;

        @Override
        public String toString() {
            return String.format("PRICE_DROP: %s: $%.2f → $%.2f (%.1f%% off)",
                productName, originalPrice, newPrice, discountPercent);
        }
    }

    /**
     * Helper to create a sample product stream for testing
     */
    private static DataStream<Product> createProductStream(StreamExecutionEnvironment env) {
        return env.fromElements(
            // Regular inventory products
            createProduct("LAPTOP_001", "Gaming Laptop", 50, 1299.99, 4.2),
            createProduct("MOUSE_001", "Wireless Mouse", 100, 29.99, 4.0),

            // Low stock products (< 10)
            createProduct("KEYBOARD_001", "Mechanical Keyboard", 8, 89.99, 4.3),
            createProduct("MONITOR_001", "4K Monitor", 5, 399.99, 4.1),

            // Out of stock products (= 0)
            createProduct("HEADSET_001", "Gaming Headset", 0, 79.99, 4.6),
            createProduct("WEBCAM_001", "HD Webcam", 0, 49.99, 4.4),

            // Price drop candidates (high rating + low price)
            createProduct("CABLE_001", "USB-C Cable", 200, 12.99, 4.8),
            createProduct("ADAPTER_001", "Power Adapter", 150, 19.99, 4.7),

            // Mixed scenarios
            createProduct("TABLET_001", "Android Tablet", 3, 299.99, 4.5),  // Low stock
            createProduct("CHARGER_001", "Fast Charger", 0, 24.99, 4.9)     // Out of stock + price drop
        );
    }

    private static Product createProduct(String id, String name, int inventory, double price, double rating) {
        Product p = new Product();
        p.productId = id;
        p.name = name;
        p.inventory = inventory;
        p.price = price;
        p.rating = rating;
        p.category = "Electronics";
        p.tags = List.of("tech", "gadgets");
        return p;
    }
}

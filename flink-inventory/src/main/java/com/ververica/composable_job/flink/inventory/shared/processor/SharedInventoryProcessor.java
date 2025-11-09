package com.ververica.composable_job.flink.inventory.shared.processor;

import com.ververica.composable_job.model.ecommerce.Product;
import com.ververica.composable_job.flink.inventory.shared.model.AlertEvent;
import com.ververica.composable_job.flink.inventory.shared.model.InventoryEvent;
import com.ververica.composable_job.flink.inventory.shared.model.EventType;
import com.ververica.composable_job.flink.inventory.shared.model.OrderItemDeduction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CoProcessFunction that implements the core logic for the inventory management job.
 * It demonstrates several Flink patterns by processing two distinct input streams
 * (product updates and order deductions) against a single, shared state.
 *
 * This function is the heart of the following patterns:
 * - PATTERN 01: Multiple Sources & Co-Processing (by its very nature)
 * - PATTERN 02: Shared Keyed State (using ValueState)
 * - PATTERN 03: Timers (for stale detection)
 * - PATTERN 04: Side Outputs (for alerts)
 */
public class SharedInventoryProcessor extends CoProcessFunction<Product, OrderItemDeduction, InventoryEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(InventoryStateFunction.class);

    // PATTERN 04: Side Output Tags used to route different types of alerts.
    public static final OutputTag<AlertEvent> LOW_STOCK_TAG =
        new OutputTag<AlertEvent>("low-stock-alerts") {};

    public static final OutputTag<AlertEvent> OUT_OF_STOCK_TAG =
        new OutputTag<AlertEvent>("out-of-stock-alerts") {};

    public static final OutputTag<AlertEvent> PRICE_DROP_TAG =
        new OutputTag<AlertEvent>("price-drop-alerts") {};

    // Stale detection timeout (1 hour)
    private static final long STALE_TIMEOUT_MS = 60 * 60 * 1000;

    // PATTERN 02: Shared Keyed State for inventory, last update time, and the timer itself.
    private transient ValueState<Product> lastProductState;
    private transient ValueState<Long> lastUpdateTimeState;
    private transient ValueState<Long> timerState;

    @Override
    public void open(Configuration parameters) {
        // Initialize state descriptors
        lastProductState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-product", TypeInformation.of(Product.class))
        );

        lastUpdateTimeState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-update-time", TypeInformation.of(Long.class))
        );

        timerState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("timer", TypeInformation.of(Long.class))
        );

        LOG.info("InventoryStateFunction initialized on task {}",
            getRuntimeContext().getIndexOfThisSubtask());
    }

    /**
     * PATTERN 01: Processes the first input stream (Product updates).
     */
    @Override
    public void processElement1(Product newProduct, Context ctx, Collector<InventoryEvent> out) throws Exception {
        LOG.info(">>> (Shared Processor) Processing product update for '{}'", newProduct.productId);
        
        // PATTERN 02: Retrieve shared state for this product ID
        Product previousProduct = lastProductState.value();
        long currentTime = System.currentTimeMillis();
        Long previousUpdateTime = lastUpdateTimeState.value();

        // PATTERN 03: Delete old timer and register a new one to reset the stale clock.
        Long existingTimer = timerState.value();
        if (existingTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(existingTimer);
        }

        long newTimer = currentTime + STALE_TIMEOUT_MS;
        ctx.timerService().registerProcessingTimeTimer(newTimer);
        timerState.update(newTimer);

        // First time seeing this product?
        if (previousProduct == null) {
            handleNewProduct(newProduct, currentTime, out, ctx);
        } else {
            handleProductUpdate(previousProduct, newProduct, currentTime, previousUpdateTime, out, ctx);
        }

        // PATTERN 02: Update shared state with the latest product data and timestamp.
        lastProductState.update(newProduct);
        lastUpdateTimeState.update(currentTime);
    }

    /**
     * PATTERN 01: Processes the second input stream (Order deductions).
     */    
    @Override
    public void processElement2(OrderItemDeduction order, Context ctx, Collector<InventoryEvent> out) throws Exception {
        LOG.info(">>> (Shared Processor) Processing order for '{}'", order.productId);

        Product currentProduct = lastProductState.value();
        if (currentProduct == null) {
            LOG.warn("⚠️  (Shared Processor) Order for unknown product: {} (order: {})", order.productId, order.orderId);
            return;
        }

        int previousInventory = currentProduct.inventory;
        int newInventory = previousInventory - order.quantity;
        if (newInventory < 0) newInventory = 0;

        currentProduct.inventory = newInventory;
        
        // PATTERN 02: Update the shared state with the modified product.
        lastProductState.update(currentProduct);
        
        long currentTime = System.currentTimeMillis();
        lastUpdateTimeState.update(currentTime);
        
        // PATTERN 03: Reset the stale timer since an update occurred.
        Long existingTimer = timerState.value();
        if (existingTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(existingTimer);
        }
        long newTimer = currentTime + STALE_TIMEOUT_MS;
        ctx.timerService().registerProcessingTimeTimer(newTimer);
        timerState.update(newTimer);

        LOG.info("✅ Inventory deducted for {}: {} -> {}. New count: {}", order.productId, previousInventory, newInventory);
        
        InventoryEvent event = InventoryEvent.builder()
            .productId(order.productId)
            .productName(currentProduct.name)
            .eventType(EventType.INVENTORY_DECREASED)
            .previousInventory(previousInventory)
            .currentInventory(newInventory)
            .previousPrice(currentProduct.price)
            .currentPrice(currentProduct.price)
            .changeReason("Deducted " + order.quantity + " for order " + order.orderId)
            .build();
        out.collect(event);
        
        // PATTERN 04: Check for and emit alerts based on the new inventory level.
        if (newInventory == 0) {
            emitOutOfStockAlert(currentProduct, ctx);
        } else if (newInventory <= 10) {
            emitLowStockAlert(currentProduct, ctx);
        }
    }

    /**
     * PATTERN 03: Timer callback for stale inventory detection.
     */
    @Override
    public void onTimer(
            long timestamp,
            OnTimerContext ctx,
            Collector<InventoryEvent> out) throws Exception {

        Product product = lastProductState.value();
        Long lastUpdate = lastUpdateTimeState.value();

        if (product != null && lastUpdate != null) {
            long timeSinceUpdate = timestamp - lastUpdate;

            if (timeSinceUpdate >= STALE_TIMEOUT_MS) {
                LOG.warn("⏰ Stale inventory detected for {}: no updates for {} hours",
                    product.productId,
                    timeSinceUpdate / (1000.0 * 60 * 60));

                // Emit stale inventory event
                InventoryEvent staleEvent = InventoryEvent.builder()
                    .productId(product.productId)
                    .productName(product.name)
                    .eventType(EventType.STALE_INVENTORY)
                    .currentInventory(product.inventory)
                    .changeReason(String.format("No updates for %.1f hours",
                        timeSinceUpdate / (1000.0 * 60 * 60)))
                    .build();

                out.collect(staleEvent);
            }
        }
    }
    
    private void handleNewProduct(
            Product product,
            long currentTime,
            Collector<InventoryEvent> out,
            Context ctx) {

        LOG.info("🆕 New product: {} with inventory: {}", product.productId, product.inventory);

        InventoryEvent event = InventoryEvent.builder()
            .productId(product.productId)
            .productName(product.name)
            .eventType(EventType.PRODUCT_ADDED)
            .previousInventory(0)
            .currentInventory(product.inventory)
            .previousPrice(0.0)
            .currentPrice(product.price)
            .changeReason("New product added to catalog")
            .build();

        out.collect(event);

        // PATTERN 04: Check for alerts on initial product creation.
        if (product.inventory > 0 && product.inventory < 10) {
            emitLowStockAlert(product, ctx);
        } else if (product.inventory == 0) {
            emitOutOfStockAlert(product, ctx);
        }
    }

    private void handleProductUpdate(
            Product previous,
            Product current,
            long currentTime,
            Long previousUpdateTime,
            Collector<InventoryEvent> out,
            Context ctx) {

        long timeSinceLastUpdate = previousUpdateTime != null ?
            currentTime - previousUpdateTime : 0;

        // Check for inventory changes
        if (previous.inventory != current.inventory) {
            handleInventoryChange(previous, current, timeSinceLastUpdate, out, ctx);
        }

        // Check for price changes
        if (Math.abs(previous.price - current.price) > 0.01) {
            handlePriceChange(previous, current, out, ctx);
        }
    }

    private void handleInventoryChange(
            Product previous,
            Product current,
            long timeSinceLastUpdate,
            Collector<InventoryEvent> out,
            Context ctx) {

        int delta = current.inventory - previous.inventory;
        EventType eventType;
        String reason;

        if (current.inventory == 0) {
            eventType = EventType.OUT_OF_STOCK;
            reason = "Product is now out of stock";
            emitOutOfStockAlert(current, ctx);
        } else if (previous.inventory == 0 && current.inventory > 0) {
            eventType = EventType.BACK_IN_STOCK;
            reason = String.format("Product restocked with %d units", current.inventory);
        } else if (delta < 0) {
            eventType = EventType.INVENTORY_DECREASED;
            reason = String.format("%d units sold/removed", Math.abs(delta));
        } else {
            eventType = EventType.INVENTORY_INCREASED;
            reason = String.format("%d units added to inventory", delta);
        }

        LOG.info("📊 Inventory change for {}: {} → {} ({})",
            current.productId, previous.inventory, current.inventory, eventType);

        InventoryEvent event = InventoryEvent.builder()
            .productId(current.productId)
            .productName(current.name)
            .eventType(eventType)
            .previousInventory(previous.inventory)
            .currentInventory(current.inventory)
            .previousPrice(previous.price)
            .currentPrice(current.price)
            .changeReason(reason)
            .build();

        out.collect(event);

        // PATTERN 04: Emit low stock alert after an inventory change.
        if (current.inventory > 0 && current.inventory < 10) {
            emitLowStockAlert(current, ctx);
        }
    }

    private void handlePriceChange(
            Product previous,
            Product current,
            Collector<InventoryEvent> out,
            Context ctx) {

        double priceDiff = current.price - previous.price;
        double percentChange = (priceDiff / previous.price) * 100;

        LOG.info("💰 Price change for {}: ${} → ${} ({:.1f}%)",
            current.productId, previous.price, current.price, percentChange);

        InventoryEvent event = InventoryEvent.builder()
            .productId(current.productId)
            .productName(current.name)
            .eventType(EventType.PRICE_CHANGED)
            .previousInventory(previous.inventory)
            .currentInventory(current.inventory)
            .previousPrice(previous.price)
            .currentPrice(current.price)
            .changeReason(String.format("Price %s by $%.2f (%.1f%%)",
                priceDiff > 0 ? "increased" : "decreased",
                Math.abs(priceDiff),
                Math.abs(percentChange)))
            .build();

        out.collect(event);

        // PATTERN 04: Emit a price drop alert if the decrease is significant.
        if (percentChange <= -10.0) {
            emitPriceDropAlert(current, previous.price, percentChange, ctx);
        }
    }

    // PATTERN 04: Helper methods to emit alerts to the appropriate side output.
    private void emitLowStockAlert(Product product, Context ctx) {
        String severity = product.inventory <= 5 ? "CRITICAL" : "WARNING";

        AlertEvent alert = AlertEvent.builder()
            .alertType("LOW_STOCK")
            .productId(product.productId)
            .message(String.format("Low stock: only %d items remaining", product.inventory))
            .severity(severity)
            .build();

        ctx.output(LOW_STOCK_TAG, alert);
        LOG.warn("⚠️  LOW STOCK alert for {}: {} items", product.productId, product.inventory);
    }

    private void emitOutOfStockAlert(Product product, Context ctx) {
        AlertEvent alert = AlertEvent.builder()
            .alertType("OUT_OF_STOCK")
            .productId(product.productId)
            .message("Product is out of stock")
            .severity("CRITICAL")
            .build();

        ctx.output(OUT_OF_STOCK_TAG, alert);
        LOG.error("🚫 OUT OF STOCK alert for {}", product.productId);
    }

    private void emitPriceDropAlert(Product product, double oldPrice, double percentChange, Context ctx) {
        AlertEvent alert = AlertEvent.builder()
            .alertType("PRICE_DROP")
            .productId(product.productId)
            .message(String.format("Price dropped %.1f%% from $%.2f to $%.2f",
                Math.abs(percentChange), oldPrice, product.price))
            .severity("INFO")
            .build();

        ctx.output(PRICE_DROP_TAG, alert);
        LOG.info("💸 PRICE DROP alert for {}: {:.1f}% off", product.productId, Math.abs(percentChange));
    }
}
package com.ververica.composable_job.flink.inventory.shared.processor;

import com.ververica.composable_job.flink.inventory.shared.model.AlertEvent;
import com.ververica.composable_job.flink.inventory.shared.model.EventType;
import com.ververica.composable_job.flink.inventory.shared.model.InventoryEvent;
import com.ververica.composable_job.model.ecommerce.Product;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive KeyedProcessFunction demonstrating the combination of:
 *
 * - PATTERN 02: Keyed State - Track inventory per product
 * - PATTERN 03: Timers - Detect stale inventory (no updates for 1 hour)
 * - PATTERN 04: Side Outputs - Route different alert types
 *
 * STATE MANAGEMENT:
 * - lastProductState: Previous product snapshot (ValueState)
 * - lastUpdateTimeState: When product was last updated (ValueState)
 * - timerState: Registered timer timestamp for cleanup (ValueState)
 *
 * SIDE OUTPUTS:
 * - LOW_STOCK_TAG: Products with inventory < 10
 * - OUT_OF_STOCK_TAG: Products with inventory = 0
 * - PRICE_DROP_TAG: Price decreases > 10%
 *
 * TIMERS:
 * - Registers 1-hour processing time timer on each update
 * - Fires if product hasn't been updated
 * - Emits stale inventory alert
 *
 * MAIN OUTPUT:
 * - InventoryEvent for all inventory changes
 */
public class InventoryStateFunction
        extends KeyedProcessFunction<String, Product, InventoryEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(InventoryStateFunction.class);

    // PATTERN 04: Side Output Tags (must be public static for access outside)
    public static final OutputTag<AlertEvent> LOW_STOCK_TAG =
        new OutputTag<AlertEvent>("low-stock-alerts") {};

    public static final OutputTag<AlertEvent> OUT_OF_STOCK_TAG =
        new OutputTag<AlertEvent>("out-of-stock-alerts") {};

    public static final OutputTag<AlertEvent> PRICE_DROP_TAG =
        new OutputTag<AlertEvent>("price-drop-alerts") {};

    // Stale detection timeout (1 hour)
    private static final long STALE_TIMEOUT_MS = 60 * 60 * 1000;

    // PATTERN 02: Keyed State
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

    @Override
    public void processElement(
            Product currentProduct,
            Context ctx,
            Collector<InventoryEvent> out) throws Exception {

        // PATTERN 02: Retrieve state for this product ID
        Product previousProduct = lastProductState.value();
        Long previousUpdateTime = lastUpdateTimeState.value();
        long currentTime = System.currentTimeMillis();

        // PATTERN 03: Delete old timer and register new one
        Long existingTimer = timerState.value();
        if (existingTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(existingTimer);
        }

        long newTimer = currentTime + STALE_TIMEOUT_MS;
        ctx.timerService().registerProcessingTimeTimer(newTimer);
        timerState.update(newTimer);

        // First time seeing this product?
        if (previousProduct == null) {
            handleNewProduct(currentProduct, currentTime, out, ctx);
        } else {
            handleProductUpdate(previousProduct, currentProduct, currentTime, previousUpdateTime, out, ctx);
        }

        // PATTERN 02: Update state
        lastProductState.update(currentProduct);
        lastUpdateTimeState.update(currentTime);
    }

    /**
     * PATTERN 03: Timer callback for stale detection
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

        // PATTERN 04: Check for initial low stock
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

        // PATTERN 04: Emit low stock alert
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

        // PATTERN 04: Emit price drop alert if significant decrease
        if (percentChange <= -10.0) {
            emitPriceDropAlert(current, previous.price, percentChange, ctx);
        }
    }

    // PATTERN 04: Side Output Emission Methods

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

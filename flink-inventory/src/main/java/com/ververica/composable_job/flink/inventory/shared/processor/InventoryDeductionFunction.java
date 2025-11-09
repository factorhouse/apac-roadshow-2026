package com.ververica.composable_job.flink.inventory.shared.processor;

import com.ververica.composable_job.flink.inventory.shared.model.EventType;
import com.ververica.composable_job.flink.inventory.shared.model.InventoryEvent;
import com.ververica.composable_job.flink.inventory.shared.model.OrderItemDeduction;
import com.ververica.composable_job.model.ecommerce.Product;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inventory Deduction Function - Deducts inventory when orders are placed
 *
 * This function processes order item deductions and updates the product inventory state.
 *
 * STATE MANAGEMENT:
 * - productState: Current product information (ValueState<Product>)
 *
 * PATTERN: Keyed State for Per-Product Inventory Tracking with Order Deductions
 *
 * KEY CONCEPT:
 * - Key: productId
 * - State: Product (with current inventory level)
 * - Input: OrderItemDeduction (orderId, productId, quantity)
 * - Output: InventoryEvent (inventory decreased)
 *
 * BUSINESS LOGIC:
 * 1. Check if product exists in state
 * 2. Check if sufficient inventory available
 * 3. Deduct quantity from inventory
 * 4. Emit inventory event
 * 5. Handle out-of-stock scenarios
 */
public class InventoryDeductionFunction
        extends KeyedProcessFunction<String, OrderItemDeduction, InventoryEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryDeductionFunction.class);

    private transient ValueState<Product> productState;

    @Override
    public void open(Configuration parameters) {
        productState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("product", TypeInformation.of(Product.class))
        );
    }

    @Override
    public void processElement(
            OrderItemDeduction order,
            Context ctx,
            Collector<InventoryEvent> out) throws Exception {

        Product product = productState.value();

        if (product == null) {
            LOG.warn("⚠️  Order for unknown product: {} (order: {})", order.productId, order.orderId);
            // Emit event for unknown product
            InventoryEvent event = InventoryEvent.builder()
                .productId(order.productId)
                .productName("Unknown Product")
                .eventType(EventType.INVENTORY_DECREASED)
                .previousInventory(0)
                .currentInventory(0)
                .changeReason(String.format("Order %s: Product not found in inventory", order.orderId))
                .build();
            out.collect(event);
            return;
        }

        int previousInventory = product.inventory;
        int newInventory = previousInventory - order.quantity;

        // Check for insufficient inventory
        if (newInventory < 0) {
            LOG.error("🚫 Insufficient inventory for {}: ordered={}, available={}",
                product.productId, order.quantity, previousInventory);

            // Still process the order but flag it
            newInventory = 0; // Set to zero instead of negative

            InventoryEvent event = InventoryEvent.builder()
                .productId(product.productId)
                .productName(product.name)
                .eventType(EventType.OUT_OF_STOCK)
                .previousInventory(previousInventory)
                .currentInventory(0)
                .previousPrice(product.price)
                .currentPrice(product.price)
                .changeReason(String.format("Order %s: Insufficient inventory (ordered: %d, available: %d)",
                    order.orderId, order.quantity, previousInventory))
                .build();
            out.collect(event);

        } else {
            // Normal inventory deduction
            LOG.info("📉 Inventory deduction: {} ({} → {}) for order {}",
                product.productId, previousInventory, newInventory, order.orderId);

            EventType eventType = newInventory == 0
                ? EventType.OUT_OF_STOCK
                : EventType.INVENTORY_DECREASED;

            InventoryEvent event = InventoryEvent.builder()
                .productId(product.productId)
                .productName(product.name)
                .eventType(eventType)
                .previousInventory(previousInventory)
                .currentInventory(newInventory)
                .previousPrice(product.price)
                .currentPrice(product.price)
                .changeReason(String.format("Order %s: %d units sold", order.orderId, order.quantity))
                .build();
            out.collect(event);
        }

        // Update state with new inventory
        product.inventory = newInventory;
        productState.update(product);
    }
}

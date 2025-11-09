package com.ververica.composable_job.flink.inventory.shared.model;

/**
 * Represents an inventory deduction from an order
 *
 * This simple POJO is used to deduct inventory when orders are placed.
 */
public class OrderItemDeduction {
    public String orderId;
    public String productId;
    public int quantity;
    public long timestamp;

    public OrderItemDeduction() {
    }

    public OrderItemDeduction(String orderId, String productId, int quantity, long timestamp) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("OrderItemDeduction{orderId='%s', productId='%s', quantity=%d}",
            orderId, productId, quantity);
    }
}

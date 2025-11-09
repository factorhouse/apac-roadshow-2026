package com.ververica.composable_job.flink.inventory.shared.model;

import java.io.Serializable;

/**
 * Enum representing different types of inventory events.
 * Used to categorize events in the inventory management system.
 */
public enum EventType implements Serializable {
    /**
     * A new product has been added to the inventory
     */
    PRODUCT_ADDED,

    /**
     * An existing product's details have been updated
     */
    PRODUCT_UPDATED,

    /**
     * Inventory quantity has increased (e.g., restocking)
     */
    INVENTORY_INCREASED,

    /**
     * Inventory quantity has decreased (e.g., sale, damage)
     */
    INVENTORY_DECREASED,

    /**
     * Product is now out of stock
     */
    OUT_OF_STOCK,

    /**
     * Product is back in stock after being unavailable
     */
    BACK_IN_STOCK,

    /**
     * Product price has changed
     */
    PRICE_CHANGED,

    /**
     * Low stock alert triggered
     */
    LOW_STOCK_ALERT,

    /**
     * Inventory has not moved for a configured time period
     */
    STALE_INVENTORY,

    /**
     * A sale or promotion has started for the product
     */
    SALE_STARTED
}

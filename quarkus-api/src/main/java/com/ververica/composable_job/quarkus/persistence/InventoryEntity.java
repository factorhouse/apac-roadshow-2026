package com.ververica.composable_job.quarkus.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA Entity for Inventory Table
 *
 * Maps to the PostgreSQL `inventory` table created in postgres-init.sql
 * This entity is optionally used for inventory sync (not currently used in CDC flow)
 */
@Entity
@Table(name = "inventory")
public class InventoryEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    public Long inventoryId;

    @Column(name = "product_id", length = 50, nullable = false, unique = true)
    public String productId;

    @Column(name = "warehouse_location", length = 100)
    public String warehouseLocation;

    @Column(name = "quantity_on_hand")
    public Integer quantityOnHand;

    @Column(name = "quantity_reserved")
    public Integer quantityReserved;

    // Note: quantity_available is a GENERATED column in PostgreSQL, not mapped here
    // It's calculated as: quantity_on_hand - quantity_reserved

    @Column(name = "reorder_point")
    public Integer reorderPoint;

    @Column(name = "reorder_quantity")
    public Integer reorderQuantity;

    @Column(name = "last_restock_date")
    public Instant lastRestockDate;

    @Column(name = "last_updated")
    public Instant lastUpdated;

    public InventoryEntity() {
    }

    public InventoryEntity(String productId, String warehouseLocation,
                          Integer quantityOnHand, Integer quantityReserved,
                          Integer reorderPoint, Integer reorderQuantity) {
        this.productId = productId;
        this.warehouseLocation = warehouseLocation;
        this.quantityOnHand = quantityOnHand;
        this.quantityReserved = quantityReserved;
        this.reorderPoint = reorderPoint;
        this.reorderQuantity = reorderQuantity;
        this.lastUpdated = Instant.now();
    }

    public static InventoryEntity findByProductId(String productId) {
        return find("productId", productId).firstResult();
    }

    public Integer getQuantityAvailable() {
        return (quantityOnHand != null && quantityReserved != null)
            ? quantityOnHand - quantityReserved
            : 0;
    }
}

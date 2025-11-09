package com.ververica.composable_job.quarkus.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * JPA Entity for Order Items Table
 *
 * Maps to the PostgreSQL `order_items` table created in postgres-init.sql
 * This entity is used for CDC (Change Data Capture) - Flink CDC will read from this table
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    public Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    public OrderEntity order;

    @Column(name = "product_id", length = 50, nullable = false)
    public String productId;

    @Column(name = "quantity", nullable = false)
    public Integer quantity;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    public BigDecimal unitPrice;

    @Column(name = "discount", precision = 10, scale = 2)
    public BigDecimal discount;

    @Column(name = "line_total", precision = 10, scale = 2, nullable = false)
    public BigDecimal lineTotal;

    public OrderItemEntity() {
    }

    public OrderItemEntity(String productId, Integer quantity, BigDecimal unitPrice,
                          BigDecimal discount, BigDecimal lineTotal) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discount = discount;
        this.lineTotal = lineTotal;
    }

    public static java.util.List<OrderItemEntity> findByOrderId(String orderId) {
        return list("order.orderId", orderId);
    }
}

package com.ververica.composable_job.quarkus.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity for Orders Table
 *
 * Maps to the PostgreSQL `orders` table created in postgres-init.sql
 * This entity is used for CDC (Change Data Capture) - Flink CDC will read from this table
 */
@Entity
@Table(name = "orders")
public class OrderEntity extends PanacheEntityBase {

    @Id
    @Column(name = "order_id", length = 50)
    public String orderId;

    @Column(name = "customer_id", length = 50, nullable = false)
    public String customerId;

    @Column(name = "order_date")
    public Instant orderDate;

    @Column(name = "status", length = 50)
    public String status;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    public BigDecimal subtotal;

    @Column(name = "tax", precision = 10, scale = 2)
    public BigDecimal tax;

    @Column(name = "shipping", precision = 10, scale = 2)
    public BigDecimal shipping;

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    public BigDecimal total;

    @Column(name = "payment_method", length = 50)
    public String paymentMethod;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    public String shippingAddress;

    @Column(name = "tracking_number", length = 100)
    public String trackingNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    public String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<OrderItemEntity> items = new ArrayList<>();

    public OrderEntity() {
    }

    public OrderEntity(String orderId, String customerId, Instant orderDate, String status,
                      BigDecimal subtotal, BigDecimal tax, BigDecimal shipping, BigDecimal total,
                      String paymentMethod, String shippingAddress) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.orderDate = orderDate;
        this.status = status;
        this.subtotal = subtotal;
        this.tax = tax;
        this.shipping = shipping;
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.shippingAddress = shippingAddress;
    }

    public void addItem(OrderItemEntity item) {
        items.add(item);
        item.order = this;
    }

    public static OrderEntity findByOrderId(String orderId) {
        return find("orderId", orderId).firstResult();
    }
}

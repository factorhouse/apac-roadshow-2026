package com.ververica.composable_job.model.ecommerce;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ShoppingCart implements Serializable {
    public String cartId;
    public String sessionId;
    public String userId;
    public List<CartItem> items;
    public double totalAmount;
    public long createdAt;
    public long updatedAt;
    public String status;

    public ShoppingCart() {
        this.items = new ArrayList<>();
    }

    public ShoppingCart(String cartId, String sessionId, String userId, long createdAt) {
        this.cartId = cartId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.items = new ArrayList<>();
        this.totalAmount = 0.0;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = "ACTIVE";
    }

    public void addItem(CartItem item) {
        items.add(item);
        recalculateTotal();
        this.updatedAt = System.currentTimeMillis();
    }

    public void removeItem(String productId) {
        items.removeIf(item -> item.productId.equals(productId));
        recalculateTotal();
        this.updatedAt = System.currentTimeMillis();
    }

    public void updateItemQuantity(String productId, int quantity) {
        items.stream()
            .filter(item -> item.productId.equals(productId))
            .findFirst()
            .ifPresent(item -> {
                item.updateQuantity(quantity);
                recalculateTotal();
                this.updatedAt = System.currentTimeMillis();
            });
    }

    public void upsertItem(CartItem newItem) {
        Optional<CartItem> existingItemOpt = this.items.stream()
                .filter(item -> item.productId.equals(newItem.productId))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            existingItemOpt.get().updateQuantity(newItem.quantity);
        } else {
            this.items.add(newItem);
        }
        recalculateTotal();
        this.updatedAt = System.currentTimeMillis();
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
            .mapToDouble(item -> item.subtotal)
            .sum();
    }
}
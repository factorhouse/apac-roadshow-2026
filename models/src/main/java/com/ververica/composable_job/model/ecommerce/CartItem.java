package com.ververica.composable_job.model.ecommerce;

import java.io.Serializable;

public class CartItem implements Serializable {
    public String productId;
    public String productName;
    public double price;
    public int quantity;
    public double subtotal;
    public String imageUrl;
    public long addedAt;

    public CartItem() {
    }

    public CartItem(String productId, String productName, double price, 
                   int quantity, String imageUrl, long addedAt) {
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = price * quantity;
        this.imageUrl = imageUrl;
        this.addedAt = addedAt;
    }

    public void updateQuantity(int newQuantity) {
        this.quantity = newQuantity;
        this.subtotal = this.price * newQuantity;
    }
}
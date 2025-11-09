package com.ververica.composable_job.model.ecommerce;

import java.io.Serializable;
import java.util.List;

public class Order implements Serializable {
    public String orderId;
    public String userId;
    public String sessionId;
    public List<CartItem> items;
    public double subtotal;
    public double tax;
    public double shipping;
    public double totalAmount;
    public String status;
    public ShippingAddress shippingAddress;
    public PaymentInfo paymentInfo;
    public long createdAt;
    public long updatedAt;
    public long timestamp;

    public Order() {
    }

    public Order(String orderId, String userId, String sessionId, List<CartItem> items,
                double subtotal, double tax, double shipping, long createdAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.items = items;
        this.subtotal = subtotal;
        this.tax = tax;
        this.shipping = shipping;
        this.totalAmount = subtotal + tax + shipping;
        this.status = "PENDING";
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.timestamp = createdAt;
    }

    public static class ShippingAddress implements Serializable {
        public String name;
        public String street;
        public String city;
        public String state;
        public String zipCode;
        public String country;

        public ShippingAddress() {
        }

        public ShippingAddress(String name, String street, String city, 
                              String state, String zipCode, String country) {
            this.name = name;
            this.street = street;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
            this.country = country;
        }
    }

    public static class PaymentInfo implements Serializable {
        public String paymentMethod;
        public String transactionId;
        public String status;
        public long processedAt;

        public PaymentInfo() {
        }

        public PaymentInfo(String paymentMethod, String transactionId, 
                          String status, long processedAt) {
            this.paymentMethod = paymentMethod;
            this.transactionId = transactionId;
            this.status = status;
            this.processedAt = processedAt;
        }
    }
}
package com.ververica.composable_job.model.ecommerce;

import java.io.Serializable;
import java.util.Map;

public class EcommerceEvent implements Serializable {
    public String eventId;
    public String sessionId;
    public String userId;
    public EcommerceEventType eventType;
    public long timestamp;
    public String productId;
    public String categoryId;
    public double value;
    public int quantity;
    public String searchQuery;
    public Map<String, Object> metadata;
    public String referrer;
    public String userAgent;
    public String ipAddress;
    public String page;
    public String title;
    public String productName;
    public int resultCount;
    public String recommendationId;
    public double cartTotal;
    public int itemCount;
    public String orderId;
    public String categoryName;
    public double totalAmount;
    public String category;

    public EcommerceEvent() {
    }

    public EcommerceEvent(String eventId, String sessionId, String userId, 
                         EcommerceEventType eventType, long timestamp) {
        this.eventId = eventId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    public EcommerceEvent withProduct(String productId, double value, int quantity) {
        this.productId = productId;
        this.value = value;
        this.quantity = quantity;
        return this;
    }

    public EcommerceEvent withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    public EcommerceEvent withTracking(String referrer, String userAgent, String ipAddress) {
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        return this;
    }
}
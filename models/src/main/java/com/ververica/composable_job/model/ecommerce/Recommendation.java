package com.ververica.composable_job.model.ecommerce;

import java.io.Serializable;
import java.util.List;

public class Recommendation implements Serializable {
    public String recommendationId;
    public String userId;
    public String sessionId;
    public List<String> productIds;
    public String recommendationType;
    public double confidence;
    public String reason;
    public long timestamp;

    public Recommendation() {
    }

    public Recommendation(String recommendationId, String userId, String sessionId,
                         List<String> productIds, String recommendationType,
                         double confidence, String reason, long timestamp) {
        this.recommendationId = recommendationId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.productIds = productIds;
        this.recommendationType = recommendationType;
        this.confidence = confidence;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public enum Type {
        FREQUENTLY_BOUGHT_TOGETHER,
        SIMILAR_ITEMS,
        TRENDING_NOW,
        PERSONALIZED,
        NEXT_BEST_OFFER,
        ABANDONED_CART_RECOVERY
    }
}
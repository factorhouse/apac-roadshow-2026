package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.model.ecommerce.EcommerceEvent;
import com.ververica.composable_job.model.ecommerce.EcommerceEventType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class EcommerceEventService {
    
    private final Map<String, AtomicLong> eventCounters = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> productViews = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> categoryViews = new ConcurrentHashMap<>();
    
    public void processEvent(EcommerceEvent event) {
        // Track event counts
        String eventKey = event.eventType.toString();
        eventCounters.computeIfAbsent(eventKey, k -> new AtomicLong(0)).incrementAndGet();
        
        // Track product views
        if (event.eventType == EcommerceEventType.PRODUCT_VIEW && event.productId != null) {
            productViews.computeIfAbsent(event.userId, k -> new ConcurrentHashMap<>())
                .merge(event.productId, 1, Integer::sum);
        }
        
        // Track category browsing
        if (event.eventType == EcommerceEventType.CATEGORY_BROWSE && event.categoryId != null) {
            categoryViews.computeIfAbsent(event.userId, k -> new ConcurrentHashMap<>())
                .merge(event.categoryId, 1, Integer::sum);
        }
        
        Log.debugf("Processed event: %s for user: %s", event.eventType, event.userId);
    }
    
    public Map<String, Integer> getUserProductViews(String userId) {
        return productViews.getOrDefault(userId, new ConcurrentHashMap<>());
    }
    
    public Map<String, Integer> getUserCategoryViews(String userId) {
        return categoryViews.getOrDefault(userId, new ConcurrentHashMap<>());
    }
    
    public long getEventCount(EcommerceEventType eventType) {
        return eventCounters.getOrDefault(eventType.toString(), new AtomicLong(0)).get();
    }
}
package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.ecommerce.*;
import com.ververica.composable_job.quarkus.kafka.streams.CacheQueryService;
import com.ververica.composable_job.quarkus.websocket.WebsocketEmitter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/ecommerce/{sessionId}/{userId}")
@ApplicationScoped
public class EcommerceWebsocket {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, List<EcommerceEvent>> sessionEvents = new ConcurrentHashMap<>();
    private static final Map<String, ShoppingCart> activeCarts = new ConcurrentHashMap<>();

    @Channel("ecommerce_events")
    Emitter<String> eventEmitter;

    @Inject
    WebSocketConnection connection;

    @Inject
    OpenConnections connections;

    @Inject
    CacheQueryService cacheQueryService;

    @Inject
    WebsocketEmitter websocketEmitter;

    @Inject
    EcommerceEventService eventService;

    @OnOpen(broadcast = false)
    public void onOpen() {
        String sessionId = connection.pathParam("sessionId");
        String userId = connection.pathParam("userId");
        
        Log.infof("E-commerce WebSocket opened for session: %s, user: %s", sessionId, userId);
        
        // Send initial recommendations
        sendPersonalizedRecommendations(userId, sessionId);
        
        // Send cart state if exists
        ShoppingCart cart = activeCarts.get(sessionId);
        if (cart != null) {
            try {
                ProcessingEvent<ShoppingCart> cartEvent = ProcessingEvent.ofCart(cart);
                connection.sendTextAndAwait(MAPPER.writeValueAsString(cartEvent));
            } catch (JsonProcessingException e) {
                Log.error("Failed to send cart state", e);
            }
        }
    }

    @OnClose
    public void onClose() {
        String sessionId = connection.pathParam("sessionId");
        Log.infof("E-commerce WebSocket closed for session: %s", sessionId);
    }

    @OnTextMessage(broadcast = false)
    public void onMessage(String message) {
        try {
            Map<String, Object> data = MAPPER.readValue(message, Map.class);
            String type = (String) data.get("type");
            
            switch (type) {
                case "CART_UPDATE":
                    handleCartUpdate(data);
                    break;
                case "EVENT":
                    handleEvent(data);
                    break;
                case "GET_RECOMMENDATIONS":
                    sendPersonalizedRecommendations(
                        connection.pathParam("userId"),
                        connection.pathParam("sessionId")
                    );
                    break;
                default:
                    Log.warnf("Unknown message type: %s", type);
            }
        } catch (Exception e) {
            Log.error("Failed to process WebSocket message", e);
        }
    }

    @Incoming("ecommerce_processing_fanout")
    @Blocking
    public void consumeProcessedEvent(String message) {
        try {
            ProcessingEvent<?> event = MAPPER.readValue(message, ProcessingEvent.class);

            // Route events to appropriate connections
            switch (event.eventType) {
                case RECOMMENDATION:
                    broadcastToUser(event.toUserId, message);
                    break;
                case SHOPPING_CART:
                    broadcastToSession(event.key, message);
                    break;
                case PRODUCT_UPDATE:
                    // Broadcast to all connections
                    websocketEmitter.emmit(message);
                    break;
            }
        } catch (JsonProcessingException e) {
            Log.error("Failed to process fanout message", e);
        }
    }

    /**
     * Listen for recommendations from Flink basket analysis job.
     * These come from the product-recommendations Kafka topic via RecommendationConsumer.
     */
    @Incoming("websocket_fanout")
    @Blocking
    public void consumeWebsocketFanout(String message) {
        try {
            Map<String, Object> event = MAPPER.readValue(message, Map.class);
            String eventType = (String) event.get("eventType");

            // Handle basket recommendations from Flink
            if ("BASKET_RECOMMENDATION".equals(eventType)) {
                String userId = (String) event.get("toUserId");
                String sessionId = (String) event.get("key");

                Log.infof("📨 Broadcasting Flink recommendation: session=%s, user=%s", sessionId, userId);

                // Send to specific user or session
                if (userId != null) {
                    broadcastToUser(userId, message);
                } else if (sessionId != null) {
                    broadcastToSession(sessionId, message);
                } else {
                    // Fallback: broadcast to all
                    websocketEmitter.emmit(message);
                }
            }
        } catch (JsonProcessingException e) {
            Log.error("Failed to process websocket fanout message", e);
        }
    }

    @Scheduled(every = "30s")
    public void generateRecommendations() {
        connections.stream().forEach(conn -> {
            String userId = conn.pathParam("userId");
            String sessionId = conn.pathParam("sessionId");
            
            // Get user's recent events
            List<EcommerceEvent> events = sessionEvents.getOrDefault(sessionId, new ArrayList<>());
            
            if (!events.isEmpty()) {
                // Generate recommendations based on recent activity
                Recommendation recommendation = generateRecommendation(userId, sessionId, events);
                
                try {
                    ProcessingEvent<Recommendation> recEvent = ProcessingEvent.ofRecommendation(recommendation);
                    String json = MAPPER.writeValueAsString(recEvent);
                    
                    // Send to Kafka for processing
                    eventEmitter.send(json);
                    
                    // Send directly to user
                    conn.sendTextAndAwait(json);
                } catch (JsonProcessingException e) {
                    Log.error("Failed to send recommendation", e);
                }
            }
        });
    }

    private void handleCartUpdate(Map<String, Object> data) throws JsonProcessingException {
        String sessionId = connection.pathParam("sessionId");
        String action = (String) data.get("action");
        Map<String, Object> payload = (Map<String, Object>) data.get("payload");
        
        ShoppingCart cart = activeCarts.computeIfAbsent(sessionId, 
            k -> new ShoppingCart(UUID.randomUUID().toString(), sessionId, 
                                connection.pathParam("userId"), System.currentTimeMillis()));
        
        switch (action) {
            case "ADD":
                CartItem item = MAPPER.convertValue(payload, CartItem.class);
                cart.addItem(item);
                break;
            case "REMOVE":
                String productId = (String) payload.get("productId");
                cart.removeItem(productId);
                break;
            case "UPDATE_QUANTITY":
                String prodId = (String) payload.get("productId");
                Integer quantity = (Integer) payload.get("quantity");
                cart.updateItemQuantity(prodId, quantity);
                break;
            case "CLEAR":
                cart.items.clear();
                cart.totalAmount = 0;
                break;
        }
        
        // Send updated cart to Kafka
        ProcessingEvent<ShoppingCart> cartEvent = ProcessingEvent.ofCart(cart);
        eventEmitter.send(MAPPER.writeValueAsString(cartEvent));
        
        // Broadcast to all sessions of the same user
        broadcastToSession(sessionId, MAPPER.writeValueAsString(cartEvent));
    }

    private void handleEvent(Map<String, Object> data) throws JsonProcessingException {
        EcommerceEvent event = MAPPER.convertValue(data, EcommerceEvent.class);
        String sessionId = connection.pathParam("sessionId");
        
        // Store event for session
        sessionEvents.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(event);
        
        // Send to Kafka
        ProcessingEvent<EcommerceEvent> processingEvent = ProcessingEvent.ofEcommerce(event);
        eventEmitter.send(MAPPER.writeValueAsString(processingEvent));
        
        // Process event
        eventService.processEvent(event);
    }

    private void sendPersonalizedRecommendations(String userId, String sessionId) {
        List<EcommerceEvent> events = sessionEvents.getOrDefault(sessionId, new ArrayList<>());
        Recommendation recommendation = generateRecommendation(userId, sessionId, events);
        
        try {
            ProcessingEvent<Recommendation> recEvent = ProcessingEvent.ofRecommendation(recommendation);
            connection.sendTextAndAwait(MAPPER.writeValueAsString(recEvent));
        } catch (JsonProcessingException e) {
            Log.error("Failed to send recommendations", e);
        }
    }

    private Recommendation generateRecommendation(String userId, String sessionId, List<EcommerceEvent> events) {
        // Simple recommendation logic based on recent events
        Set<String> viewedProducts = new HashSet<>();
        Set<String> categories = new HashSet<>();
        
        for (EcommerceEvent event : events) {
            if (event.productId != null) {
                viewedProducts.add(event.productId);
            }
            if (event.categoryId != null) {
                categories.add(event.categoryId);
            }
        }
        
        // Generate product recommendations (mock for demo)
        List<String> recommendedProducts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String productId = "prod_" + (new Random().nextInt(100) + 1);
            if (!viewedProducts.contains(productId)) {
                recommendedProducts.add(productId);
            }
        }
        
        String recommendationType = events.isEmpty() ? "TRENDING_NOW" : "PERSONALIZED";
        String reason = events.isEmpty() ? "Trending products" : "Based on your recent activity";
        
        return new Recommendation(
            UUID.randomUUID().toString(),
            userId,
            sessionId,
            recommendedProducts,
            recommendationType,
            0.75 + new Random().nextDouble() * 0.25,
            reason,
            System.currentTimeMillis()
        );
    }

    private void broadcastToUser(String userId, String message) {
        connections.stream()
            .filter(conn -> userId.equals(conn.pathParam("userId")))
            .forEach(conn -> conn.sendTextAndAwait(message));
    }

    private void broadcastToSession(String sessionId, String message) {
        connections.stream()
            .filter(conn -> sessionId.equals(conn.pathParam("sessionId")))
            .forEach(conn -> conn.sendTextAndAwait(message));
    }
}
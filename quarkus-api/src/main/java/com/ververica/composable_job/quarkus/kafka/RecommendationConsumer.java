package com.ververica.composable_job.quarkus.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ververica.composable_job.quarkus.websocket.WebsocketEmitter;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes product recommendations from the Flink basket analysis job.
 *
 * This consumer:
 * 1. Listens to 'product-recommendations' Kafka topic (output from Flink)
 * 2. Parses recommendation events
 * 3. Routes them to WebSocket clients based on sessionId/userId
 *
 * FLINK INTEGRATION:
 * - Receives RecommendationEvent objects from BasketAnalysisJobRefactored
 * - Recommendation types: BASKET_BASED, VIEW_BASED, SESSION_BASED
 * - Contains confidence scores from pattern mining (association rules)
 */
@ApplicationScoped
public class RecommendationConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    WebsocketEmitter websocketEmitter;

    /**
     * Process recommendations from Flink basket analysis job.
     *
     * Expected JSON format from Flink:
     * {
     *   "sessionId": "sess_123",
     *   "userId": "user_456",
     *   "recommendationType": "BASKET_BASED",
     *   "recommendedProducts": ["prod_1", "prod_2", "prod_3"],
     *   "confidence": 0.75,
     *   "timestamp": 1234567890,
     *   "triggerEvent": "ADD_TO_CART",
     *   "context": {"basketSize": "3"}
     * }
     */
    @Incoming("product-recommendations")
    @Blocking
    public void consumeRecommendation(String message) {
        try {
            Log.infof("📦 Received recommendation from Flink: %s",
                message.substring(0, Math.min(200, message.length())));

            // Parse the recommendation JSON
            Map<String, Object> recommendation = MAPPER.readValue(message, Map.class);

            // Create ProcessingEvent wrapper for WebSocket
            Map<String, Object> websocketEvent = new HashMap<>();
            websocketEvent.put("eventType", "BASKET_RECOMMENDATION");
            websocketEvent.put("payload", recommendation);
            websocketEvent.put("timestamp", System.currentTimeMillis());

            // Extract routing info
            String sessionId = (String) recommendation.get("sessionId");
            String userId = (String) recommendation.get("userId");

            // Set routing key for targeted delivery
            if (userId != null) {
                websocketEvent.put("toUserId", userId);
            }
            if (sessionId != null) {
                websocketEvent.put("key", sessionId);
            }

            // Convert back to JSON and emit to WebSocket
            String websocketMessage = MAPPER.writeValueAsString(websocketEvent);
            websocketEmitter.emmit(websocketMessage);

            Log.infof("✅ Routed recommendation to session=%s, user=%s, type=%s, confidence=%.2f",
                sessionId, userId,
                recommendation.get("recommendationType"),
                recommendation.get("confidence"));

        } catch (Exception e) {
            Log.errorf(e, "❌ Failed to process recommendation from Flink: %s",
                message.substring(0, Math.min(100, message.length())));
        }
    }
}

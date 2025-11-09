package com.ververica.composable_job.quarkus.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket connections and relays assistant responses from Kafka
 */
@ApplicationScoped
public class ShoppingAssistantEmitter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Map of connectionId -> WebSocketConnection
    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();

    // Map of sessionId -> connectionId (for routing responses)
    private final Map<String, String> sessionConnections = new ConcurrentHashMap<>();

    public void registerConnection(WebSocketConnection connection) {
        String connectionId = connection.id();
        connections.put(connectionId, connection);
        Log.infof("Registered shopping assistant connection: %s", connectionId);
    }

    public void unregisterConnection(String connectionId) {
        connections.remove(connectionId);

        // Clean up session mappings
        sessionConnections.entrySet().removeIf(entry -> entry.getValue().equals(connectionId));

        Log.infof("Unregistered shopping assistant connection: %s", connectionId);
    }

    public void registerSession(String sessionId, String connectionId) {
        sessionConnections.put(sessionId, connectionId);
    }

    /**
     * Consumes assistant responses from Kafka and sends to appropriate WebSocket clients
     */
    @Incoming("assistant-responses")
    @Blocking
    public void consumeAssistantResponse(String message) {
        try {
            JsonNode responseNode = MAPPER.readTree(message);

            String sessionId = responseNode.get("sessionId").asText();
            String messageId = responseNode.has("messageId") ? responseNode.get("messageId").asText() : null;
            String responseText = responseNode.get("responseText").asText();
            long timestamp = responseNode.has("timestamp") ? responseNode.get("timestamp").asLong() : System.currentTimeMillis();

            // Build response for frontend
            Map<String, Object> wsResponse = new java.util.HashMap<>();
            wsResponse.put("type", "ASSISTANT_RESPONSE");
            wsResponse.put("id", messageId);
            wsResponse.put("sessionId", sessionId);
            wsResponse.put("text", responseText);
            wsResponse.put("timestamp", timestamp);

            // Add recommended products if present
            if (responseNode.has("recommendedProducts")) {
                wsResponse.put("recommendedProducts", MAPPER.convertValue(
                    responseNode.get("recommendedProducts"),
                    java.util.List.class
                ));
            }

            String wsMessage = MAPPER.writeValueAsString(wsResponse);

            // Find connection for this session
            String connectionId = sessionConnections.get(sessionId);
            if (connectionId != null) {
                WebSocketConnection connection = connections.get(connectionId);
                if (connection != null) {
                    connection.sendTextAndAwait(wsMessage);
                    Log.debugf("Sent assistant response to connection %s for session %s", connectionId, sessionId);
                } else {
                    Log.warnf("Connection %s not found for session %s", connectionId, sessionId);
                }
            } else {
                // Broadcast to all connections as fallback (if sessionId not tracked)
                Log.debugf("Broadcasting assistant response for session %s to all connections", sessionId);
                broadcastMessage(wsMessage);
            }

        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to parse assistant response: %s", message);
        }
    }

    /**
     * Broadcasts a message to all connected clients
     */
    private void broadcastMessage(String message) {
        connections.values().forEach(connection -> {
            try {
                connection.sendTextAndAwait(message);
            } catch (Exception e) {
                Log.errorf(e, "Failed to send message to connection %s", connection.id());
            }
        });
    }

    /**
     * Sends a message to a specific connection
     */
    public void sendToConnection(String connectionId, String message) {
        WebSocketConnection connection = connections.get(connectionId);
        if (connection != null) {
            try {
                connection.sendTextAndAwait(message);
            } catch (Exception e) {
                Log.errorf(e, "Failed to send message to connection %s", connectionId);
            }
        }
    }
}

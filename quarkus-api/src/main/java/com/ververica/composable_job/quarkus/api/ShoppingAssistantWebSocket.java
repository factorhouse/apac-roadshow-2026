package com.ververica.composable_job.quarkus.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket endpoint for Shopping Assistant chat
 * Handles real-time communication between frontend and Flink job
 */
@WebSocket(path = "/ws/chat")
@ApplicationScoped
public class ShoppingAssistantWebSocket {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Channel("shopping-assistant-chat")
    Emitter<String> chatEmitter;

    @Inject
    WebSocketConnection connection;

    @Inject
    ShoppingAssistantEmitter assistantEmitter;

    @OnOpen
    public void onOpen() {
        String connectionId = connection.id();
        Log.infof("Shopping assistant connection opened: %s", connectionId);

        // Register connection for receiving assistant responses
        assistantEmitter.registerConnection(connection);
    }

    @OnClose
    public void onClose() {
        String connectionId = connection.id();
        Log.infof("Shopping assistant connection closed: %s", connectionId);

        // Unregister connection
        assistantEmitter.unregisterConnection(connectionId);
    }

    @OnTextMessage
    public void onMessage(String message) {
        try {
            JsonNode messageNode = MAPPER.readTree(message);
            String type = messageNode.get("type").asText();

            Log.debugf("Received message type: %s", type);

            switch (type) {
                case "USER_MESSAGE":
                    handleUserMessage(messageNode);
                    break;
                case "INIT":
                    handleInit(messageNode);
                    break;
                case "BASKET_UPDATE":
                    handleBasketUpdate(messageNode);
                    break;
                case "PRODUCT_VIEW":
                    handleProductView(messageNode);
                    break;
                default:
                    Log.warnf("Unknown message type: %s", type);
            }
        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to parse message: %s", message);
        }
    }

    private void handleUserMessage(JsonNode messageNode) throws JsonProcessingException {
        String text = messageNode.get("text").asText();
        String sessionId = messageNode.get("sessionId").asText();
        String userId = messageNode.has("userId") ? messageNode.get("userId").asText() : null;

        // Register this session with this connection for routing responses
        assistantEmitter.registerSession(sessionId, connection.id());

        // Create chat message for Flink
        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("messageId", UUID.randomUUID().toString());
        chatMessage.put("sessionId", sessionId);
        chatMessage.put("userId", userId);
        chatMessage.put("text", text);
        chatMessage.put("timestamp", System.currentTimeMillis());
        chatMessage.put("type", "USER_MESSAGE");
        chatMessage.put("connectionId", connection.id());

        // Add context if present
        if (messageNode.has("context")) {
            chatMessage.put("context", MAPPER.convertValue(messageNode.get("context"), Map.class));
        }

        // Send to Kafka
        String messageJson = MAPPER.writeValueAsString(chatMessage);
        chatEmitter.send(messageJson);

        Log.debugf("Sent chat message to Kafka: sessionId=%s, text=%s", sessionId, text);
    }

    private void handleInit(JsonNode messageNode) throws JsonProcessingException {
        String sessionId = messageNode.get("sessionId").asText();
        String userId = messageNode.has("userId") ? messageNode.get("userId").asText() : null;

        // Register this session with this connection
        assistantEmitter.registerSession(sessionId, connection.id());

        // Send init message to Kafka (optional, for tracking)
        Map<String, Object> initMessage = new HashMap<>();
        initMessage.put("messageId", UUID.randomUUID().toString());
        initMessage.put("sessionId", sessionId);
        initMessage.put("userId", userId);
        initMessage.put("timestamp", System.currentTimeMillis());
        initMessage.put("type", "INIT");
        initMessage.put("connectionId", connection.id());

        if (messageNode.has("basket")) {
            initMessage.put("basket", MAPPER.convertValue(messageNode.get("basket"), Object.class));
        }

        String messageJson = MAPPER.writeValueAsString(initMessage);
        chatEmitter.send(messageJson);

        Log.infof("Shopping assistant initialized: sessionId=%s, userId=%s", sessionId, userId);
    }

    private void handleBasketUpdate(JsonNode messageNode) {
        // Optional: track basket updates for context
        Log.debugf("Basket updated for connection: %s", connection.id());
    }

    private void handleProductView(JsonNode messageNode) {
        // Optional: track product views for context
        Log.debugf("Product viewed for connection: %s", connection.id());
    }
}

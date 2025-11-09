package com.ververica.composable_job.quarkus.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoint for Shopping Assistant (fallback when WebSocket is not available)
 */
@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShoppingAssistantResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    @Channel("shopping-assistant-chat")
    Emitter<String> chatEmitter;

    @POST
    @Path("/shopping-assistant")
    public Response sendMessage(ChatRequest request) {
        try {
            // Validate request
            if (request.message == null || request.message.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Message is required"))
                    .build();
            }

            if (request.sessionId == null || request.sessionId.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Session ID is required"))
                    .build();
            }

            // Create chat message for Flink
            Map<String, Object> chatMessage = new HashMap<>();
            chatMessage.put("messageId", UUID.randomUUID().toString());
            chatMessage.put("sessionId", request.sessionId);
            chatMessage.put("userId", request.userId);
            chatMessage.put("text", request.message);
            chatMessage.put("timestamp", System.currentTimeMillis());
            chatMessage.put("type", "USER_MESSAGE");

            // Add basket context
            if (request.basket != null && !request.basket.isEmpty()) {
                chatMessage.put("basketContext", request.basket);
            }

            // Add current product context
            if (request.currentProduct != null) {
                chatMessage.put("currentProduct", request.currentProduct);
            }

            // Send to Kafka
            String messageJson = MAPPER.writeValueAsString(chatMessage);
            chatEmitter.send(messageJson);

            Log.infof("REST: Sent chat message to Kafka: sessionId=%s", request.sessionId);

            // Note: In REST mode, we return a simple acknowledgment
            // The actual AI response will come through a different channel
            // For a fully synchronous REST API, you'd need to implement request-response pattern
            ChatResponse response = new ChatResponse();
            response.id = chatMessage.get("messageId").toString();
            response.response = "Processing your message...";
            response.status = "PENDING";

            return Response.accepted(response).build();

        } catch (JsonProcessingException e) {
            Log.error("Failed to process chat message", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to process message"))
                .build();
        }
    }

    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok(Map.of(
            "service", "Shopping Assistant",
            "status", "ACTIVE",
            "websocketEndpoint", "/ws/chat"
        )).build();
    }

    // Request/Response DTOs

    public static class ChatRequest {
        public String message;
        public String sessionId;
        public String userId;
        public List<Map<String, Object>> basket;
        public Map<String, Object> currentProduct;
    }

    public static class ChatResponse {
        public String id;
        public String response;
        public String status;
        public List<Map<String, Object>> recommendedProducts;
    }
}

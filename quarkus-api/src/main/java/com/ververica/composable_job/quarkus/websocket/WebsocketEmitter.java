package com.ververica.composable_job.quarkus.websocket;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.Language;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.quarkus.api.LanguageStateService;
import com.ververica.composable_job.quarkus.serialization.ModelMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WebsocketEmitter {

    @Inject
    OpenConnections connections;

    @Inject
    LanguageStateService languageStateService;

    // TODO get the translated message
    public void emmit(String message) {
        try {
            ProcessingEvent<?> genericEvent = ModelMapper.readValue(message, ProcessingEvent.class);

            if (EventRouter.isPublicMessage(genericEvent)) {
                connections.stream().forEach(webSocketConnection -> sendMessageToConnection(message, webSocketConnection));

                return;
            }

            ProcessingEvent<ChatMessage> event = ModelMapper.readChatMessageEvent(message); // ideally we should serialize only once
            connections.stream().forEach(webSocketConnection -> {
                if (EventRouter.shouldSendToUser(event, webSocketConnection.pathParam("username"))) {
                    sendMessageToConnection(message, webSocketConnection);
                }
            });
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void sendMessageToConnection(String message, WebSocketConnection webSocketConnection) {
        try {
            ProcessingEvent<?> processingEvent = ModelMapper.readValue(message, ProcessingEvent.class);

            // Just send messages that are not chat messages to the user that sent the message
            if (!ProcessingEvent.Type.CHAT_MESSAGE.equals(processingEvent.eventType)) {
                webSocketConnection.sendTextAndAwait(message);
                return;
            }

            ProcessingEvent<EnrichedChatMessage> enrichedChatMessageProcessingEvent = ModelMapper.readEnrichedChatMessageEvent(message);

            // Just send messages that are not chat messages to the user that sent the message
            if (!ChatMessageType.CHAT_MESSAGE.equals(enrichedChatMessageProcessingEvent.payload.type)) {
                webSocketConnection.sendTextAndAwait(message);
                return;
            }


            String username = webSocketConnection.pathParam("username");
            Language userLanguage = languageStateService.getUserLanguage(username);

            Log.info("User " + username + " speaks " + userLanguage);

            ChatMessage translatedMessage = ChatMessage.from(enrichedChatMessageProcessingEvent.payload, userLanguage);
            ProcessingEvent<ChatMessage> chatMessageProcessingEvent = ProcessingEvent.of(translatedMessage, enrichedChatMessageProcessingEvent.toUserId);

            ObjectMapper objectMapper = new ObjectMapper();
            webSocketConnection.sendTextAndAwait(objectMapper.writeValueAsString(chatMessageProcessingEvent));
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }
}


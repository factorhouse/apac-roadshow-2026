package com.ververica.composable_job.quarkus.serialization;

import com.ververica.composable_job.model.ChatMessage;
import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ModelMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<ProcessingEvent<ChatMessage>> CHAT_MESSAGE_EVENT_TYPE_REF = new TypeReference<>() {
    };
    private static final TypeReference<ProcessingEvent<EnrichedChatMessage>> ENRICHED_CHAT_MESSAGE_EVENT_TYPE_REF = new TypeReference<>() {
    };

    public static ProcessingEvent<ChatMessage> readChatMessageEvent(String processingEvent) throws JsonProcessingException {
        return readValue(processingEvent, CHAT_MESSAGE_EVENT_TYPE_REF);
    }

    public static ProcessingEvent<EnrichedChatMessage> readEnrichedChatMessageEvent(String processingEvent) throws JsonProcessingException {
        return readValue(processingEvent, ENRICHED_CHAT_MESSAGE_EVENT_TYPE_REF);
    }

    public static <V> V readValue(String value, TypeReference<V> typeRef) throws JsonProcessingException {
        return MAPPER.readValue(value, typeRef);
    }

    public static <V> V readValue(String value, Class<V> clazz) throws JsonProcessingException {
        return MAPPER.readValue(value, clazz);
    }
}

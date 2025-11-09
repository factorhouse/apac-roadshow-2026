package com.ververica.composable_job.quarkus.kafka.streams;

import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.quarkus.serialization.ModelMapper;
import com.ververica.composable_job.quarkus.websocket.WebsocketEmitter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProcessingFanoutProcessor implements Processor<String, String, String, String> {
    private ProcessorContext<String, String> context;

    private final String chatStoreName;
    private final Integer chatCacheSize;
    private final String dataPointStoreName;
    private final Integer dataPointCacheSize;

    private KeyValueStore<String, List<String>> chatStore;
    private KeyValueStore<String, List<String>> dataPointStore;

    final private WebsocketEmitter websocketEmitter;

    public ProcessingFanoutProcessor(String chatStoreName, Integer chatCacheSize, String dataPointStoreName, Integer dataPointCacheSize, WebsocketEmitter websocketEmitter) {
        this.chatStoreName = chatStoreName;
        this.chatCacheSize = chatCacheSize;
        this.dataPointStoreName = dataPointStoreName;
        this.dataPointCacheSize = dataPointCacheSize;

        this.websocketEmitter = websocketEmitter;
    }

    @Override
    public void init(ProcessorContext<String, String> context) {
        this.context = context;

        this.chatStore = context.getStateStore(chatStoreName);
        this.dataPointStore = context.getStateStore(dataPointStoreName);
    }

    @Override
    public void process(Record<String, String> record) {
        String value = record.value();
        if (value == null) {
            return;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode node = objectMapper.readTree(value);

            switch (ProcessingEvent.Type.valueOf(node.get("eventType").asText())) {
                case CHAT_MESSAGE:
                    List<String> messages = chatStore.get(KafkaStreamsTopology.CHAT_MESSAGES_KEY);
                    if (messages == null) {
                        messages = Collections.synchronizedList(new ArrayList<String>());
                    }

                    ProcessingEvent<EnrichedChatMessage> chatMessageProcessingEvent = ModelMapper.readEnrichedChatMessageEvent(value);
                    if (ChatMessageType.CHAT_MESSAGE.equals(chatMessageProcessingEvent.payload.type)) {
                        messages.add(value);
                        if (messages.size() > chatCacheSize) {
                            messages.remove(0);
                        }
                        chatStore.put(KafkaStreamsTopology.CHAT_MESSAGES_KEY, messages);
                    }

                    break;
                case RANDOM_NUMBER_POINT:
                    List<String> dataPoints = dataPointStore.get(KafkaStreamsTopology.DATA_POINTS_KEY);
                    if (dataPoints == null) {
                        dataPoints = Collections.synchronizedList(new ArrayList<String>());
                    }

                    dataPoints.add(value);

                    if (dataPoints.size() > dataPointCacheSize) {
                        dataPoints.remove(0);
                    }

                    dataPointStore.put(KafkaStreamsTopology.DATA_POINTS_KEY, dataPoints);
                    break;
                default:
                    Log.warn("Unknown event type: " + node.get("eventType").asText());
                    break;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        websocketEmitter.emmit(record.value());
    }

    @Override
    public void close() {
        // Cleanup resources if needed
    }
}



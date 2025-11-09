package com.ververica.composable_job.quarkus.kafka.streams;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class CacheQueryService {

    @Inject
    KafkaStreams kafkaStreams;

    public List<String> getLastChatMessages() {
        ReadOnlyKeyValueStore<String, List<String>> chatStore =
                kafkaStreams.store(
                        StoreQueryParameters.fromNameAndType(
                                KafkaStreamsTopology.CHAT_STORE_NAME,
                                QueryableStoreTypes.keyValueStore()
                        )
                );

        List<String> messages = chatStore.get(KafkaStreamsTopology.CHAT_MESSAGES_KEY);
        if (messages == null) {
            return Collections.synchronizedList(new ArrayList<String>());
        }
        return messages;
    }

    public List<String> getLastDataPoints() {
        ReadOnlyKeyValueStore<String, List<String>> dataPointStore =
                kafkaStreams.store(
                        StoreQueryParameters.fromNameAndType(
                                KafkaStreamsTopology.DATA_POINT_STORE_NAME,
                                QueryableStoreTypes.keyValueStore()
                        )
                );

        List<String> dataPoints = dataPointStore.get(KafkaStreamsTopology.DATA_POINTS_KEY);
        if (dataPoints == null) {
            return Collections.synchronizedList(new ArrayList<String>());
        }
        return dataPoints;
    }
}


package com.ververica.composable_job.common.flink;

import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;

public class KafkaUtils {

    private KafkaUtils() {}

    public static class Topic {

        public static final String PROCESSING_FANOUT = "processing_fanout";
    }

    public static class Source {

        private Source() {}

        public static <OUT> KafkaSource<OUT> create(String brokers, String topic, String groupId, JsonDeserializationSchema<OUT> valueSchema) {
            return KafkaSource.<OUT>builder()
                    .setBootstrapServers(brokers)
                    .setTopics(topic)
                    .setGroupId(groupId)
                    .setValueOnlyDeserializer(valueSchema)
                    .build();
        }
    }


    public static class Sink {

        private Sink() {}

        public static <IN> KafkaSink<IN> create(String brokers, String topic, JsonSerializationSchema<IN> valueSchema) {
            return KafkaSink.<IN>builder()
                    .setBootstrapServers(brokers)
                    .setRecordSerializer(
                            KafkaRecordSerializationSchema.<IN>builder()
                                    .setTopic(topic)
                                    .setValueSerializationSchema(valueSchema)
                                    //.setKeySerializationSchema()
                                    .build()
                    )
                    .build();

        }
    }
}

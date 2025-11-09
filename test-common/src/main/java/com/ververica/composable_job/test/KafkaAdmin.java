package com.ververica.composable_job.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class KafkaAdmin {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String bootstrapServers;

    public KafkaAdmin(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public void createTopic(String topicName) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(properties)) {
            adminClient.createTopics(List.of(new NewTopic(topicName, 1, (short)1)));
        }
    }

    public <V> List<V> getRecordValues(String topic, TypeReference<V> typeRef) throws JsonProcessingException {
        LinkedList<V> values = new LinkedList<>();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(topic));
            for (ConsumerRecord<String, String> record: consumer.poll(Duration.ofSeconds(5)))
                values.add(MAPPER.readValue(record.value(), typeRef));
        }

        return values;
    }

    public <V> void publishRecords(List<V> values, String topic) throws JsonProcessingException {

        try (KafkaProducer<String, String> producer = createProducer()) {
            for (V value : values)
                producer.send(new ProducerRecord<>(topic, MAPPER.writeValueAsString(value)));
        }

    }

    private KafkaProducer<String, String> createProducer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    private <V> KafkaConsumer<String, String> createConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "testGroupId");
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(properties);
    }
}

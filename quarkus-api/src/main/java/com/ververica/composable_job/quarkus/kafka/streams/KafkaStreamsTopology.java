package com.ververica.composable_job.quarkus.kafka.streams;

import com.ververica.composable_job.model.ecommerce.Product;
import com.ververica.composable_job.quarkus.websocket.WebsocketEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.common.utils.Bytes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class KafkaStreamsTopology {

    public static final String CHAT_STORE_NAME = "chat-store";
    public static final String CHAT_MESSAGES_KEY = "chat-messages";

    public static final String DATA_POINT_STORE_NAME = "data-point-store";
    public static final String DATA_POINTS_KEY = "data-points";
    
    public static final String PRODUCTS_STORE_NAME = "products-cache";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    private WebsocketEmitter websocketEmitter;
    
    @Inject
    private ProductCacheService productCacheService;

    @Produces
    public Topology buildTopology(
            @ConfigProperty(name = "quarkus.kafka-streams.topics") String topics,
            @ConfigProperty(name = "quarkus.kafka-streams.caching.chat") Integer chatCacheSize,
            @ConfigProperty(name = "quarkus.kafka-streams.caching.data-points") Integer dataPointCacheSize
    ) {
        StreamsBuilder builder = new StreamsBuilder();
        
        // Create custom Serde for Product
        Serde<Product> productSerde = createProductSerde();

        StoreBuilder<KeyValueStore<String, List<String>>> chatListStoreBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.inMemoryKeyValueStore(CHAT_STORE_NAME),
                        Serdes.String(),
                        Serdes.ListSerde(ArrayList.class, Serdes.String())
                );

        StoreBuilder<KeyValueStore<String, List<String>>> dataPointStoreBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.inMemoryKeyValueStore(DATA_POINT_STORE_NAME),
                        Serdes.String(),
                        Serdes.ListSerde(ArrayList.class, Serdes.String())
                );

        builder.addStateStore(chatListStoreBuilder);
        builder.addStateStore(dataPointStoreBuilder);
        // Product store will be created by Materialized below

        KStream<String, String> inventoryStream = builder.stream(
                "inventory-events",
                Consumed.with(Serdes.String(), Serdes.String())
        );
        
        // Parse inventory events and extract current product
        KStream<String, Product> productStream = inventoryStream
            .mapValues(value -> {
                try {
                    JsonNode node = MAPPER.readTree(value);
                    String eventType = node.get("eventType").asText();

                    // Ignore the PRODUCT_ADDED event entirely.
                    // This prevents the race condition and the creation of "skeleton" products.
                    if ("PRODUCT_ADDED".equals(eventType)) {
                        return null; // This message will be filtered out and discarded.
                    }

                    // For all other event types (like INVENTORY_DECREASED), create a partial update object.
                    Product partialUpdate = new Product();
                    partialUpdate.productId = node.get("productId").asText();
                    if (node.has("currentInventory")) {
                        partialUpdate.inventory = node.get("currentInventory").asInt();
                    }
                    if (node.has("currentPrice")) {
                        partialUpdate.price = node.get("currentPrice").asDouble();
                    }
                    return partialUpdate;
                } catch (Exception e) {
                    Log.warn("Failed to parse inventory event: " + e.getMessage());
                    return null;
                }
            })
            .filter((key, value) -> value != null && value.productId != null)
            .selectKey((key, value) -> value.productId);

        // Materialize as KTable for product cache (CQRS read model)
        KTable<String, Product> productsTable = productStream
                .toTable(
                        Named.as("products-table"),
                        Materialized.<String, Product, KeyValueStore<Bytes, byte[]>>as(PRODUCTS_STORE_NAME)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(productSerde)
                );

        // Forward product updates to WebSocket for real-time updates
        productStream.foreach((key, product) -> {
            productCacheService.updateProduct(product);
        });
        
        // Original processing for other topics
        builder
                .stream(
                        topics,
                        Consumed.with(Serdes.String(), Serdes.String())
                )
                .process(() ->
                                new ProcessingFanoutProcessor(
                                        CHAT_STORE_NAME,
                                        chatCacheSize,
                                        DATA_POINT_STORE_NAME,
                                        dataPointCacheSize,
                                        websocketEmitter
                                ), Named.as("processor")
                        , CHAT_STORE_NAME, DATA_POINT_STORE_NAME
                );

        return builder.build();
    }
    
    private Serde<Product> createProductSerde() {
        return Serdes.serdeFrom(
                new Serializer<Product>() {
                    @Override
                    public byte[] serialize(String topic, Product data) {
                        try {
                            return MAPPER.writeValueAsBytes(data);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to serialize Product", e);
                        }
                    }
                },
                new Deserializer<Product>() {
                    @Override
                    public Product deserialize(String topic, byte[] data) {
                        if (data == null) return null;
                        try {
                            return MAPPER.readValue(data, Product.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to deserialize Product", e);
                        }
                    }
                }
        );
    }
}

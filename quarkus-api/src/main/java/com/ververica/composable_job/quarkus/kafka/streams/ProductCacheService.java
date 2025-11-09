package com.ververica.composable_job.quarkus.kafka.streams;

import com.ververica.composable_job.model.ProcessingEvent;
import com.ververica.composable_job.model.ecommerce.Product;
import com.ververica.composable_job.quarkus.websocket.WebsocketEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Startup
public class ProductCacheService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Product> productCache = new ConcurrentHashMap<>();
    private KafkaStreams streams;
    private ReadOnlyKeyValueStore<String, Product> productStore;

    @Inject
    WebsocketEmitter websocketEmitter;
    
    public void initializeCache(KafkaStreams kafkaStreams) {
        this.streams = kafkaStreams;
        
        // Wait for streams to be ready
        streams.setStateListener((newState, oldState) -> {
            if (newState == KafkaStreams.State.RUNNING) {
                try {
                    productStore = streams.store(
                        StoreQueryParameters.fromNameAndType(
                            "products-cache",
                            QueryableStoreTypes.keyValueStore()
                        )
                    );
                    Log.info("Product cache store initialized");
                    loadInitialProducts();
                } catch (Exception e) {
                    Log.error("Failed to initialize product store", e);
                }
            }
        });
    }
    
    private void loadInitialProducts() {
        // Sync with KStreams store if available
        if (productStore != null) {
            try {
                productStore.all().forEachRemaining(kv -> {
                    productCache.put(kv.key, kv.value);
                });
                Log.infof("Loaded %d products from KStreams cache", productCache.size());
            } catch (Exception e) {
                Log.warn("Could not load from KStreams cache", e);
            }
        }

        // If cache is empty, that's expected! Waiting for Flink inventory job to populate products
        if (productCache.isEmpty()) {
            Log.info("📦 Product cache is empty - waiting for inventory Flink job to send products via Kafka");
            Log.info("   Run: ./flink-1-inventory-job.sh to populate the shop");
        }
    }
    
    @Scheduled(every = "30s")
    public void syncCacheToClients() {
        if (productCache.isEmpty()) {
            Log.debug("⏳ Product cache still empty - waiting for Flink inventory job to send products");
            return;
        }

        try {
            // Send cache sync event to all connected clients
            Map<String, Object> cacheSync = new HashMap<>();
            cacheSync.put("products", new ArrayList<>(productCache.values()));
            cacheSync.put("timestamp", System.currentTimeMillis());

            ProcessingEvent<Map<String, Object>> syncEvent = new ProcessingEvent<>(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                null,
                null,
                ProcessingEvent.Type.PRODUCT_UPDATE,
                cacheSync
            );

            String json = MAPPER.writeValueAsString(syncEvent);
            websocketEmitter.emmit(json);

            Log.debugf("🔄 Synced %d products to clients via WebSocket", productCache.size());
        } catch (Exception e) {
            Log.error("Failed to sync cache to clients", e);
        }
    }
    
    public void updateProduct(Product productUpdate) {
        // A full product object will have a name. A partial update from inventoryStream will not.
        if (productUpdate.name == null || productUpdate.name.isEmpty()) {
            // This is a PARTIAL update for inventory/price.
            Product existingProduct = productCache.get(productUpdate.productId);

            if (existingProduct != null) {
                // MERGE the update into the full object that already exists in the cache.
                existingProduct.inventory = productUpdate.inventory;
                existingProduct.price = productUpdate.price;
                
                Log.infof("🔄 Merged inventory update for product %s (%s) - new inventory: %d",
                    existingProduct.productId, existingProduct.name, existingProduct.inventory);
                
                // Send the complete, updated product to the WebSocket.
                sendWebSocketUpdate(existingProduct);
            } else {
                // This is expected if the inventory event arrives before the full product details.
                Log.warnf("Received inventory update for product %s, but full details are not yet in cache. Ignoring.", productUpdate.productId);
            }
        } else {
            // This is a FULL product object, coming from the other consumer.
            // The original logic is correct for this case.
            boolean isNew = !productCache.containsKey(productUpdate.productId);
            productCache.put(productUpdate.productId, productUpdate);

            Log.infof("🔄 %s product %s (%s) - total products in cache: %d",
                isNew ? "Added new" : "Updated", productUpdate.productId, productUpdate.name, productCache.size());

            sendWebSocketUpdate(productUpdate);
        }
    }
    
    public Product getProduct(String productId) {
        // Try cache first
        Product product = productCache.get(productId);
        
        // Try KStreams store if not in cache
        if (product == null && productStore != null) {
            try {
                product = productStore.get(productId);
                if (product != null) {
                    productCache.put(productId, product);
                }
            } catch (Exception e) {
                Log.warn("Failed to query KStreams store", e);
            }
        }
        
        return product;
    }
    
    public Collection<Product> getAllProducts() {
        return productCache.values();
    }
    
    public List<Product> getProductsByCategory(String category) {
        return productCache.values().stream()
            .filter(p -> p.category.equalsIgnoreCase(category))
            .toList();
    }
    
    public List<Product> searchProducts(String query) {
        String searchLower = query.toLowerCase();
        return productCache.values().stream()
            .filter(p -> p.name.toLowerCase().contains(searchLower) ||
                        p.description.toLowerCase().contains(searchLower) ||
                        p.tags.stream().anyMatch(t -> t.toLowerCase().contains(searchLower)))
            .toList();
    }

    private void sendWebSocketUpdate(Product product) {
        try {
            ProcessingEvent<Product> updateEvent = new ProcessingEvent<>(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                product.productId,
                null,
                ProcessingEvent.Type.PRODUCT_UPDATE,
                product
            );

            websocketEmitter.emmit(MAPPER.writeValueAsString(updateEvent));
            Log.debugf("→ Sent WebSocket update for product %s", product.productId);
        } catch (Exception e) {
            Log.error("Failed to send product update", e);
        }
    }    

    /**
     * DEPRECATED: Do not call this method in production!
     *
     * This method creates mock products for testing purposes only.
     * In the event-driven architecture, products should come from the Flink inventory job
     * via Kafka streams, not be generated at startup.
     *
     * To populate products correctly:
     * 1. Ensure Quarkus and Kafka are running
     * 2. Run: ./flink-1-inventory-job.sh
     * 3. Watch products stream into the shop via KStreams
     *
     * This method is kept for local testing/development only.
     */
    @Deprecated
    private void initializeDefaultProducts() {
        String[] categories = {"Electronics", "Fashion", "Home & Garden", "Sports", "Books", "Toys", "Beauty", "Food & Grocery"};
        String[] brands = {"TechPro", "StyleCraft", "HomeEssentials", "SportMax", "BookWorm",
                          "ToyLand", "BeautyPlus", "GourmetKitchen", "EcoLife", "PremiumCo"};
        String[][] productNames = {
            {"Wireless Headphones", "Smart Watch", "Laptop", "Tablet", "Camera"},
            {"Designer Jacket", "Running Shoes", "Leather Bag", "Sunglasses", "Watch"},
            {"Coffee Maker", "Air Purifier", "Smart Light", "Vacuum Cleaner", "Blender"},
            {"Yoga Mat", "Dumbbells", "Bicycle", "Tennis Racket", "Swimming Goggles"},
            {"Bestseller Novel", "Cookbook", "Travel Guide", "Science Fiction", "Biography"}
        };

        Random random = new Random();
        int productId = 1;

        for (int cat = 0; cat < 5; cat++) {
            for (String productName : productNames[cat]) {
                for (int variant = 1; variant <= 4; variant++) {
                    String id = "prod_" + productId++;
                    String category = categories[cat];
                    String brand = brands[random.nextInt(brands.length)];

                    Product product = new Product(
                        id,
                        brand + " " + productName + " " + (variant > 1 ? "v" + variant : "Pro"),
                        "Experience premium quality with our " + productName.toLowerCase() +
                        ". Designed for modern lifestyle with cutting-edge features and exceptional build quality.",
                        50 + random.nextDouble() * 950,
                        category,
                        String.format("https://source.unsplash.com/600x400/?%s,%s",
                                    category.replace(" ", ""), productName.replace(" ", "")),
                        random.nextInt(100) + 1,
                        Arrays.asList("bestseller", "premium", category.toLowerCase().replace(" & ", "-")),
                        3.5 + random.nextDouble() * 1.5,
                        random.nextInt(1000) + 10
                    );

                    productCache.put(id, product);
                }
            }
        }

        Log.warnf("⚠️ Initialized %d mock products (should only be used for testing!)", productCache.size());
    }
}
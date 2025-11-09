package com.ververica.composable_job.quarkus.kafka;

import com.ververica.composable_job.model.ecommerce.Product;
import com.ververica.composable_job.quarkus.kafka.streams.ProductCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ProductConsumer {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Inject
    ProductCacheService productCacheService;
    
    @Incoming("products-in")
    public void consumeProduct(String productJson) {
        try {
            Product product = MAPPER.readValue(productJson, Product.class);
            Log.infof("Received product from Kafka: %s", product.productId);
            productCacheService.updateProduct(product);
        } catch (Exception e) {
            Log.error("Failed to process product from Kafka", e);
        }
    }
}
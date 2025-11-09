package com.ververica.composable_job.flink.inventory.shared.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ververica.composable_job.model.ecommerce.Product;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable ProcessFunction to parse JSON strings into Product objects.
 *
 * PATTERN: Data Transformation / Parsing
 *
 * Handles both:
 * - JSON arrays: [{"productId":"P001",...}, {"productId":"P002",...}]
 * - Single objects: {"productId":"P001",...}
 *
 * This is extracted from Pattern 01 (Hybrid Source) but made reusable
 * across the entire inventory management pipeline.
 *
 * USAGE:
 * <pre>
 * DataStream<String> rawJson = ...;
 * DataStream<Product> products = rawJson.process(new ProductParser());
 * </pre>
 *
 * ERROR HANDLING:
 * - Logs errors but doesn't fail the job
 * - Continues processing valid records
 * - Use side outputs for error handling in production
 */
public class ProductParser extends ProcessFunction<String, Product> {

    private static final Logger LOG = LoggerFactory.getLogger(ProductParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void processElement(String value, Context ctx, Collector<Product> out) throws Exception {
        LOG.info("!!!!!!!!!!!!!!!!!! PRODUCT PARSER PROCESS-ELEMENT CALLED !!!!!!!!!!!!!!!!!!");
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        try {
            String trimmedValue = value.trim();

            // Check if the input is a JSON array (from the initial file)
            if (trimmedValue.startsWith("[")) {
                LOG.info(">>>>>>>>>>>>>>> PARSING JSON ARRAY FROM FILE <<<<<<<<<<<<<<<");
                Product[] products = MAPPER.readValue(trimmedValue, Product[].class);
                for (Product product : products) {
                    if (product != null) {
                        out.collect(product);
                    }
                }
            } 
            // Check if the input is a single JSON object (from Kafka)
            else if (trimmedValue.startsWith("{")) {
                Product product = MAPPER.readValue(trimmedValue, Product.class);
                if (product != null) {
                    out.collect(product);
                }
            } else {
                LOG.warn("Received non-JSON data: {}", trimmedValue.substring(0, Math.min(100, trimmedValue.length())));
            }
        } catch (Exception e) {
            LOG.error("Failed to parse product JSON: {}", value, e);
            // Do not re-throw, just log and skip the bad record.
        }
    }
}
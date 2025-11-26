package com.ververica.composable_job.flink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ververica.composable_job.flink.inventory.patterns.hybrid_source.HybridSourceExample;
import com.ververica.composable_job.flink.inventory.shared.config.InventoryConfig;
import com.ververica.composable_job.flink.inventory.shared.model.AlertEvent;
import com.ververica.composable_job.flink.inventory.shared.model.InventoryEvent;
import com.ververica.composable_job.flink.inventory.shared.model.OrderItemDeduction;
import com.ververica.composable_job.flink.inventory.shared.processor.*;
import com.ververica.composable_job.model.ecommerce.Product;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.connector.base.source.hybrid.HybridSource;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inventory Management Job WITH ORDER DEDUCTION - Advanced Pattern Composition
 *
 * This enhanced version processes both product updates and customer orders to maintain
 * a real-time inventory count, and now publishes a clean 'products' topic.
 *
 * PATTERNS DEMONSTRATED:
 *
 * 1. PATTERN 01: Hybrid Source for State Bootstrapping
 *    - The Product Stream is initialized using a `HybridSource`. This allows the job to
 *      bootstrap its state by first reading a bounded source (a file with the initial
 *      product catalog) before switching seamlessly to an unbounded Kafka topic for
 *      continuous, real-time updates.
 *
 * 2. PATTERN 02: Co-Processing Multiple Streams
 *    - The job `connect`s two distinct data streams—the product stream and the order
 *      stream—into a single `CoProcessFunction`. This is the core mechanism that
 *      allows for unified logic and state management across different event types.
 *
 * 3. PATTERN 03: Shared Keyed State
 *    - The `CoProcessFunction` uses `ValueState` keyed by `productId` to maintain the
 *      inventory and price. This ensures both product and order streams read from and
 *      write to the same consistent state for any given product.
 *
 * 4. PATTERN 04: Timers for Event Generation
 *    - The `CoProcessFunction` uses processing time timers to detect and flag products
 *      with stale inventory (i.e., no updates for a set period), generating a new event.
 *
 * 5. PATTERN 05: Side Outputs for Alert Routing
 *    - Alerts for `LOW_STOCK`, `OUT_OF_STOCK`, and `PRICE_DROP` are routed from the
 *      main process into dedicated side output streams for separate downstream handling.
 *
 * 6. Pattern 06: Data Validation & Canonicalization
 *    - The job consumes raw product data, parses it into a clean `Product` object,
 *      and sinks it to a canonical `products` topic for other microservices to use.
 *
 * ARCHITECTURE (Updated):
 * <pre>
 * Product File  ──┐
 *                 ├─→ Hybrid Source → Product Parser ──┬── ▶ Kafka: products (for other services)
 * Product Kafka ──┘                                    │
 *                                                      ▼
 *                                         CoProcessFunction (Shared Keyed State)
 *                                                      ▲
 * Order Kafka ───→ Order Parser ───────────────────────┘
 *                                         │
 *                   ┌─────────────────────┼─────────────────────┐
 *                   ▼                     ▼                     ▼
 *             Inventory Events    Low Stock Alerts    Out of Stock Alerts
 *                   │                     │                     │
 *                   ▼                     └─────────────────────┘
 *           Kafka: inventory-events         Kafka: inventory-alerts
 *                   │
 *                   ▼
 *           Kafka: websocket_fanout (Real-time UI updates)
 * </pre>
 *
 * LEARNING OUTCOMES:
 * - Understand how to bootstrap Flink state from a file using a Hybrid Source.
 * - See how to use a CoProcessFunction to manage shared state between two streams.
 * - Learn how a Flink job can act as a data enricher, producing a clean, canonical
 *   topic for a broader microservices ecosystem.
 * </pre>
 */
public class InventoryManagementJobWithOrders {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryManagementJobWithOrders.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        // ========================================
        // STEP 1: Setup Environment & Configuration
        // ========================================

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        InventoryConfig config = InventoryConfig.fromEnvironment();

        env.setParallelism(config.getParallelism());
        env.enableCheckpointing(config.getCheckpointInterval());

        LOG.info("🚀 Starting Inventory Management Job WITH ORDER DEDUCTION");
        LOG.info("📊 Parallelism: {}", config.getParallelism());
        LOG.info("💾 Checkpoint interval: {}ms", config.getCheckpointInterval());
        LOG.info("🆕 NEW: This job processes orders and deducts inventory!");

        // ========================================
        // STEP 2: PATTERN 01 - Hybrid Source for State Bootstrapping
        // ========================================

        LOG.info("\n📥 PATTERN 01: Creating Product Hybrid Source (File → Kafka)");

        HybridSource<String> productSource = HybridSourceExample.createHybridSource(config);

        DataStream<String> rawProductStream = env.fromSource(
            productSource,
            WatermarkStrategy.noWatermarks(),
            "Product Hybrid Source",
            Types.STRING
        );

        DataStream<Product> productStream = rawProductStream
            .process(new ProductParser())
            .name("Parse Product JSON")
            .uid("product-parser");

        // =============================================================
        // STEP 3: Pattern 06: Data Validation & Canonicalization
        // =============================================================

        LOG.info("\n📤 PATTERN 06: Sinking clean product data to 'products' topic for other services");

        KafkaSink<String> productsSink = KafkaSink.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setKafkaProducerConfig(config.getKafkaPropertiesAsProperties())
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic(config.getProductsTopic())
                .setValueSerializationSchema(new SimpleStringSchema())
                .build()
            )
            .build();

        productStream
            .map(product -> MAPPER.writeValueAsString(product))
            .sinkTo(productsSink)
            .name("Sink to products topic")
            .uid("products-sink");

        // ========================================
        // STEP 4: Create Order Events Source (Input for Pattern 02)
        // ========================================

        LOG.info("\n📦 Creating Order Events Source (Kafka)");
        LOG.info("   Topic: order-events");
        LOG.info("   Purpose: Real-time inventory deduction from orders");

        KafkaSource<String> orderEventsSource = KafkaSource.<String>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setTopics("order-events")
            .setGroupId(config.getKafkaGroupId())
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .setProperties(config.getKafkaPropertiesAsProperties())
            .build();

        DataStream<String> rawOrderStream = env.fromSource(
            orderEventsSource,
            WatermarkStrategy.noWatermarks(),
            "Order Events Source"
        );

        DataStream<OrderItemDeduction> orderStream = rawOrderStream
            .process(new OrderItemParser())
            .name("Parse Order Item JSON")
            .uid("order-parser");

        // =================================================================
        // STEP 5: PATTERNS 02, 03, 04, 05 - Co-Process, State, Timers, and Side Outputs
        // =================================================================
        LOG.info("\n🤝 PATTERN 02: Connecting product and order streams to a CoProcessFunction...");
        LOG.info("   PATTERN 03: It will use Shared Keyed State for inventory.");
        LOG.info("   PATTERN 04: It will use Timers to detect stale products.");
        LOG.info("   PATTERN 05: It will use Side Outputs to route alerts.");


        SingleOutputStreamOperator<InventoryEvent> allInventoryEvents = productStream
                .keyBy(product -> product.productId)
                .connect(orderStream.keyBy(order -> order.productId))
                .process(new SharedInventoryProcessor())
                .name("Shared Inventory State Processor")
                .uid("shared-inventory-state");

        // Extract the side outputs for alerts directly from the new, single operator.
        DataStream<AlertEvent> allAlerts = allInventoryEvents.getSideOutput(SharedInventoryProcessor.LOW_STOCK_TAG)
                .union(allInventoryEvents.getSideOutput(SharedInventoryProcessor.OUT_OF_STOCK_TAG),
                    allInventoryEvents.getSideOutput(SharedInventoryProcessor.PRICE_DROP_TAG));

        // ========================================
        // STEP 6: Sinks - Output to Kafka
        // ========================================

        LOG.info("\n📤 Configuring Kafka Sinks");

        // All inventory events (product updates + order deductions)
        SinkFactory.sinkInventoryEvents(allInventoryEvents, config);

        // Alerts
        SinkFactory.sinkAlerts(allAlerts, config);

        // WebSocket for real-time UI updates
        SinkFactory.sinkToWebSocket(allInventoryEvents, config);

        // ========================================
        // STEP 7: Execute Job
        // ========================================

        LOG.info("\n✅ Job configured with COMPLETE inventory depletion!");
        LOG.info("🎯 Pattern Summary:");
        LOG.info("   01. Hybrid Source: ✓");
        LOG.info("   02. Co-Processing: ✓");
        LOG.info("   03. Shared Keyed State: ✓");
        LOG.info("   04. Timers: ✓");
        LOG.info("   05. Side Outputs: ✓");
        LOG.info("   06. Data Enrichment: ✓");
        LOG.info("\n💡 TRY IT:");
        LOG.info("   1. Place an order in the UI");
        LOG.info("   2. Watch inventory decrease in real-time!");
        LOG.info("   3. See out-of-stock alerts when inventory = 0");
        LOG.info("\n🚀 Executing job...\n");

        env.execute("Inventory Management Job WITH ORDER DEDUCTION");
    }
}

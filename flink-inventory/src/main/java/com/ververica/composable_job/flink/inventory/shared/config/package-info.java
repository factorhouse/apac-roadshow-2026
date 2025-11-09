/**
 * Configuration classes for the Inventory Management Job.
 *
 * <p>This package contains all configuration-related classes for the Flink inventory job,
 * providing a centralized and type-safe way to configure the application.</p>
 *
 * <h2>Main Classes</h2>
 * <ul>
 *   <li>{@link com.ververica.composable_job.flink.inventory.shared.config.InventoryConfig} -
 *       Main configuration class with builder pattern and factory methods</li>
 *   <li>{@link com.ververica.composable_job.flink.inventory.shared.config.KafkaTopics} -
 *       Constants for all Kafka topic names</li>
 *   <li>{@link com.ververica.composable_job.flink.inventory.shared.config.StateConfig} -
 *       State management configuration including TTL and cleanup strategies</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Configuration from Environment Variables</h3>
 * <pre>{@code
 * // Reads from environment variables with sensible defaults
 * InventoryConfig config = InventoryConfig.fromEnvironment();
 *
 * // Use the configuration
 * env.setParallelism(config.getParallelism());
 * env.enableCheckpointing(config.getCheckpointInterval());
 * }</pre>
 *
 * <h3>Example 2: Configuration from Command Line Arguments</h3>
 * <pre>{@code
 * public static void main(String[] args) {
 *     // Parse command line arguments and fall back to environment
 *     InventoryConfig config = InventoryConfig.fromArgs(args);
 *
 *     StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
 *     env.setParallelism(config.getParallelism());
 *
 *     // Create Kafka source with configuration
 *     KafkaSource<String> source = KafkaSource.<String>builder()
 *         .setBootstrapServers(config.getKafkaBootstrapServers())
 *         .setTopics(config.getProductUpdatesTopic())
 *         .setGroupId(config.getKafkaGroupId())
 *         .build();
 * }
 * }</pre>
 *
 * <h3>Example 3: Custom Configuration with Builder</h3>
 * <pre>{@code
 * // Build a custom configuration
 * InventoryConfig config = InventoryConfig.builder()
 *     .withKafkaBootstrapServers("kafka:9092")
 *     .withKafkaGroupId("my-consumer-group")
 *     .withInitialProductsFile("/data/products.json")
 *     .withCheckpointInterval(30000L) // 30 seconds
 *     .withParallelism(4)
 *     .withStateConfig(
 *         StateConfig.builder()
 *             .withStateTtl(24, TimeUnit.HOURS)
 *             .withStateBackendType(StateConfig.StateBackendType.ROCKSDB)
 *             .withIncrementalCheckpoints(true)
 *             .build()
 *     )
 *     .build();
 *
 * // Configuration is validated on build()
 * config.validate();
 * }</pre>
 *
 * <h3>Example 4: Using State Configuration</h3>
 * <pre>{@code
 * StateConfig stateConfig = StateConfig.builder()
 *     .withStateTtl(Time.hours(24))
 *     .withStateBackendType(StateConfig.StateBackendType.ROCKSDB)
 *     .withCleanupStrategy(StateConfig.CleanupStrategy.INCREMENTAL_CLEANUP)
 *     .withIncrementalCheckpoints(true)
 *     .build();
 *
 * // Create a state descriptor with TTL
 * ValueStateDescriptor<Product> descriptor = new ValueStateDescriptor<>(
 *     "product-state",
 *     Product.class
 * );
 *
 * // Apply TTL configuration
 * StateTtlConfig ttlConfig = stateConfig.createStateTtlConfig();
 * if (ttlConfig != null) {
 *     descriptor.enableTimeToLive(ttlConfig);
 * }
 * }</pre>
 *
 * <h3>Example 5: Using Kafka Topic Constants</h3>
 * <pre>{@code
 * // Instead of hardcoding topic names
 * KafkaSink<String> sink = KafkaSink.<String>builder()
 *     .setBootstrapServers(bootstrapServers)
 *     .setRecordSerializer(
 *         KafkaRecordSerializationSchema.builder()
 *             .setTopic(KafkaTopics.INVENTORY_EVENTS)  // Type-safe constant
 *             .setValueSerializationSchema(new SimpleStringSchema())
 *             .build()
 *     )
 *     .build();
 * }</pre>
 *
 * @since 1.0
 */
package com.ververica.composable_job.flink.inventory.shared.config;

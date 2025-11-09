# Inventory Management Flink Job

A sophisticated Apache Flink 1.20 job that demonstrates the Hybrid Source pattern for inventory management, combining batch processing of initial product data with real-time stream processing of updates.

## Architecture Overview

```
┌──────────────────┐      ┌─────────────────┐      ┌──────────────────┐
│  Initial Products│─────▶│  Hybrid Source  │─────▶│  Flink Pipeline  │
│  (JSON File)     │      │  (Bounded)      │      │  (Processing)    │
└──────────────────┘      └─────────────────┘      └──────────────────┘
                                  │                         │
                                  ▼                         ▼
┌──────────────────┐      ┌─────────────────┐      ┌──────────────────┐
│  Product Updates │─────▶│  Hybrid Source  │─────▶│  Inventory State │
│  (Kafka Topic)   │      │  (Unbounded)    │      │  (Tracking)      │
└──────────────────┘      └─────────────────┘      └──────────────────┘
                                                            │
                                                            ▼
                                                   ┌──────────────────┐
                                                   │  Output Topics   │
                                                   │  - inventory-    │
                                                   │    events        │
                                                   │  - websocket_    │
                                                   │    fanout        │
                                                   └──────────────────┘
```

## Key Features

### Hybrid Source Pattern
The job demonstrates Flink 1.20's Hybrid Source capability:
1. **Phase 1 (Bounded)**: Reads initial product catalog from JSON file
2. **Phase 2 (Unbounded)**: Seamlessly switches to Kafka for real-time updates
3. **Unified Processing**: Both sources processed through same pipeline

### Stateful Processing
- **Keyed State**: Tracks product history per product ID
- **Event Detection**: Identifies inventory changes, price updates, stock alerts
- **Timers**: Monitors for stale inventory (no updates > 1 hour)

### Event Types Generated
```java
public enum EventType {
    PRODUCT_ADDED,        // New product in catalog
    PRODUCT_UPDATED,      // Metadata changes
    INVENTORY_INCREASED,  // Stock replenishment
    INVENTORY_DECREASED,  // Sales/removals
    OUT_OF_STOCK,        // Inventory reaches zero
    BACK_IN_STOCK,       // Restocked from zero
    PRICE_CHANGED,       // Price modifications
    LOW_STOCK_ALERT,     // Inventory ≤ 10 units
    STALE_INVENTORY,     // No updates > 1 hour
    SALE_STARTED         // Price drop ≥ 10%
}
```

## Project Structure

```
flink-inventory/
├── src/main/java/com/ververica/composable_job/flink/inventory/
│   └── InventoryManagementJob.java
│       ├── main()                    # Job entry point
│       ├── createHybridSource()      # Hybrid source configuration
│       ├── JsonToProductParser       # JSON parsing processor
│       ├── InventoryStateProcessor   # Stateful inventory tracking
│       └── InventoryEvent           # Event data model
├── src/main/resources/
│   └── log4j2.properties            # Logging configuration
├── build.gradle                      # Build configuration
└── README.md
```

## Configuration

### Environment Variables
```bash
# Kafka/Redpanda connection
KAFKA_BOOTSTRAP_SERVERS=localhost:19092  # Default: localhost:19092

# Initial products file path
INITIAL_PRODUCTS_FILE=data/initial-products.json  # Default path
```

### Input Sources

#### 1. Initial Products File (Bounded)
Location: `data/initial-products.json`
Format: JSON array of products
```json
[
  {
    "productId": "PROD_0001",
    "name": "TechPro UltraBook Pro 15",
    "description": "High-performance laptop",
    "category": "Electronics",
    "brand": "TechPro",
    "price": 1899.99,
    "inventory": 25,
    "imageUrl": "https://picsum.photos/400/300?random=1",
    "tags": ["laptop", "computer", "productivity"],
    "rating": 4.5,
    "reviewCount": 127
  }
]
```

#### 2. Product Updates Topic (Unbounded)
Topic: `product-updates`
Format: Single product JSON objects

### Output Topics

#### inventory-events
Contains detailed inventory change events:
```json
{
  "productId": "PROD_0001",
  "productName": "UltraBook Pro 15",
  "category": "Electronics",
  "eventType": "INVENTORY_DECREASED",
  "previousInventory": 25,
  "currentInventory": 20,
  "previousPrice": 1899.99,
  "currentPrice": 1899.99,
  "changeReason": "5 units sold/removed",
  "timestamp": 1693574400000,
  "isInitialLoad": false,
  "millisSinceLastUpdate": 3600000
}
```

#### websocket_fanout
Formatted for WebSocket broadcast:
```json
{
  "eventType": "INVENTORY_UPDATE",
  "payload": { /* inventory event */ },
  "timestamp": 1693574400000
}
```

## Running the Job

### Prerequisites
1. Apache Flink 1.20+ cluster or local runtime
2. Kafka/Redpanda cluster running
3. Required topics created:
   - `product-updates` (input)
   - `inventory-events` (output)
   - `websocket_fanout` (output)

### Local Development

```bash
# Build the job
./gradlew :flink-inventory:shadowJar

# Run with Flink CLI
flink run -c com.ververica.composable_job.flink.inventory.InventoryManagementJob \
  flink-inventory/build/libs/flink-inventory-all.jar

# Or run directly with Gradle
./gradlew :flink-inventory:run
```

### Topic Creation

```bash
# Create required topics (using Redpanda)
rpk topic create product-updates --brokers localhost:19092
rpk topic create inventory-events --brokers localhost:19092
rpk topic create websocket_fanout --brokers localhost:19092
```

### Sending Test Updates

```bash
# Send a product update to trigger processing
echo '{"productId":"PROD_0001","name":"Updated Product","inventory":15,"price":1799.99}' | \
  rpk topic produce product-updates --brokers localhost:19092
```

## Processing Pipeline

### 1. Source Phase
```java
HybridSource<String> hybridSource = HybridSource.builder(fileSource)
    .addSource(kafkaSource)  // Switches after file is complete
    .build();
```

### 2. Parsing Phase
- Handles both array format (initial file) and single objects (Kafka)
- Error handling with logging (production: dead letter queue)

### 3. Stateful Processing
```java
KeyedProcessFunction<String, Product, InventoryEvent>
├── open()           # Initialize state descriptors
├── processElement() # Process each product update
│   ├── Compare with previous state
│   ├── Detect change type
│   ├── Generate primary event
│   └── Generate alerts (low stock, sales)
└── onTimer()        # Check for stale inventory
```

### 4. Alert Generation
- **Low Stock**: Triggered when inventory ≤ 10
  - Critical (≤ 5 units)
  - Warning (6-10 units)
- **Sales**: Price drops ≥ 10%
- **Stale Data**: No updates for > 1 hour

## State Management

### ValueState Components
1. **lastProductState**: Previous product snapshot
2. **lastUpdateTime**: Timestamp of last update

### State Operations
- **Checkpointing**: Enabled for fault tolerance
- **State Backend**: Default (HashMapStateBackend for local)
- **Parallelism**: Set to 2 for better throughput

## Monitoring & Debugging

### Logging
Configured via `log4j2.properties`:
- INFO level for job lifecycle
- DEBUG for product parsing
- ERROR for failures

### Key Metrics to Monitor
1. **Records Processed**: Initial load + updates
2. **Event Types Distribution**: Track event type frequencies
3. **Processing Latency**: Time from update to event generation
4. **State Size**: Growth of product state over time

### Debugging Tips

```bash
# Watch the inventory-events topic
rpk topic consume inventory-events --brokers localhost:19092

# Monitor Flink job
flink list
flink cancel <job-id>

# Check logs
tail -f /tmp/flink-logs/*.log
```

## Performance Tuning

### Parallelism
```java
env.setParallelism(2);  // Adjust based on cluster
```

### Kafka Source Configuration
```java
.setProperty("partition.discovery.interval.ms", "10000")
.setProperty("commit.offsets.on.checkpoint", "true")
```

### Memory Settings
```bash
# For large product catalogs
-Xmx4g -Xms2g
```

## Error Handling

1. **Parse Errors**: Logged, skipped (or sent to DLQ)
2. **State Failures**: Recovered from checkpoints
3. **Kafka Failures**: Retries with backoff
4. **Timer Failures**: Logged, job continues

## Testing

### Unit Testing
```java
// Test event generation logic
InventoryStateProcessor processor = new InventoryStateProcessor();
// Mock context and test processElement()
```

### Integration Testing
1. Start local Flink mini-cluster
2. Use embedded Kafka for testing
3. Verify events in output topics

## Extending the Job

### Adding New Event Types
1. Add to `EventType` enum
2. Implement detection logic in `InventoryStateProcessor`
3. Update alert generation if needed

### Custom Alerts
```java
private void generateCustomAlerts(Product product, Collector<InventoryEvent> out) {
    // Add your custom alert logic
    if (customCondition) {
        InventoryEvent alert = new InventoryEvent();
        // Configure alert
        out.collect(alert);
    }
}
```

### Different Sources
Replace file source with:
- Database CDC (Debezium)
- S3/Object storage
- Another Kafka topic

## Troubleshooting

### Common Issues

1. **"Type of TypeVariable 'T' could not be determined"**
   - Solution: Add explicit type info: `Types.STRING`

2. **"Topic doesn't exist"**
   - Create topics before running job

3. **"Cannot connect to Kafka"**
   - Check KAFKA_BOOTSTRAP_SERVERS
   - Verify Kafka/Redpanda is running

4. **Memory Issues**
   - Increase heap size
   - Reduce parallelism
   - Enable incremental checkpoints

## Key Learning Points

This job demonstrates several advanced Flink patterns:

1. **Hybrid Source Pattern**: Seamlessly combine batch and stream processing
2. **Stateful Processing**: Maintain and evolve state across events
3. **Timer Service**: Time-based operations and alerts
4. **Multi-Sink Pattern**: Fan out to multiple downstream systems
5. **Event Detection**: Complex event processing with business logic
6. **CQRS Integration**: Feed read models via Kafka

Perfect for understanding modern stream processing architectures!

## License

Part of the Ververica Visual Demo project demonstrating Apache Flink capabilities.
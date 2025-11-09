# Flink Pattern: Hybrid Source (Bounded → Unbounded)

## 🎯 Learning Objectives

After completing this module, you will understand:
1. When and why to use Hybrid Sources
2. How to seamlessly transition from batch to streaming
3. The difference between bounded and unbounded sources
4. How to create custom StreamFormats for special file types

## 📖 Pattern Overview

### What is a Hybrid Source?

A **Hybrid Source** allows you to bootstrap Flink state from historical data (files, databases) before transitioning to a live stream (Kafka, Kinesis). This is essential for:

- **Initializing lookup tables** - Load product catalog before processing orders
- **Reprocessing scenarios** - Replay last 30 days of data, then continue live
- **Testing** - Use files in dev, switch to Kafka in production (same code!)
- **ML model bootstrapping** - Train on historical data, update with live data

### Visual Flow

```
┌─────────────────────────────────────────────────────────┐
│                                                           │
│   ┌──────────────┐                                       │
│   │  File Source │  (BOUNDED - has an end)               │
│   │              │                                       │
│   │  products.   │                                       │
│   │  json        │                                       │
│   └──────┬───────┘                                       │
│          │                                               │
│          │  [Reads entire file]                         │
│          │                                               │
│          ▼                                               │
│   ┌──────────────┐                                       │
│   │  Hybrid      │                                       │
│   │  Source      │  ← Automatically switches when file   │
│   │              │    completes                         │
│   └──────┬───────┘                                       │
│          │                                               │
│          │  [Continues with Kafka]                      │
│          │                                               │
│          ▼                                               │
│   ┌──────────────┐                                       │
│   │ Kafka Source │  (UNBOUNDED - runs forever)          │
│   │              │                                       │
│   │  product-    │                                       │
│   │  updates     │                                       │
│   └──────┬───────┘                                       │
│          │                                               │
│          ▼                                               │
│   Downstream Operators (don't know the difference!)     │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

## 🔑 Key Concepts

### Bounded vs Unbounded Sources

| Property | Bounded (File) | Unbounded (Kafka) |
|----------|---------------|-------------------|
| **Has End** | ✅ Yes | ❌ No |
| **Backpressure** | Handled by Flink | Offset-based |
| **Watermarks** | Can be perfect | Needs estimation |
| **Replayable** | Always | Depends on retention |
| **Use Case** | Historical data | Real-time events |

### Source Switching

When the file source finishes (returns no more records):
1. Hybrid Source **automatically** switches to Kafka
2. No interruption in data flow
3. Downstream operators see continuous stream
4. Checkpoints work across the boundary

## 💻 Code Example

```java
// Create file source (reads historical products)
FileSource<String> fileSource = FileSource
    .forRecordStreamFormat(new WholeFileStreamFormat(), new Path("data/products.json"))
    .build();

// Create Kafka source (receives live updates)
KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
    .setBootstrapServers("localhost:19092")
    .setTopics("product-updates")
    .setStartingOffsets(OffsetsInitializer.latest())  // Start AFTER file data
    .setValueOnlyDeserializer(new SimpleStringSchema())
    .build();

// Combine into hybrid source
HybridSource<String> hybrid = HybridSource.builder(fileSource)
    .addSource(kafkaSource)  // Automatically switches here
    .build();

// Use like any other source!
DataStream<String> stream = env.fromSource(
    hybrid,
    WatermarkStrategy.noWatermarks(),
    "Products"
);
```

## 🎓 Hands-On Exercise

### Exercise 1: Basic Hybrid Source

**Task:** Run the `HybridSourceExample` and observe the source switching.

```bash
# 1. Start Kafka/Redpanda
docker compose up -d redpanda

# 2. Create sample data file
echo '[{"productId":"P001","name":"Laptop"}]' > data/initial-products.json

# 3. Run the example
./gradlew :flink-inventory:run --args="--class HybridSourceExample"

# 4. Observe logs - you'll see:
# - File data processed first
# - Then "Switching to Kafka source"
# - Then waiting for Kafka messages

# 5. Publish to Kafka to see live processing
docker exec -it redpanda rpk topic produce product-updates
# Type: {"productId":"P002","name":"Mouse"}
# Press Ctrl+C
```

**Expected Output:**
```
INFO: Processing product from FILE: {"productId":"P001","name":"Laptop"}
INFO: File source completed, switching to Kafka...
INFO: Processing product from KAFKA: {"productId":"P002","name":"Mouse"}
```

### Exercise 2: Multiple File Sources

**Challenge:** Modify the example to read from TWO files before switching to Kafka.

**Hint:** You can chain multiple sources:
```java
HybridSource.builder(fileSource1)
    .addSource(fileSource2)
    .addSource(kafkaSource)
    .build();
```

**Files to create:**
- `data/products-electronics.json` - Electronics products
- `data/products-fashion.json` - Fashion products

**Expected Behavior:**
1. Read all electronics
2. Then read all fashion
3. Then start consuming from Kafka

<details>
<summary>Solution</summary>

```java
FileSource<String> electronicsSource = FileSource
    .forRecordStreamFormat(
        new WholeFileStreamFormat(),
        new Path("data/products-electronics.json")
    ).build();

FileSource<String> fashionSource = FileSource
    .forRecordStreamFormat(
        new WholeFileStreamFormat(),
        new Path("data/products-fashion.json")
    ).build();

KafkaSource<String> liveSource = /* ... */;

HybridSource<String> multiFileHybrid = HybridSource.builder(electronicsSource)
    .addSource(fashionSource)      // Second file
    .addSource(liveSource)          // Then Kafka
    .build();
```
</details>

### Exercise 3: Conditional Source Switching

**Advanced Challenge:** Add a third source type - read from a database table after files, before Kafka.

**Requirements:**
1. File → Database → Kafka
2. Database source uses JDBC connector
3. All three transitions are seamless

**Hints:**
- Use `JdbcConnectionOptions` for database
- Create a `ResultSetIterator` for reading
- Set appropriate fetch size for large tables

## 🔍 Common Pitfalls

### ❌ **Pitfall 1: Duplicate Data**

**Problem:**
```java
// Kafka starts from EARLIEST - will re-read old data!
.setStartingOffsets(OffsetsInitializer.earliest())
```

**Solution:**
```java
// Use LATEST to only get NEW data after file
.setStartingOffsets(OffsetsInitializer.latest())
```

### ❌ **Pitfall 2: File Not Found**

**Problem:** File doesn't exist, job fails immediately.

**Solution:**
```java
FileSource.builder()
    .setFileEnumerator(/* custom enumerator with fallback */)
    .allowNonExistentPaths()  // Continue if file missing
```

### ❌ **Pitfall 3: Different Schemas**

**Problem:** File has schema v1, Kafka has schema v2.

**Solution:** Use union types or schema evolution:
```java
.process(new ProcessFunction<String, Event>() {
    public void processElement(String value, Context ctx, Collector<Event> out) {
        Event event = parseWithSchemaEvolution(value);
        out.collect(event);
    }
})
```

## 📊 Performance Tips

1. **File Splitting:** For large files, use `TextLineInputFormat` instead of `WholeFileStreamFormat`
2. **Parallelism:** File source parallelism = number of files (max)
3. **Kafka Partitions:** Ensure Kafka has enough partitions for your parallelism
4. **Checkpointing:** First checkpoint after file source completes marks the boundary

## 🔗 Related Patterns

- **Pattern 02: Keyed State** - Use hybrid source to bootstrap keyed state
- **Watermark Strategies** - How to handle watermarks across source switching
- **Savepoints** - Restart from Kafka-only after initial bootstrap

## 📚 Further Reading

- [Flink Hybrid Source Documentation](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/hybrid_source/)
- [File Connectors](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/filesystem/)
- [Kafka Connector](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/kafka/)

## ✅ Quiz

Test your understanding:

1. **Q:** Can you switch from Kafka → File in a Hybrid Source?
   - **A:** Technically yes, but not recommended. Bounded sources should come first.

2. **Q:** What happens to checkpoints during source switching?
   - **A:** Checkpoints work normally. The next checkpoint after switching includes the new source's state.

3. **Q:** Can I restart a job from a savepoint and skip the file source?
   - **A:** Yes! Modify the code to only use Kafka source, restore from savepoint.

## 🎯 Next Steps

Once you're comfortable with Hybrid Sources, move to:
- **Pattern 02: Keyed State** - Learn how to maintain state per product
- **Pattern 03: Timers** - Detect stale data with timers

---

**Workshop Note:** In production, this pattern is used by companies processing billions of events to bootstrap reference data before handling live transactions.

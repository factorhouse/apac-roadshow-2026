# Flink Pattern: Keyed State (Per-Key State Management)

## Learning Objectives

After completing this module, you will understand:
1. How Flink partitions and manages state per key across parallel tasks
2. When to use ValueState, ListState, MapState, ReducingState, and AggregatingState
3. How keyed state enables fault tolerance through checkpointing
4. How to choose the right state backend (Heap vs RocksDB) for your use case
5. Best practices for state TTL, cleanup, and memory management

## Pattern Overview

### What is Keyed State?

**Keyed State** allows you to maintain independent state for each key in a stream. It's the foundation for stateful stream processing and is essential for:

- **Entity Tracking** - Track inventory per product, balance per account, status per device
- **Change Detection** - Detect price changes, inventory fluctuations, anomalies per entity
- **Aggregations** - Calculate running totals, averages, counts per key
- **Session Management** - Maintain shopping cart state, user sessions, device connections
- **Business Logic** - Implement stateful workflows, order processing, fraud detection

### Visual State Distribution

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Input Stream (Products)                           │
│                                                                       │
│   P001, P002, P003, P001, P004, P002, P003, P001 ...                │
└───────────────────────────────┬───────────────────────────────────┘
                                │
                                │ keyBy(product -> product.productId)
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│              Flink Automatically Partitions by Key                  │
│                                                                     │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐         │
│    │  Task 1      │    │  Task 2      │    │  Task 3      │         │
│    │  (P001, P004)│    │  (P002, P005)│    │  (P003, P006)│         │
│    │              │    │              │    │              │         │
│    │  State:      │    │  State:      │    │  State:      │         │
│    │  ┌────────┐  │    │  ┌────────┐  │    │  ┌────────┐  │         │
│    │  │ P001:  │  │    │  │ P002:  │  │    │  │ P003:  │  │         │
│    │  │ {last: │  │    │  │ {last: │  │    │  │ {last: │  │         │
│    │  │Product}│  │    │  │Product}│  │    │  │Product}│  │         │
│    │  └────────┘  │    │  └────────┘  │    │  └────────┘  │         │
│    │  ┌────────┐  │    │  ┌────────┐  │    │  ┌────────┐  │         │
│    │  │ P004:  │  │    │  │ P005:  │  │    │  │ P006:  │  │         │
│    │  │ {last: │  │    │  │ {last: │  │    │  │ {last: │  │         │
│    │  │ Product}  │    │  │Product}│  │    │  │Product}│  │         │
│    │  └────────┘  │    │  └────────┘  │    │  └────────┘  │         │
│    └──────────────┘    └──────────────┘    └──────────────┘         │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                │ Checkpointing
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      State Backend Storage                          │
│                                                                     │
│  ┌──────────────────────┐        ┌──────────────────────┐           │
│  │   Heap State Backend │        │ RocksDB State Backend│           │
│  │                      │        │                      │           │
│  │  - In JVM Heap       │        │ - On Local Disk      │           │
│  │  - Fast access       │        │ - Large state support│           │
│  │  - Limited by memory │        │ - Slower access      │           │
│  │  - GC overhead       │        │ - No GC overhead     │           │
│  └──────────────────────┘        └──────────────────────┘           │
│                                                                     │
│  Checkpoint:  Task 1 State → S3/HDFS/Filesystem                     │
│               Task 2 State → S3/HDFS/Filesystem                     │
│               Task 3 State → S3/HDFS/Filesystem                     │
│                                                                     │
│  Recovery:    Restore state from last checkpoint → Resume processing│
└─────────────────────────────────────────────────────────────────────┘
```

### How State Partitioning Works

```
Event Flow with State Access:

1. Product P001 arrives
   ↓
2. Hash(P001) % num_tasks → Task 2
   ↓
3. Task 2 retrieves state for key "P001"
   - First time: state.value() = null
   - Subsequent: state.value() = previous Product object
   ↓
4. Process event, update state
   ↓
5. state.update(newProduct)
   - State change recorded locally
   ↓
6. On checkpoint trigger:
   - Snapshot all state to persistent storage
   - State becomes durable
```

## Key Concepts

### State Types Comparison

| State Type | Storage | Use Case | Example |
|------------|---------|----------|---------|
| **ValueState\<T\>** | Single value per key | Latest value, flags, counters | Last product update, current inventory |
| **ListState\<T\>** | Ordered list per key | History, buffering, windows | Last 10 price changes, event buffer |
| **MapState\<K,V\>** | Key-value map per key | Lookups, categories, hierarchies | Sales by category, tags, attributes |
| **ReducingState\<T\>** | Aggregated value with ReduceFunction | Sums, counts, min/max | Total sales, item count |
| **AggregatingState\<IN,OUT\>** | Aggregated with AggregateFunction | Complex aggregations | Average price, variance, custom metrics |

### State Scoping

**Important:** Keyed state is scoped to BOTH the key AND the operator:

```java
// Stream 1: Track inventory
stream1
    .keyBy(p -> p.productId)
    .process(new InventoryTracker())  // State: last inventory

// Stream 2: Track price
stream2
    .keyBy(p -> p.productId)
    .process(new PriceTracker())      // State: last price (SEPARATE!)

// Even with same key (e.g., P001), each operator has independent state
```

### Automatic Partitioning

Flink automatically:
1. **Hashes the key** to determine which task processes it
2. **Routes all events** with the same key to the same task
3. **Isolates state** so tasks only see their assigned keys
4. **Checkpoints state** from all tasks for fault tolerance
5. **Redistributes state** when scaling (changing parallelism)

### Checkpointing and Fault Tolerance

```
Normal Processing:
─────────────────────────────────────────────────────────>
Event → Update State (in memory) → Continue
              ↓
        (State is volatile)


With Checkpointing:
─────────────────────────────────────────────────────────>
Event → Update State → Checkpoint Barrier → Snapshot State
                                                    ↓
                                            S3/HDFS/FS
                                                    ↓
                                          (State is durable)

On Failure:
─────────X (Job fails)
         ↓
    Restore from last checkpoint
         ↓
    Resume processing
```

**Checkpoint Guarantees:**
- **Exactly-once:** State and outputs are consistent (same result if replayed)
- **At-least-once:** Faster but may duplicate outputs
- **No checkpointing:** Lose all state on failure

### State Backends

#### 1. Heap State Backend (HashMapStateBackend)

```java
env.setStateBackend(new HashMapStateBackend());
```

**Characteristics:**
- Stores state in JVM heap memory
- Fast access (no serialization)
- Limited by available heap size
- Garbage collection overhead
- **Best for:** Small state (< 1GB per task), low latency

#### 2. RocksDB State Backend (EmbeddedRocksDBStateBackend)

```java
env.setStateBackend(new EmbeddedRocksDBStateBackend());
```

**Characteristics:**
- Stores state on local disk (RocksDB)
- Serializes on write, deserializes on read
- Supports huge state (TBs per task)
- No GC overhead
- **Best for:** Large state, long TTLs, many keys

**Performance Comparison:**

| Metric | Heap Backend | RocksDB Backend |
|--------|--------------|-----------------|
| **Read Latency** | ~10 ns | ~1-10 μs |
| **Write Latency** | ~50 ns | ~5-50 μs |
| **State Size Limit** | Heap size | Disk size |
| **GC Impact** | High (state in heap) | Low (state on disk) |
| **Checkpoint Size** | Larger (full state) | Incremental supported |

### State TTL and Cleanup

State can grow unbounded if not cleaned up. Use State TTL:

```java
StateTtlConfig ttlConfig = StateTtlConfig
    .newBuilder(Time.hours(24))                               // TTL: 24 hours
    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)  // Reset on write
    .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)  // Hide expired
    .cleanupFullSnapshot()                                     // Cleanup on checkpoint
    .build();

ValueStateDescriptor<Product> descriptor =
    new ValueStateDescriptor<>("product", Product.class);
descriptor.enableTimeToLive(ttlConfig);

ValueState<Product> state = getRuntimeContext().getState(descriptor);
```

**TTL Update Strategies:**
- `OnCreateAndWrite` - Reset TTL on every state write (most common)
- `OnReadAndWrite` - Reset TTL on reads too (keeps active keys alive)
- `Disabled` - TTL never updates (fixed expiration)

**Cleanup Strategies:**
- `cleanupFullSnapshot()` - Remove during full checkpoints (simple, works everywhere)
- `cleanupIncrementally()` - Remove during state access (for RocksDB)
- `cleanupInRocksdbCompactFilter()` - Remove during RocksDB compaction (most efficient)

## Code Example with Annotations

```java
/**
 * Track inventory changes using ValueState
 */
public static class InventoryChangeDetector
        extends KeyedProcessFunction<String, Product, InventoryChange> {

    // [1] State: Store last seen product for each product ID
    //     - Scoped to key (productId)
    //     - Survives checkpoints
    //     - Automatically partitioned
    private transient ValueState<Product> lastProductState;

    // [2] State: Store timestamp of last update
    private transient ValueState<Long> lastUpdateTimeState;

    @Override
    public void open(Configuration parameters) {
        // [3] Create state descriptor
        //     - Name: for debugging/monitoring
        //     - Type: for serialization
        ValueStateDescriptor<Product> productDescriptor =
            new ValueStateDescriptor<>(
                "last-product",              // State name
                Product.class                // Type
            );

        // [4] Optional: Enable TTL to prevent unbounded growth
        StateTtlConfig ttlConfig = StateTtlConfig
            .newBuilder(Time.hours(24))      // Remove after 24h inactive
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
            .build();
        productDescriptor.enableTimeToLive(ttlConfig);

        // [5] Get state handle from runtime context
        //     - Runtime context provides access to state
        //     - Same descriptor = same state across restarts
        lastProductState = getRuntimeContext().getState(productDescriptor);

        // [6] Initialize timestamp state
        lastUpdateTimeState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-update-time", Long.class)
        );
    }

    @Override
    public void processElement(
            Product current,
            Context ctx,
            Collector<InventoryChange> out) throws Exception {

        // [7] Retrieve previous state for THIS key (productId)
        //     - If first time: returns null
        //     - Otherwise: returns last Product for this key
        Product previous = lastProductState.value();
        Long previousUpdateTime = lastUpdateTimeState.value();

        long currentTime = System.currentTimeMillis();

        // [8] Handle first-time key
        if (previous == null) {
            InventoryChange change = new InventoryChange();
            change.productId = current.productId;
            change.changeType = "NEW_PRODUCT";
            change.currentInventory = current.inventory;
            change.timestamp = currentTime;

            out.collect(change);

        } else {
            // [9] Detect inventory changes
            if (previous.inventory != current.inventory) {
                int delta = current.inventory - previous.inventory;

                InventoryChange change = new InventoryChange();
                change.productId = current.productId;
                change.changeType = delta > 0 ? "RESTOCK" : "SALE";
                change.previousInventory = previous.inventory;
                change.currentInventory = current.inventory;
                change.delta = delta;
                change.timestamp = currentTime;

                if (previousUpdateTime != null) {
                    change.timeSinceLastUpdate = currentTime - previousUpdateTime;
                }

                out.collect(change);
            }
        }

        // [10] Update state with new values
        //      - State update is local until checkpoint
        //      - Checkpoint makes it durable
        //      - IMPORTANT: Always update state if you want to track latest
        lastProductState.update(current);
        lastUpdateTimeState.update(currentTime);
    }
}
```

## State Types Deep Dive

### 1. ValueState\<T\> - Single Value

**Use When:** You need to store one value per key.

```java
// Descriptor
ValueStateDescriptor<Product> descriptor =
    new ValueStateDescriptor<>("last-product", Product.class);
ValueState<Product> state = getRuntimeContext().getState(descriptor);

// Access
Product last = state.value();           // Get (null if not set)
state.update(newProduct);               // Set
state.clear();                          // Remove
```

**Example:** Track last price for each product.

### 2. ListState\<T\> - Ordered List

**Use When:** You need to store multiple values per key (history, buffer).

```java
// Descriptor
ListStateDescriptor<Double> descriptor =
    new ListStateDescriptor<>("price-history", Double.class);
ListState<Double> state = getRuntimeContext().getListState(descriptor);

// Access
state.add(newPrice);                    // Append
Iterable<Double> prices = state.get();  // Get all (returns empty if not set)
state.update(Arrays.asList(p1, p2));    // Replace all
state.clear();                          // Remove all
```

**Example:** Track last 10 price changes per product.

### 3. MapState\<K,V\> - Key-Value Map

**Use When:** You need lookups by secondary key (categories, tags).

```java
// Descriptor
MapStateDescriptor<String, Integer> descriptor =
    new MapStateDescriptor<>("category-sales", String.class, Integer.class);
MapState<String, Integer> state = getRuntimeContext().getMapState(descriptor);

// Access
Integer count = state.get("Electronics");    // Get by key (null if not exists)
state.put("Electronics", 100);               // Put
state.remove("Electronics");                 // Remove key
Iterable<Map.Entry<String, Integer>> all = state.entries();  // Get all
state.clear();                               // Remove all
```

**Example:** Track sales count per category for each product.

### 4. ReducingState\<T\> - Aggregated Value

**Use When:** You need to aggregate values with a ReduceFunction (same input/output type).

```java
// Descriptor with ReduceFunction
ReducingStateDescriptor<Integer> descriptor =
    new ReducingStateDescriptor<>(
        "total-sales",
        new ReduceFunction<Integer>() {
            @Override
            public Integer reduce(Integer a, Integer b) {
                return a + b;  // Sum
            }
        },
        Integer.class
    );
ReducingState<Integer> state = getRuntimeContext().getReducingState(descriptor);

// Access
state.add(5);              // Add value (automatically reduces)
Integer total = state.get();  // Get aggregated result
state.clear();             // Remove
```

**Example:** Track total sales amount per product.

### 5. AggregatingState\<IN,OUT\> - Custom Aggregation

**Use When:** You need complex aggregation with different input/output types.

```java
// Descriptor with AggregateFunction
AggregatingStateDescriptor<Double, Tuple2<Double, Long>, Double> descriptor =
    new AggregatingStateDescriptor<>(
        "average-price",
        new AggregateFunction<Double, Tuple2<Double, Long>, Double>() {
            @Override
            public Tuple2<Double, Long> createAccumulator() {
                return new Tuple2<>(0.0, 0L);  // (sum, count)
            }

            @Override
            public Tuple2<Double, Long> add(Double value, Tuple2<Double, Long> acc) {
                return new Tuple2<>(acc.f0 + value, acc.f1 + 1);
            }

            @Override
            public Double getResult(Tuple2<Double, Long> acc) {
                return acc.f1 > 0 ? acc.f0 / acc.f1 : 0.0;  // average
            }

            @Override
            public Tuple2<Double, Long> merge(Tuple2<Double, Long> a, Tuple2<Double, Long> b) {
                return new Tuple2<>(a.f0 + b.f0, a.f1 + b.f1);
            }
        },
        Types.TUPLE(Types.DOUBLE, Types.LONG)
    );
AggregatingState<Double, Double> state = getRuntimeContext().getAggregatingState(descriptor);

// Access
state.add(99.99);          // Add value (automatically aggregates)
Double avg = state.get();  // Get result (average)
state.clear();             // Remove
```

**Example:** Track average price per product.

## When to Use Each State Type

| Scenario | Best State Type | Why |
|----------|-----------------|-----|
| Track latest product info | ValueState\<Product\> | Single value per key |
| Store last 10 events | ListState\<Event\> | Need ordered history |
| Count items by category | MapState\<String, Integer\> | Secondary key lookups |
| Calculate total sales | ReducingState\<Double\> | Simple aggregation (sum) |
| Calculate average price | AggregatingState | Different input/output types |
| Track session cart items | ListState\<CartItem\> | Need to iterate items |
| Implement deduplication | ValueState\<Boolean\> | Track "seen" flag |
| Store last N unique visitors | ListState\<String\> + dedup logic | Bounded history |
| Category-level aggregations | MapState\<Category, Metrics\> | Per-category state |
| Running min/max/count | ReducingState | Efficient aggregation |

## Hands-On Exercises

### Exercise 1: Track Total Sales Amount Using ReducingState

**Task:** Modify `KeyedStateExample` to track total sales revenue per product using ReducingState.

**Requirements:**
1. Use `ReducingState<Double>` to sum all sales amounts
2. Emit total sales whenever a sale occurs
3. Print: "Product P001: Total sales = $1,234.56"

**Steps:**

```java
// 1. Add ReducingState field
private transient ReducingState<Double> totalSalesState;

// 2. Initialize in open()
ReducingStateDescriptor<Double> salesDescriptor =
    new ReducingStateDescriptor<>(
        "total-sales",
        new ReduceFunction<Double>() {
            @Override
            public Double reduce(Double a, Double b) {
                return a + b;  // Sum
            }
        },
        Double.class
    );
totalSalesState = getRuntimeContext().getReducingState(salesDescriptor);

// 3. Update in processElement()
if (current.inventory < previous.inventory) {
    // Calculate sale amount
    int sold = previous.inventory - current.inventory;
    double saleAmount = sold * current.price;

    // Add to total
    totalSalesState.add(saleAmount);

    // Get total
    Double total = totalSalesState.get();
    System.out.println("Product " + current.productId +
                       ": Total sales = $" + total);
}
```

**Expected Output:**
```
Product LAPTOP_001: Total sales = $999.99
Product LAPTOP_001: Total sales = $6,999.93  (cumulative)
Product MOUSE_001: Total sales = $149.95
```

<details>
<summary>Full Solution</summary>

```java
public static class TotalSalesTracker
        extends KeyedProcessFunction<String, Product, SalesReport> {

    private transient ValueState<Product> lastProductState;
    private transient ReducingState<Double> totalSalesState;

    @Override
    public void open(Configuration parameters) {
        lastProductState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-product", Product.class)
        );

        ReducingStateDescriptor<Double> salesDescriptor =
            new ReducingStateDescriptor<>(
                "total-sales",
                new ReduceFunction<Double>() {
                    @Override
                    public Double reduce(Double a, Double b) {
                        return a + b;
                    }
                },
                Double.class
            );
        totalSalesState = getRuntimeContext().getReducingState(salesDescriptor);
    }

    @Override
    public void processElement(
            Product current,
            Context ctx,
            Collector<SalesReport> out) throws Exception {

        Product previous = lastProductState.value();

        if (previous != null && current.inventory < previous.inventory) {
            // Calculate sale amount
            int unitsSold = previous.inventory - current.inventory;
            double saleAmount = unitsSold * current.price;

            // Add to total
            totalSalesState.add(saleAmount);

            // Emit report
            SalesReport report = new SalesReport();
            report.productId = current.productId;
            report.unitsSold = unitsSold;
            report.saleAmount = saleAmount;
            report.totalSales = totalSalesState.get();
            report.timestamp = System.currentTimeMillis();

            out.collect(report);

            System.out.println(String.format(
                "Product %s: Sold %d units for $%.2f - Total sales: $%.2f",
                current.productId, unitsSold, saleAmount, report.totalSales
            ));
        }

        lastProductState.update(current);
    }
}

public static class SalesReport implements Serializable {
    public String productId;
    public int unitsSold;
    public double saleAmount;
    public double totalSales;
    public long timestamp;
}
```
</details>

### Exercise 2: Store Last 10 Price Changes Using ListState

**Challenge:** Track the last 10 price changes for each product and detect trends.

**Requirements:**
1. Use `ListState<Double>` to store price history
2. Keep only last 10 prices (remove oldest if > 10)
3. Detect trend: "INCREASING" if last 3 prices going up, "DECREASING" if going down
4. Emit price trend report

**Hints:**
```java
// Add price to history
priceHistoryState.add(currentPrice);

// Get all prices
List<Double> prices = new ArrayList<>();
priceHistoryState.get().forEach(prices::add);

// Keep only last 10
if (prices.size() > 10) {
    prices.remove(0);  // Remove oldest
    priceHistoryState.update(prices);
}

// Check trend (last 3 prices)
if (prices.size() >= 3) {
    int n = prices.size();
    boolean increasing =
        prices.get(n-1) > prices.get(n-2) &&
        prices.get(n-2) > prices.get(n-3);
    // ...
}
```

**Expected Output:**
```
Product LAPTOP_001: Price history [999.99, 949.99, 899.99] - Trend: DECREASING
Product LAPTOP_001: Price history [899.99, 949.99, 999.99] - Trend: INCREASING
```

<details>
<summary>Full Solution</summary>

```java
public static class PriceTrendTracker
        extends KeyedProcessFunction<String, Product, PriceTrend> {

    private transient ListState<Double> priceHistoryState;
    private static final int MAX_HISTORY = 10;

    @Override
    public void open(Configuration parameters) {
        priceHistoryState = getRuntimeContext().getListState(
            new ListStateDescriptor<>("price-history", Double.class)
        );
    }

    @Override
    public void processElement(
            Product current,
            Context ctx,
            Collector<PriceTrend> out) throws Exception {

        // Add current price to history
        priceHistoryState.add(current.price);

        // Get all prices
        List<Double> prices = new ArrayList<>();
        priceHistoryState.get().forEach(prices::add);

        // Keep only last 10
        if (prices.size() > MAX_HISTORY) {
            prices = prices.subList(prices.size() - MAX_HISTORY, prices.size());
            priceHistoryState.update(prices);
        }

        // Detect trend (last 3 prices)
        String trend = "STABLE";
        if (prices.size() >= 3) {
            int n = prices.size();
            double p1 = prices.get(n-3);
            double p2 = prices.get(n-2);
            double p3 = prices.get(n-1);

            if (p3 > p2 && p2 > p1) {
                trend = "INCREASING";
            } else if (p3 < p2 && p2 < p1) {
                trend = "DECREASING";
            }
        }

        // Emit trend report
        PriceTrend trendReport = new PriceTrend();
        trendReport.productId = current.productId;
        trendReport.priceHistory = new ArrayList<>(prices);
        trendReport.trend = trend;
        trendReport.currentPrice = current.price;
        trendReport.timestamp = System.currentTimeMillis();

        out.collect(trendReport);

        System.out.println(String.format(
            "Product %s: Price history %s - Trend: %s",
            current.productId, prices, trend
        ));
    }
}

public static class PriceTrend implements Serializable {
    public String productId;
    public List<Double> priceHistory;
    public String trend;
    public double currentPrice;
    public long timestamp;
}
```
</details>

### Exercise 3: Track Sales by Category Using MapState

**Advanced Challenge:** Track sales metrics per category for each product using MapState.

**Scenario:** Products can belong to multiple categories (e.g., "Electronics", "Gaming", "Premium"). Track sales separately for each category.

**Requirements:**
1. Use `MapState<String, Integer>` to track units sold per category
2. Product has `categories` field: `List<String>`
3. When product is sold, increment count for ALL its categories
4. Emit category-level sales report

**Example Data:**
```java
Product laptop = new Product();
laptop.productId = "LAPTOP_001";
laptop.categories = Arrays.asList("Electronics", "Gaming", "Premium");
laptop.inventory = 10;

// After sale (10 → 8):
// MapState for LAPTOP_001:
//   "Electronics" → 2
//   "Gaming" → 2
//   "Premium" → 2
```

**Hints:**
```java
// Initialize MapState
MapStateDescriptor<String, Integer> descriptor =
    new MapStateDescriptor<>(
        "category-sales",
        String.class,
        Integer.class
    );
categorySalesState = getRuntimeContext().getMapState(descriptor);

// Update on sale
for (String category : product.categories) {
    Integer currentCount = categorySalesState.get(category);
    if (currentCount == null) currentCount = 0;
    categorySalesState.put(category, currentCount + unitsSold);
}

// Get all category sales
for (Map.Entry<String, Integer> entry : categorySalesState.entries()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

<details>
<summary>Full Solution</summary>

```java
public static class CategorySalesTracker
        extends KeyedProcessFunction<String, Product, CategorySalesReport> {

    private transient ValueState<Product> lastProductState;
    private transient MapState<String, Integer> categorySalesState;

    @Override
    public void open(Configuration parameters) {
        lastProductState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-product", Product.class)
        );

        MapStateDescriptor<String, Integer> mapDescriptor =
            new MapStateDescriptor<>(
                "category-sales",
                String.class,
                Integer.class
            );
        categorySalesState = getRuntimeContext().getMapState(mapDescriptor);
    }

    @Override
    public void processElement(
            Product current,
            Context ctx,
            Collector<CategorySalesReport> out) throws Exception {

        Product previous = lastProductState.value();

        if (previous != null && current.inventory < previous.inventory) {
            int unitsSold = previous.inventory - current.inventory;

            // Update sales for each category
            if (current.categories != null) {
                for (String category : current.categories) {
                    Integer currentCount = categorySalesState.get(category);
                    if (currentCount == null) currentCount = 0;
                    categorySalesState.put(category, currentCount + unitsSold);
                }

                // Emit report
                CategorySalesReport report = new CategorySalesReport();
                report.productId = current.productId;
                report.unitsSold = unitsSold;
                report.categorySales = new HashMap<>();

                for (Map.Entry<String, Integer> entry : categorySalesState.entries()) {
                    report.categorySales.put(entry.getKey(), entry.getValue());
                }

                out.collect(report);

                System.out.println(String.format(
                    "Product %s: Sold %d units - Category sales: %s",
                    current.productId, unitsSold, report.categorySales
                ));
            }
        }

        lastProductState.update(current);
    }
}

public static class CategorySalesReport implements Serializable {
    public String productId;
    public int unitsSold;
    public Map<String, Integer> categorySales;
    public long timestamp;
}
```
</details>

## Common Pitfalls

### Pitfall 1: Forgetting to Check for Null State

**Problem:**
```java
// BAD: NPE when state is null (first time for key)
public void processElement(Product product, Context ctx, Collector<Alert> out) {
    Product last = lastProductState.value();
    int delta = product.inventory - last.inventory;  // NPE if last == null!
    // ...
}
```

**Solution:**
```java
// GOOD: Always check for null
public void processElement(Product product, Context ctx, Collector<Alert> out) {
    Product last = lastProductState.value();

    if (last == null) {
        // First time seeing this key
        lastProductState.update(product);
        return;
    }

    int delta = product.inventory - last.inventory;  // Safe
    // ...
}
```

### Pitfall 2: Not Initializing State in open()

**Problem:**
```java
// BAD: State not initialized
public static class MyFunction extends KeyedProcessFunction<String, Product, Alert> {
    private transient ValueState<Product> state;  // Never initialized!

    @Override
    public void processElement(Product product, Context ctx, Collector<Alert> out) {
        Product last = state.value();  // NPE: state is null!
    }
}
```

**Solution:**
```java
// GOOD: Initialize in open()
public static class MyFunction extends KeyedProcessFunction<String, Product, Alert> {
    private transient ValueState<Product> state;

    @Override
    public void open(Configuration parameters) {
        state = getRuntimeContext().getState(
            new ValueStateDescriptor<>("product", Product.class)
        );  // Properly initialized
    }

    @Override
    public void processElement(Product product, Context ctx, Collector<Alert> out) {
        Product last = state.value();  // Works!
    }
}
```

### Pitfall 3: Memory Leaks Without State TTL

**Problem:**
```java
// BAD: State grows forever
// Scenario: 1M products, average 1KB state each = 1GB
// After 1 year: Most products inactive, but state still in memory!

ValueStateDescriptor<Product> descriptor =
    new ValueStateDescriptor<>("product", Product.class);
// No TTL configured - state lives forever
```

**Solution:**
```java
// GOOD: Configure TTL for inactive keys
StateTtlConfig ttlConfig = StateTtlConfig
    .newBuilder(Time.days(30))  // Remove after 30 days inactive
    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
    .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
    .cleanupFullSnapshot()  // Cleanup on checkpoint
    .build();

ValueStateDescriptor<Product> descriptor =
    new ValueStateDescriptor<>("product", Product.class);
descriptor.enableTimeToLive(ttlConfig);  // Prevent leaks

ValueState<Product> state = getRuntimeContext().getState(descriptor);
```

**Impact:**
- **Without TTL:** State size = (active keys + inactive keys)
- **With TTL:** State size = active keys only
- **Memory savings:** 70-90% in typical scenarios

### Pitfall 4: Using Wrong State Type

**Problem:**
```java
// BAD: Using ListState to track single value
ListState<Product> productState;  // Overkill for single value

public void processElement(Product product, Context ctx, Collector<Alert> out) {
    List<Product> products = new ArrayList<>();
    productState.get().forEach(products::add);

    Product last = products.isEmpty() ? null : products.get(products.size() - 1);
    // Complex and inefficient!
}
```

**Solution:**
```java
// GOOD: Use ValueState for single value
ValueState<Product> productState;  // Simple and efficient

public void processElement(Product product, Context ctx, Collector<Alert> out) {
    Product last = productState.value();  // Direct access
    // ...
}
```

**State Type Selection:**
- Need ONE value? → `ValueState`
- Need MULTIPLE values (history)? → `ListState`
- Need LOOKUPS by key? → `MapState`
- Need AGGREGATION (sum)? → `ReducingState`
- Need COMPLEX AGGREGATION (avg)? → `AggregatingState`

### Pitfall 5: Mutating State Objects Directly

**Problem:**
```java
// BAD: Mutating state object without calling update()
Product product = productState.value();
product.inventory = 100;  // Modifies object, but Flink doesn't know!
// State change NOT checkpointed!
```

**Solution:**
```java
// GOOD: Always call update() after modification
Product product = productState.value();
product.inventory = 100;
productState.update(product);  // Tell Flink about the change

// OR: Create new object
Product updated = new Product();
updated.productId = product.productId;
updated.inventory = 100;
productState.update(updated);  // Safe
```

**Why?** Flink tracks state changes only through `update()` calls for checkpointing.

## Performance Tips

### 1. Choose the Right State Backend

**Use Heap Backend when:**
- State size < 1GB per task
- Low latency critical (microseconds)
- Small number of keys (< 100K)
- Frequent state access

**Use RocksDB Backend when:**
- State size > 1GB per task
- Many keys (millions+)
- Long TTLs (weeks, months)
- Incremental checkpoints needed

**Configuration:**
```java
// Heap (fast, limited)
env.setStateBackend(new HashMapStateBackend());
env.getCheckpointConfig().setCheckpointStorage("s3://bucket/checkpoints");

// RocksDB (scalable, slower)
env.setStateBackend(new EmbeddedRocksDBStateBackend());
env.getCheckpointConfig().setCheckpointStorage("s3://bucket/checkpoints");
```

### 2. Configure State TTL Appropriately

```java
// Short TTL for high-churn keys (shopping carts, sessions)
StateTtlConfig.newBuilder(Time.hours(2))  // 2 hour cart expiration

// Long TTL for stable keys (user profiles, product catalog)
StateTtlConfig.newBuilder(Time.days(90))  // 90 day user profile

// Cleanup strategy (performance impact)
.cleanupIncrementally(10, false)     // Clean 10 entries per access (low overhead)
.cleanupInRocksdbCompactFilter(1000) // Clean during compaction (RocksDB only)
```

### 3. Monitor State Size

```java
// Add metrics to track state growth
getRuntimeContext().getMetricGroup()
    .gauge("state-size", () -> {
        // Estimate state size
        return calculateStateSize();
    });

// Log state access patterns
LOG.info("State access for key {}: {} entries",
    ctx.getCurrentKey(),
    Iterables.size(listState.get())
);
```

### 4. Optimize Serialization

**Use efficient serializers:**
```java
// BAD: Generic Kryo serializer (slow)
new ValueStateDescriptor<>("state", Product.class);  // Uses Kryo by default

// GOOD: Register POJO or use TypeInformation
TypeInformation<Product> typeInfo = TypeInformation.of(Product.class);
new ValueStateDescriptor<>("state", typeInfo);  // POJO serializer (faster)

// BEST: Avro/Protobuf for complex objects
AvroTypeInfo<Product> avroType = new AvroTypeInfo<>(Product.class);
new ValueStateDescriptor<>("state", avroType);  // Avro serializer (fastest)
```

**Serialization Performance:**
| Serializer | Speed | Size | Schema Evolution |
|------------|-------|------|------------------|
| Kryo | Slow | Large | No |
| POJO | Fast | Medium | Limited |
| Avro | Fastest | Small | Yes |
| Protobuf | Fastest | Smallest | Yes |

### 5. Batch State Updates

```java
// BAD: Multiple state updates
for (int i = 0; i < 1000; i++) {
    listState.add(event);  // 1000 state updates
}

// GOOD: Batch update
List<Event> batch = new ArrayList<>();
for (int i = 0; i < 1000; i++) {
    batch.add(event);
}
listState.update(batch);  // 1 state update
```

## Real-World Production Examples

### 1. E-Commerce: Shopping Cart State

```java
/**
 * Maintain shopping cart state per user
 * - Track items added/removed
 * - Calculate totals
 * - Handle cart expiration (2 hour TTL)
 */
public static class ShoppingCartManager
        extends KeyedProcessFunction<String, CartEvent, CartUpdate> {

    private transient ListState<CartItem> cartItemsState;
    private transient ValueState<Long> lastActivityState;

    @Override
    public void open(Configuration parameters) {
        // Cart items with 2-hour TTL
        StateTtlConfig ttlConfig = StateTtlConfig
            .newBuilder(Time.hours(2))
            .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
            .cleanupIncrementally(5, false)
            .build();

        ListStateDescriptor<CartItem> cartDescriptor =
            new ListStateDescriptor<>("cart-items", CartItem.class);
        cartDescriptor.enableTimeToLive(ttlConfig);
        cartItemsState = getRuntimeContext().getListState(cartDescriptor);

        lastActivityState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-activity", Long.class)
        );
    }

    @Override
    public void processElement(CartEvent event, Context ctx, Collector<CartUpdate> out) {
        if ("ADD".equals(event.action)) {
            cartItemsState.add(event.item);
        } else if ("REMOVE".equals(event.action)) {
            // Remove item from cart
            List<CartItem> items = new ArrayList<>();
            cartItemsState.get().forEach(items::add);
            items.removeIf(item -> item.productId.equals(event.item.productId));
            cartItemsState.update(items);
        }

        // Calculate total
        double total = 0;
        int itemCount = 0;
        for (CartItem item : cartItemsState.get()) {
            total += item.price * item.quantity;
            itemCount += item.quantity;
        }

        // Emit cart update
        CartUpdate update = new CartUpdate();
        update.userId = ctx.getCurrentKey();
        update.itemCount = itemCount;
        update.totalAmount = total;
        update.timestamp = System.currentTimeMillis();
        out.collect(update);

        lastActivityState.update(update.timestamp);
    }
}
```

### 2. IoT: Device State Tracking

```java
/**
 * Track device state and detect anomalies
 * - Store last 100 readings per device
 * - Calculate moving average
 * - Detect anomalies (> 3 std dev)
 */
public static class DeviceAnomalyDetector
        extends KeyedProcessFunction<String, SensorReading, Anomaly> {

    private transient ListState<Double> readingsState;
    private transient AggregatingState<Double, Double> avgState;

    @Override
    public void open(Configuration parameters) {
        // Store last 100 readings
        readingsState = getRuntimeContext().getListState(
            new ListStateDescriptor<>("readings", Double.class)
        );

        // Calculate moving average
        AggregatingStateDescriptor<Double, Tuple2<Double, Long>, Double> avgDescriptor =
            new AggregatingStateDescriptor<>(
                "moving-avg",
                new AggregateFunction<Double, Tuple2<Double, Long>, Double>() {
                    @Override
                    public Tuple2<Double, Long> createAccumulator() {
                        return new Tuple2<>(0.0, 0L);
                    }

                    @Override
                    public Tuple2<Double, Long> add(Double value, Tuple2<Double, Long> acc) {
                        return new Tuple2<>(acc.f0 + value, acc.f1 + 1);
                    }

                    @Override
                    public Double getResult(Tuple2<Double, Long> acc) {
                        return acc.f1 > 0 ? acc.f0 / acc.f1 : 0.0;
                    }

                    @Override
                    public Tuple2<Double, Long> merge(Tuple2<Double, Long> a, Tuple2<Double, Long> b) {
                        return new Tuple2<>(a.f0 + b.f0, a.f1 + b.f1);
                    }
                },
                Types.TUPLE(Types.DOUBLE, Types.LONG)
            );
        avgState = getRuntimeContext().getAggregatingState(avgDescriptor);
    }

    @Override
    public void processElement(SensorReading reading, Context ctx, Collector<Anomaly> out) {
        // Add to readings history
        readingsState.add(reading.value);

        // Keep only last 100
        List<Double> readings = new ArrayList<>();
        readingsState.get().forEach(readings::add);
        if (readings.size() > 100) {
            readings = readings.subList(readings.size() - 100, readings.size());
            readingsState.update(readings);
        }

        // Update moving average
        avgState.add(reading.value);
        Double avg = avgState.get();

        // Calculate standard deviation
        double variance = 0;
        for (Double val : readings) {
            variance += Math.pow(val - avg, 2);
        }
        double stdDev = Math.sqrt(variance / readings.size());

        // Detect anomaly (> 3 standard deviations)
        if (Math.abs(reading.value - avg) > 3 * stdDev) {
            Anomaly anomaly = new Anomaly();
            anomaly.deviceId = ctx.getCurrentKey();
            anomaly.value = reading.value;
            anomaly.average = avg;
            anomaly.stdDev = stdDev;
            anomaly.severity = "HIGH";
            out.collect(anomaly);
        }
    }
}
```

### 3. Finance: Account Balance Tracking

```java
/**
 * Track account balances and transaction history
 * - Maintain current balance per account
 * - Store last 1000 transactions
 * - Detect overdrafts and fraud patterns
 */
public static class AccountBalanceTracker
        extends KeyedProcessFunction<String, Transaction, BalanceUpdate> {

    private transient ValueState<Double> balanceState;
    private transient ListState<Transaction> transactionHistoryState;
    private transient MapState<String, Integer> transactionTypeCountState;

    @Override
    public void open(Configuration parameters) {
        balanceState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("balance", Double.class)
        );

        transactionHistoryState = getRuntimeContext().getListState(
            new ListStateDescriptor<>("tx-history", Transaction.class)
        );

        transactionTypeCountState = getRuntimeContext().getMapState(
            new MapStateDescriptor<>("tx-type-count", String.class, Integer.class)
        );
    }

    @Override
    public void processElement(Transaction tx, Context ctx, Collector<BalanceUpdate> out) {
        // Get current balance
        Double currentBalance = balanceState.value();
        if (currentBalance == null) currentBalance = 0.0;

        // Update balance
        double newBalance = currentBalance + tx.amount;  // Negative for withdrawals
        balanceState.update(newBalance);

        // Add to transaction history
        transactionHistoryState.add(tx);

        // Update transaction type counts
        Integer count = transactionTypeCountState.get(tx.type);
        transactionTypeCountState.put(tx.type, count == null ? 1 : count + 1);

        // Emit balance update
        BalanceUpdate update = new BalanceUpdate();
        update.accountId = ctx.getCurrentKey();
        update.previousBalance = currentBalance;
        update.newBalance = newBalance;
        update.transaction = tx;
        update.overdraft = newBalance < 0;
        update.timestamp = System.currentTimeMillis();
        out.collect(update);

        // Check for fraud patterns
        if (newBalance < -1000) {
            System.err.println("FRAUD ALERT: Large overdraft for account " +
                               ctx.getCurrentKey());
        }
    }
}
```

## Quiz

Test your understanding:

1. **Q:** What happens to keyed state when you change the parallelism of a job?
   - **A:** Flink automatically redistributes state to tasks based on the new key-to-task mapping. State is preserved during rescaling using savepoints/checkpoints.

2. **Q:** Can two different operators share the same keyed state?
   - **A:** No. Keyed state is scoped to both the key AND the operator. Each operator has its own independent state even for the same key.

3. **Q:** What's the difference between `update(null)` and `clear()` on ValueState?
   - **A:** They have the same effect: both remove the state. `clear()` is preferred for clarity.

4. **Q:** When should you use ReducingState vs AggregatingState?
   - **A:** Use ReducingState when input and output types are the same (e.g., sum of integers). Use AggregatingState when they differ (e.g., average: input=Double, output=Double, accumulator=Tuple2<Double,Long>).

5. **Q:** How does State TTL affect checkpoints?
   - **A:** Expired state is removed from checkpoints (using cleanup strategies), reducing checkpoint size. The actual removal timing depends on the cleanup strategy configured.

6. **Q:** What happens if you forget to call `update()` after modifying a state object?
   - **A:** The modification is NOT checkpointed. On failure/restart, you'll lose the change. Always call `update()` to persist changes.

7. **Q:** Can you have keyed state without calling `keyBy()`?
   - **A:** No. Keyed state requires a keyed stream (created by `keyBy()`). Non-keyed operators can only use operator state.

8. **Q:** Which state backend should you use for 10TB of state?
   - **A:** RocksDB (EmbeddedRocksDBStateBackend). Heap backend cannot handle that much state.

9. **Q:** How do you clear state for ALL keys at once?
   - **A:** You can't from within the operator. State is per-key. To clear all state, restart the job without a savepoint (fresh start).

10. **Q:** What's the performance difference between ValueState and MapState for single-value lookups?
    - **A:** ValueState is faster. MapState has overhead for the map structure. Use ValueState when you only need one value per key.

## Related Patterns and Next Steps

- **Pattern 01: Hybrid Source** - Use keyed state to maintain product catalog loaded from files
- **Pattern 03: Timers** - Combine with timers to detect stale state and trigger cleanup
- **Pattern 04: Side Outputs** - Route state-based decisions to different streams
- **Broadcast State** - Combine keyed state with broadcast state for rules-based processing
- **Queryable State** - Expose keyed state for external queries

## Further Reading

- [Flink State Documentation](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/state/)
- [State Backends](https://nightlies.apache.org/flink/flink-docs-stable/docs/ops/state/state_backends/)
- [State TTL](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/state/#state-time-to-live-ttl)
- [Checkpointing](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/checkpointing/)

## Next Steps

Once you're comfortable with Keyed State, move to:
- **Pattern 03: Timers** - Learn how to trigger actions based on time progression
- **Pattern 04: Side Outputs** - Route events to multiple streams efficiently
- **Advanced: State Schema Evolution** - Handle state migrations during upgrades

---

**Workshop Note:** Keyed state is the foundation of stateful stream processing. Companies like Uber, Netflix, Alibaba, and LinkedIn use keyed state to track billions of entities (users, orders, devices) in real-time. Mastering keyed state patterns is essential for building production-grade streaming applications that can scale to petabytes of state while maintaining millisecond latencies.

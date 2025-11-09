# Flink Pattern: Timers (Processing Time & Event Time)

## 🎯 Learning Objectives

After completing this module, you will understand:
1. The difference between Processing Time and Event Time timers
2. How to register and manage timers in KeyedProcessFunction
3. When to use timers vs windows vs state TTL
4. How to implement timeout detection and periodic tasks
5. Best practices for timer cleanup and memory management

## 📖 Pattern Overview

### What are Timers?

**Timers** allow you to trigger actions based on time progression. They are essential for:

- **Timeout Detection** - Alert when no data received for 1 hour
- **Session Management** - Detect user inactivity (30 min timeout)
- **Periodic Reporting** - Emit inventory snapshot every 5 minutes
- **State Cleanup** - Remove inactive keys after TTL expires

### Timer Types

| Timer Type | Based On | Use Case | Deterministic | Replayable |
|------------|----------|----------|---------------|------------|
| **Processing Time** | Wall-clock time | Timeouts, cleanup | ❌ No | ❌ No |
| **Event Time** | Event timestamps | Event-driven logic | ✅ Yes | ✅ Yes |

### Visual Timer Lifecycle

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  Product Update Arrives                                      │
│         │                                                    │
│         ▼                                                    │
│  ┌───────────────────────────────────────────────┐           │
│  │  processElement(product, ctx, out)            │           │
│  │                                               │           │
│  │  1. Delete existing timer (if any)            │           │
│  │     ctx.timerService()                        │           │
│  │        .deleteProcessingTimeTimer(old)        │           │
│  │                                               │           │
│  │  2. Register new timer                        │           │
│  │     long fireTime = now + 1_HOUR              │           │
│  │     ctx.timerService()                        │           │
│  │        .registerProcessingTimeTimer(fireTime) │           │
│  │                                               │           │
│  │  3. Save timer timestamp in state             │           │
│  │     timerState.update(fireTime)               │           │
│  └───────────────────────────────────────────────┘           │
│         │                                                    │
│         ├──────────────────┬─────────────────────┐           │
│         │                  │                     │           │
│         ▼                  ▼                     ▼           │
│  New update arrives   Timer fires         (Nothing)          │
│  → Reset timer        → Call onTimer()    → Timer fires      │
│                                                              │
│  ┌───────────────────────────────────────────────┐           │
│  │  onTimer(timestamp, ctx, out)                 │           │
│  │                                               │           │
│  │  1. Timer fired = NO update received          │           │
│  │                                               │           │
│  │  2. Retrieve state                            │           │
│  │     Product lastProduct = state.value()       │           │
│  │                                               │           │
│  │  3. Emit alert                                │           │
│  │     out.collect(new StaleAlert(...))          │           │
│  │                                               │           │
│  │  4. Cleanup (timer auto-removed)              │           │
│  │     timerState.clear()                        │           │
│  └───────────────────────────────────────────────┘           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

## 🔑 Key Concepts

### Processing Time vs Event Time

**Processing Time:**
```java
// Fire based on machine wall-clock time
long fireTime = ctx.timerService().currentProcessingTime() + 3600000; // +1 hour
ctx.timerService().registerProcessingTimeTimer(fireTime);
```

**Event Time:**
```java
// Fire based on watermark progression
long fireTime = ctx.timestamp() + 3600000; // +1 hour from event time
ctx.timerService().registerEventTimeTimer(fireTime);
```

**When to Use:**
- **Processing Time** → Real-time dashboards, timeouts, cleanup
- **Event Time** → Historical replay, accurate windows, late data handling

### Timer Registration

```java
@Override
public void processElement(Product product, Context ctx, Collector<Alert> out) {
    // Get current time
    long now = ctx.timerService().currentProcessingTime();

    // Register timer to fire in 1 hour
    long timerTime = now + 3600000;
    ctx.timerService().registerProcessingTimeTimer(timerTime);

    // IMPORTANT: Save timer timestamp for cleanup
    timerState.update(timerTime);
}
```

### Timer Callback

```java
@Override
public void onTimer(long timestamp, OnTimerContext ctx, Collector<Alert> out) {
    // timestamp = the REGISTERED time, not current time
    String key = ctx.getCurrentKey(); // Which product triggered this?

    // Retrieve state
    Product lastProduct = productState.value();

    // Emit timeout event
    out.collect(new TimeoutAlert(key, lastProduct));

    // Cleanup
    timerState.clear();
}
```

### Timer Cleanup

```java
// Delete timer before it fires
Long existingTimer = timerState.value();
if (existingTimer != null) {
    ctx.timerService().deleteProcessingTimeTimer(existingTimer);
    timerState.clear(); // Clean up state
}
```

## 💻 Code Example with Annotations

```java
/**
 * Detect products with no updates for 1 hour
 */
public static class StaleInventoryDetector
        extends KeyedProcessFunction<String, Product, Alert> {

    // [1] State: Track when we registered the timer
    private transient ValueState<Long> timerState;

    // [2] State: Store product data for alert details
    private transient ValueState<Product> productState;

    @Override
    public void open(Configuration parameters) {
        // [3] Initialize state descriptors
        timerState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("timer", Long.class)
        );
        productState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("product", Product.class)
        );
    }

    @Override
    public void processElement(Product product, Context ctx, Collector<Alert> out) {
        // [4] Cancel existing timer (reset on each update)
        Long oldTimer = timerState.value();
        if (oldTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(oldTimer);
        }

        // [5] Register new timer: fire if no update for 1 hour
        long fireTime = ctx.timerService().currentProcessingTime() + 3600000;
        ctx.timerService().registerProcessingTimeTimer(fireTime);

        // [6] Save timer and product in state
        timerState.update(fireTime);
        productState.update(product);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Alert> out) {
        // [7] Timer fired = no update received in 1 hour!
        Product product = productState.value();

        // [8] Emit alert
        out.collect(new Alert(ctx.getCurrentKey(), "STALE", product));

        // [9] Cleanup (timer already auto-removed)
        timerState.clear();
    }
}
```

## 🎓 Hands-On Exercises

### Exercise 1: Detect Products Not Updated in 2 Hours

**Task:** Modify `TimerExample` to alert when products have no updates for 2 hours instead of 1 hour.

**Steps:**
1. Change `STALE_THRESHOLD_MS` constant
2. Observe timer firing time in logs
3. Test with different product update frequencies

**Expected Output:**
```
⏰ Timer registered for product: LAPTOP_001 - Will fire in 120 min
🚨 STALE INVENTORY ALERT: MOUSE_001 - No update for 120 minutes
```

<details>
<summary>Solution</summary>

```java
// Change threshold constant
private static final long STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000; // 2 hours

// Everything else stays the same - timers automatically adjust!
```
</details>

### Exercise 2: Periodic Inventory Snapshots

**Challenge:** Emit inventory snapshot every 5 minutes for each product.

**Requirements:**
1. Use processing time timers
2. After timer fires, emit snapshot AND register NEXT timer
3. Continue indefinitely (periodic behavior)

**Hints:**
```java
@Override
public void onTimer(long timestamp, OnTimerContext ctx, Collector<Snapshot> out) {
    // 1. Emit snapshot
    out.collect(createSnapshot());

    // 2. Register NEXT timer (5 minutes from now)
    long nextTimer = ctx.timerService().currentProcessingTime() + 300000;
    ctx.timerService().registerProcessingTimeTimer(nextTimer);
    timerState.update(nextTimer);
}
```

**Expected Behavior:**
```
📸 Snapshot for LAPTOP_001: inventory=10 (t=0)
📸 Snapshot for LAPTOP_001: inventory=8 (t=5min)
📸 Snapshot for LAPTOP_001: inventory=5 (t=10min)
```

<details>
<summary>Solution</summary>

```java
public static class PeriodicSnapshotFunction
        extends KeyedProcessFunction<String, Product, InventorySnapshot> {

    private static final long SNAPSHOT_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private transient ValueState<Long> timerState;
    private transient ValueState<Product> productState;

    @Override
    public void processElement(Product product, Context ctx, Collector<InventorySnapshot> out) {
        // Update product state
        productState.update(product);

        // Register timer ONLY if not already registered
        if (timerState.value() == null) {
            long firstSnapshot = ctx.timerService().currentProcessingTime() + SNAPSHOT_INTERVAL_MS;
            ctx.timerService().registerProcessingTimeTimer(firstSnapshot);
            timerState.update(firstSnapshot);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<InventorySnapshot> out) {
        Product product = productState.value();

        // Emit snapshot
        InventorySnapshot snapshot = new InventorySnapshot();
        snapshot.productId = product.productId;
        snapshot.inventory = product.inventory;
        snapshot.snapshotTime = timestamp;
        out.collect(snapshot);

        // Register NEXT timer (periodic)
        long nextTimer = ctx.timerService().currentProcessingTime() + SNAPSHOT_INTERVAL_MS;
        ctx.timerService().registerProcessingTimeTimer(nextTimer);
        timerState.update(nextTimer);
    }
}
```
</details>

### Exercise 3: Session Timeout (No Activity for 30 Minutes)

**Advanced Challenge:** Implement user session timeout detection.

**Scenario:**
- User adds products to cart (represented as Product events with userId)
- If no activity for 30 minutes, emit "SESSION_TIMEOUT" event
- If user returns, reset timer

**Requirements:**
1. Key by `userId` instead of `productId`
2. Track session start time
3. Calculate session duration on timeout
4. Emit session summary (total items, duration)

**Hints:**
- Use `ListState<Product>` to track all products in session
- Calculate session duration: `timeoutTime - firstActivityTime`
- Clear all state on timeout

**Expected Output:**
```
🕐 Session started for user: U123
🔄 Session activity for user: U123 (item: LAPTOP_001)
🔄 Session activity for user: U123 (item: MOUSE_001)
⏱️ SESSION TIMEOUT for user: U123 - Duration: 35 min, Items: 2
```

<details>
<summary>Solution</summary>

```java
public static class SessionTimeoutDetector
        extends KeyedProcessFunction<String, UserActivity, SessionTimeout> {

    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    private transient ValueState<Long> timerState;
    private transient ValueState<Long> sessionStartState;
    private transient ListState<Product> sessionItemsState;

    @Override
    public void open(Configuration parameters) {
        timerState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("timer", Long.class)
        );
        sessionStartState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("session-start", Long.class)
        );
        sessionItemsState = getRuntimeContext().getListState(
            new ListStateDescriptor<>("session-items", Product.class)
        );
    }

    @Override
    public void processElement(UserActivity activity, Context ctx, Collector<SessionTimeout> out) {
        // Initialize session start time
        if (sessionStartState.value() == null) {
            sessionStartState.update(ctx.timerService().currentProcessingTime());
        }

        // Add item to session
        sessionItemsState.add(activity.product);

        // Cancel existing timer
        Long oldTimer = timerState.value();
        if (oldTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(oldTimer);
        }

        // Register new timeout timer
        long fireTime = ctx.timerService().currentProcessingTime() + SESSION_TIMEOUT_MS;
        ctx.timerService().registerProcessingTimeTimer(fireTime);
        timerState.update(fireTime);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<SessionTimeout> out) {
        Long sessionStart = sessionStartState.value();
        List<Product> items = new ArrayList<>();
        sessionItemsState.get().forEach(items::add);

        // Emit session timeout
        SessionTimeout timeout = new SessionTimeout();
        timeout.userId = ctx.getCurrentKey();
        timeout.sessionDurationMs = timestamp - sessionStart;
        timeout.itemCount = items.size();
        timeout.items = items;
        out.collect(timeout);

        // Clear all session state
        sessionStartState.clear();
        sessionItemsState.clear();
        timerState.clear();
    }
}
```
</details>

## 🔍 Common Pitfalls

### ❌ **Pitfall 1: Registering Multiple Timers for Same Key**

**Problem:**
```java
// BAD: Registers new timer without deleting old one
public void processElement(Product product, Context ctx, Collector<Alert> out) {
    long fireTime = ctx.timerService().currentProcessingTime() + 3600000;
    ctx.timerService().registerProcessingTimeTimer(fireTime);
    // ❌ Old timer still registered! Will fire twice!
}
```

**Solution:**
```java
// GOOD: Always delete old timer first
public void processElement(Product product, Context ctx, Collector<Alert> out) {
    Long oldTimer = timerState.value();
    if (oldTimer != null) {
        ctx.timerService().deleteProcessingTimeTimer(oldTimer); // ✅
    }

    long fireTime = ctx.timerService().currentProcessingTime() + 3600000;
    ctx.timerService().registerProcessingTimeTimer(fireTime);
    timerState.update(fireTime);
}
```

### ❌ **Pitfall 2: Forgetting to Save Timer Timestamp**

**Problem:**
```java
// BAD: No way to delete timer later
ctx.timerService().registerProcessingTimeTimer(fireTime);
// ❌ Timer timestamp not saved!
```

**Solution:**
```java
// GOOD: Save timer timestamp in state
long fireTime = ctx.timerService().currentProcessingTime() + 3600000;
ctx.timerService().registerProcessingTimeTimer(fireTime);
timerState.update(fireTime); // ✅ Save for cleanup
```

### ❌ **Pitfall 3: Processing vs Event Time Confusion**

**Problem:**
```java
// BAD: Mixing processing time and event time
long fireTime = ctx.timerService().currentProcessingTime() + 3600000;
ctx.timerService().registerEventTimeTimer(fireTime); // ❌ Wrong timer type!
```

**Solution:**
```java
// GOOD: Match timer type with time semantics
// Processing Time Timer
long fireTime = ctx.timerService().currentProcessingTime() + 3600000;
ctx.timerService().registerProcessingTimeTimer(fireTime); // ✅

// Event Time Timer
long fireTime = ctx.timestamp() + 3600000; // Use event timestamp
ctx.timerService().registerEventTimeTimer(fireTime); // ✅
```

### ❌ **Pitfall 4: Memory Leak from Unbounded Timers**

**Problem:**
```java
// BAD: Timers never cleaned up for inactive keys
// If 1M products exist but only 100 are active, 900K timers waste memory
```

**Solution:**
```java
// GOOD: Implement state TTL or cleanup logic
public void onTimer(long timestamp, OnTimerContext ctx, Collector<Alert> out) {
    // Emit alert
    out.collect(alert);

    // IMPORTANT: Clear ALL state for this key
    timerState.clear();
    productState.clear();
    // State backend will eventually remove key
}
```

## 📊 Performance Tips

### 1. **Timer Granularity**
```java
// BAD: Timer per second = 86,400 timers/day per key
registerProcessingTimeTimer(now + 1000);

// GOOD: Timer per hour = 24 timers/day per key
registerProcessingTimeTimer(now + 3600000);
```

### 2. **Batch Timer Registration**
```java
// BAD: Register timer for every event
public void processElement(Product product, Context ctx, Collector<Alert> out) {
    ctx.timerService().registerProcessingTimeTimer(now + 3600000);
    // Creates many timers if events arrive rapidly
}

// GOOD: Only register if no timer exists
public void processElement(Product product, Context ctx, Collector<Alert> out) {
    if (timerState.value() == null) { // ✅ Check first
        ctx.timerService().registerProcessingTimeTimer(now + 3600000);
        timerState.update(now + 3600000);
    }
}
```

### 3. **State TTL vs Timers**

For simple cleanup, use State TTL instead:
```java
// Simpler alternative for automatic cleanup
StateTtlConfig ttlConfig = StateTtlConfig
    .newBuilder(Time.hours(1))
    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
    .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
    .build();

ValueStateDescriptor<Product> descriptor = new ValueStateDescriptor<>("product", Product.class);
descriptor.enableTimeToLive(ttlConfig);
```

### 4. **Processing Time for Most Use Cases**

Processing time timers are ~10x faster than event time:
```java
// Processing Time: O(log n) - RocksDB lookup
ctx.timerService().registerProcessingTimeTimer(fireTime);

// Event Time: O(log n) + watermark propagation overhead
ctx.timerService().registerEventTimeTimer(fireTime);
```

## 🔗 Related Patterns

- **Pattern 02: Keyed State** - Timers require keyed state to track timer timestamps
- **Window Functions** - Use for fixed-size windows; timers for custom logic
- **State TTL** - Automatic cleanup without timer overhead
- **Side Outputs** - Tag timeout events separately from normal events

## 📚 Further Reading

- [Flink Process Function Documentation](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/process_function/)
- [Timer Service API](https://nightlies.apache.org/flink/flink-docs-stable/api/java/org/apache/flink/streaming/api/TimerService.html)
- [State TTL](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/fault-tolerance/state/#state-time-to-live-ttl)

## ✅ Quiz

Test your understanding:

1. **Q:** What happens if you register multiple timers with the same timestamp?
   - **A:** Flink deduplicates timers. Registering same timestamp twice = one timer fires once.

2. **Q:** Can you register a timer in `onTimer()` callback?
   - **A:** Yes! This is how you implement periodic timers (register next timer when current fires).

3. **Q:** What's the difference between deleting a timer and clearing timer state?
   - **A:** Deleting timer cancels the callback. Clearing state removes the timestamp. Do both!

4. **Q:** When should you use Processing Time vs Event Time timers?
   - **A:** Processing Time for real-time features (dashboards, timeouts). Event Time for accurate historical replay.

5. **Q:** How many timers can Flink handle?
   - **A:** Millions, but each timer has memory cost. Use coarser granularity (hours vs seconds) for scale.

6. **Q:** What happens to timers during job restart from savepoint?
   - **A:** Processing time timers are NOT restored (wall-clock dependent). Event time timers ARE restored.

## 🎯 Next Steps

Once you're comfortable with Timers, move to:
- **Pattern 04: Side Outputs** - Route timeout events to separate streams
- **Pattern 05: Broadcast State** - Share configuration across all tasks
- **Pattern 06: Async I/O** - Enrich events with external lookups

---

**Workshop Note:** Timers are used in production to detect SLA violations, implement custom session windows, and manage billions of stateful entities with automatic cleanup. Companies like Uber use timers to detect ride timeout, fraud detection systems use them to flag suspicious inactivity patterns.

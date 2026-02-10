# Kafka Streams Implementation

## Overview

This directory contains the Kafka Streams implementation for the KartShoppe application. The Kafka Streams component serves as a real-time data processing and caching layer that bridges the gap between Flink-produced events and the frontend application.

## Architecture

The Kafka Streams implementation consists of four main components:

### 1. KafkaStreamsTopology (`KafkaStreamsTopology.java`)

The central component that defines the stream processing topology. It creates and configures the Kafka Streams pipeline.

**Key Responsibilities:**
- **Product Cache Stream**: Consumes inventory events from the `inventory-events` topic and maintains a materialized view of product inventory and pricing
- **Chat Messages Stream**: Processes chat messages and stores them in an in-memory state store
- **Data Points Stream**: Handles random number points for visualization

**Topology Structure:**

```
inventory-events (Kafka Topic)
    ↓
Parse & Filter (mapValues)
    ↓
Extract Product Updates (filter)
    ↓
Re-key by productId (selectKey)
    ↓
Materialize as KTable (products-cache)
    ↓
Forward to WebSocket (foreach)
```

**State Stores:**
- `products-cache`: A materialized KTable that stores the latest state of each product
- `chat-store`: In-memory store for recent chat messages (configurable size)
- `data-point-store`: In-memory store for data visualization points (configurable size)

**Event Processing Logic:**
- Filters out `PRODUCT_ADDED` events to prevent race conditions
- Processes partial inventory updates (inventory count and price changes)
- Maintains product state in a queryable KTable
- Forwards updates to WebSocket clients in real-time

### 2. ProductCacheService (`ProductCacheService.java`)

A service that manages the product cache and synchronizes product data between Kafka Streams state stores and the application's in-memory cache.

**Key Features:**

**Dual-Source Product Management:**
- Integrates with Kafka Streams state store for persistent product data
- Maintains a `ConcurrentHashMap` for fast, local access
- Handles both full product objects (from Kafka Consumer) and partial updates (from inventory events)

**Intelligent Update Merging:**
- Full products (with name/description): Replace entire cache entry
- Partial updates (inventory/price only): Merge into existing product object
- Prevents creation of incomplete "skeleton" products

**Real-time Synchronization:**
- Scheduled sync every 30 seconds to push cache state to all connected WebSocket clients
- Immediate WebSocket updates when products change
- Logs helpful warnings when cache is empty (waiting for Flink job)

**Query Interface:**
- `getProduct(productId)`: Retrieve a single product by ID
- `getAllProducts()`: Get all products in the cache
- `getProductsByCategory(category)`: Filter products by category
- `searchProducts(query)`: Search products by name, description, or tags

### 3. ProcessingFanoutProcessor (`ProcessingFanoutProcessor.java`)

A custom Kafka Streams processor that handles message routing and state management for chat messages and data points.

**Processing Logic:**
- Deserializes incoming events and determines event type
- For `CHAT_MESSAGE` events:
  - Stores messages in the chat state store
  - Implements a bounded cache (removes oldest when size exceeded)
  - Only stores actual chat messages (filters out system messages)
- For `RANDOM_NUMBER_POINT` events:
  - Stores data points for visualization
  - Maintains a sliding window of recent points
- Forwards all processed events to WebSocket clients

**State Management:**
- Uses Kafka Streams KeyValueStore for persistence
- Implements thread-safe synchronized lists
- Automatically trims lists to configured maximum size

### 4. CacheQueryService (`CacheQueryService.java`)

Provides a query interface to access data stored in Kafka Streams state stores.

**Query Methods:**
- `getLastChatMessages()`: Retrieves recent chat messages from the chat state store
- `getLastDataPoints()`: Retrieves recent data points from the data point store

**Design Pattern:**
- Uses Kafka Streams Interactive Queries feature
- Provides read-only access to state stores
- Returns empty collections when no data is available (graceful degradation)

## Data Flow

### Product Update Flow

1. **Flink Inventory Job** → Produces inventory events to `inventory-events` topic
2. **KafkaStreamsTopology** → Consumes and processes inventory events
   - Filters and transforms events
   - Updates products-cache KTable
3. **ProductCacheService** → Receives updates from stream
   - Merges partial updates with existing products
   - Updates in-memory cache
   - Pushes to WebSocket clients
4. **Frontend** → Receives real-time product updates via WebSocket

### Chat Message Flow

1. **Flink/API** → Produces chat events to configured topics
2. **ProcessingFanoutProcessor** → Processes chat events
   - Validates message type
   - Stores in chat-store
   - Maintains bounded cache
3. **CacheQueryService** → Provides query access to chat history
4. **WebSocket** → Broadcasts messages to connected clients

## Configuration

The Kafka Streams application is configured through Quarkus properties:

```properties
quarkus.kafka-streams.topics=<comma-separated-topic-list>
quarkus.kafka-streams.caching.chat=<max-chat-messages>
quarkus.kafka-streams.caching.data-points=<max-data-points>
```

## Key Design Patterns

### 1. CQRS (Command Query Responsibility Segregation)
The KTable materialization serves as a read model optimized for queries, while Flink jobs handle the command/write side.

### 2. Event Sourcing
Product state is derived from a stream of inventory events rather than direct database queries.

### 3. Materialized Views
The `products-cache` KTable provides a queryable, up-to-date view of product inventory without hitting the database.

### 4. Stateful Stream Processing
Uses Kafka Streams state stores to maintain application state across restarts.

### 5. Real-time Cache Invalidation
Product cache updates are pushed to clients immediately via WebSocket, ensuring eventual consistency.

## Race Condition Prevention

The implementation specifically addresses a race condition where inventory events might arrive before full product details:

- **Problem**: Inventory events from Flink arrive before the ProductConsumer receives full product objects
- **Solution**: 
  - Filter out `PRODUCT_ADDED` events in the stream (line 90-92)
  - Partial updates only merge into existing products (line 112-129)
  - Log warnings when receiving updates for unknown products (line 128)
  - Wait for full product from `products` topic before showing in UI

## Integration Points

### Kafka Topics
- **Input Topics**:
  - `inventory-events`: Inventory and price change events from Flink
  - Configured topics for chat and data points
  
- **Output Topics**:
  - None (this is a consumer-only application)

### WebSocket Integration
Updates are pushed to the frontend via `WebsocketEmitter` for:
- Product inventory changes
- Price updates
- Chat messages
- Data visualization points

### State Store Integration
- State stores are queryable via `CacheQueryService`
- REST endpoints can access cached data without Kafka lag
- Provides consistent read-your-writes semantics

## Operational Characteristics

### Startup Behavior
1. Initializes state stores (chat, data-point, products)
2. Waits for Kafka Streams to reach RUNNING state
3. Loads existing products from state store into memory cache
4. Logs helpful messages if cache is empty (waiting for Flink job)

### Performance Characteristics
- In-memory state stores for low-latency queries
- Concurrent HashMap for thread-safe cache access
- Bounded caches prevent memory growth
- Scheduled sync (30s) balances freshness with WebSocket traffic

### Error Handling
- Graceful handling of malformed JSON events
- Returns empty collections when data unavailable
- Logs warnings for unexpected event types
- Null-safe product merging logic

## Usage Example

The Kafka Streams application runs automatically as part of the Quarkus application. To populate it with data:

1. Start the platform: `./start-platform-local.sh`
2. Run the Flink inventory job: `./flink-inventory-with-orders-job.sh`
3. Watch products populate in real-time through WebSocket updates
4. Query product cache via REST API or WebSocket

## Best Practices Demonstrated

1. **Separation of Concerns**: Each class has a single, well-defined responsibility
2. **Dependency Injection**: Uses CDI for loose coupling
3. **Immutable State**: State modifications are controlled and tracked
4. **Logging**: Comprehensive logging for debugging and monitoring
5. **Configuration**: Externalized configuration via Quarkus properties
6. **Null Safety**: Defensive programming with null checks
7. **Resource Management**: Proper cleanup in processor close() method

## Future Enhancements

Potential improvements to consider:

- Add metrics and monitoring (latency, throughput, cache hit rates)
- Implement windowed aggregations for analytics
- Add support for product recommendations using stream joins
- Implement changelog topics for state store backup/recovery
- Add health checks for Kafka Streams state

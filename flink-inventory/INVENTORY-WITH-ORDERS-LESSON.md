# Inventory Management with Order Deduction - Complete E-Commerce Flow

## Overview

This enhanced inventory job demonstrates a **complete real-time e-commerce inventory system** with automatic depletion when customers place orders.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    COMPLETE INVENTORY FLOW                          │
└─────────────────────────────────────────────────────────────────────┘

1. USER ACTION:
   User → KartShoppe UI → Checkout

2. QUARKUS API:
   Order → PostgreSQL (persistence)
   Order Items → Kafka (order-events topic)

3. FLINK INVENTORY JOB:
   ┌──────────────────────────────────────────────┐
   │ SOURCE 1: Product Updates                    │
   │  • File: initial-products.json (bootstrap)   │
   │  • Kafka: product-updates (real-time)        │
   └──────────────────┬───────────────────────────┘
                      │
                      ▼
   ┌──────────────────────────────────────────────┐
   │        SHARED KEYED STATE                    │
   │  Key: productId                              │
   │  Value: Product (with inventory)             │
   └──────────────────┬───────────────────────────┘
                      ▲
                      │
   ┌──────────────────┴───────────────────────────┐
   │ SOURCE 2: Order Events (NEW!)                │
   │  • Kafka: order-events                       │
   │  • Deducts inventory when orders placed      │
   └──────────────────────────────────────────────┘

4. OUTPUT:
   Inventory Events → Kafka → WebSocket → UI
   User sees inventory decrease in real-time!
```

## Patterns Demonstrated

### Pattern 01: Hybrid Source
- **Bootstrap**: Load 200 products from JSON file
- **Stream**: Continuous updates from Kafka

### Pattern 02: Keyed State
- **Shared State**: Both product updates AND orders update the same state
- **Key**: `productId`
- **State**: Complete `Product` object with current inventory

### Pattern 03: Timers
- **Stale Detection**: Products with no updates for 1 hour
- **Processing Time Timers**: Per-product timeout tracking

### Pattern 04: Side Outputs
- **Low Stock Alerts**: Inventory < 10
- **Out of Stock Alerts**: Inventory = 0
- **Price Drop Alerts**: Price decreases > 10%

### Pattern 05: Multiple Sources (NEW!)
- **Product Source**: Hybrid (file + Kafka)
- **Order Source**: Kafka (order-events)
- **Convergence**: Both update same keyed state

### Pattern 06: Real-time Inventory Deduction (NEW!)
- **Order Processing**: Parse order items from Kafka
- **Inventory Deduction**: Subtract quantity from state
- **Out-of-Stock Handling**: Prevent negative inventory
- **Event Emission**: Notify downstream systems

## Code Structure

```
flink-inventory/
├── InventoryManagementJobRefactored.java
│   └── Base job (Patterns 01-04)
│
├── InventoryManagementJobWithOrders.java  ← NEW!
│   └── Enhanced job (Patterns 01-06)
│
└── shared/
    ├── model/
    │   ├── InventoryEvent.java
    │   ├── AlertEvent.java
    │   └── OrderItemDeduction.java  ← NEW!
    │
    └── processor/
        ├── InventoryStateFunction.java (product updates)
        ├── OrderItemParser.java  ← NEW!
        └── InventoryDeductionFunction.java  ← NEW!
```

## Running the Lesson

### Step 1: Start Infrastructure
```bash
docker compose up -d
```

### Step 2: Setup Kafka Topics
```bash
./0-setup-topics.sh
```

This creates:
- `products` - Product updates
- `order-events` - Order items for deduction
- `inventory-events` - Inventory changes
- `websocket_fanout` - Real-time UI updates

### Step 3: Run Enhanced Inventory Job
```bash
./flink-1b-inventory-with-orders-job.sh
```

### Step 4: Load Initial Products
```bash
# The job automatically loads from data/initial-products.json
# Wait a few seconds for 200 products to appear in the UI
```

### Step 5: Place an Order!
1. Open KartShoppe UI: http://localhost:8081
2. Add products to cart
3. Checkout
4. **Watch the inventory decrease in real-time!**

## Testing the Flow

### Test 1: Single Order
```
1. Note initial inventory of a product (e.g., PROD_0001: 17 units)
2. Add 2 units to cart
3. Checkout
4. Observe: Inventory → 15 units (real-time update in UI!)
```

### Test 2: Deplete to Zero
```
1. Find a product with low inventory (< 10)
2. Order all remaining units
3. Observe: Out-of-stock badge appears immediately
4. Try to order again → Should show "Out of Stock"
```

### Test 3: Monitor Logs
```bash
# Watch Flink job logs
tail -f logs/inventory.log

# You'll see:
📦 Parsed order item: PROD_0001 x2 (order: order_123)
📉 Inventory deduction: PROD_0001 (17 → 15) for order order_123
```

### Test 4: Check Kafka Topics
```bash
# See inventory events
docker exec redpanda rpk topic consume inventory-events --brokers localhost:19092

# See order events
docker exec redpanda rpk topic consume order-events --brokers localhost:19092
```

## Learning Outcomes

After completing this lesson, you will understand:

1. **Multiple Sources Pattern**
   - How to combine different event sources in Flink
   - Managing shared state across sources
   - Coordinating updates from different streams

2. **Event-Driven Inventory**
   - Real-time inventory depletion
   - Order-driven state updates
   - Consistency in distributed systems

3. **Complete E-Commerce Flow**
   - User action → Database → Kafka → Flink → UI
   - End-to-end event processing
   - Real-time data synchronization

4. **State Management Best Practices**
   - Keyed state for per-entity tracking
   - State sharing across functions
   - Handling concurrent updates

5. **Production Patterns**
   - Error handling (insufficient inventory)
   - Alert generation (out-of-stock)
   - Observability (logging, metrics)

## Comparison with Base Job

| Feature | Base Job | Enhanced Job (With Orders) |
|---------|----------|---------------------------|
| Product Updates | ✓ | ✓ |
| Inventory Tracking | ✓ | ✓ |
| Timers | ✓ | ✓ |
| Side Outputs | ✓ | ✓ |
| **Order Processing** | ✗ | **✓ NEW!** |
| **Inventory Deduction** | ✗ | **✓ NEW!** |
| **Multiple Sources** | ✗ | **✓ NEW!** |
| **Complete E-Commerce** | ✗ | **✓ NEW!** |

## Next Steps

1. **Experiment**: Try ordering more than available inventory
2. **Monitor**: Watch WebSocket messages in browser dev tools
3. **Extend**: Add inventory restocking logic
4. **Scale**: Increase parallelism for production workloads

## Troubleshooting

### No inventory deduction?
- Check `order-events` topic exists: `./0-setup-topics.sh`
- Verify Quarkus is publishing: Check logs/quarkus.log
- Confirm Flink job is running: Check console output

### Orders not appearing?
- Check PostgreSQL: `docker exec postgres-cdc psql -U postgres -d ecommerce -c "SELECT * FROM orders;"`
- Verify Quarkus API: `curl http://localhost:8081/api/ecommerce/inventory/state`

### Inventory not updating in UI?
- Check WebSocket connection in browser console
- Verify `websocket_fanout` topic has messages
- Refresh the products page

## Related Files

- `./flink-1-inventory-job.sh` - Base inventory job (no orders)
- `./flink-1b-inventory-with-orders-job.sh` - Enhanced job (with orders) ← **USE THIS**
- `./flink-2-order-cdc-job.sh` - PostgreSQL CDC job (optional)
- `data/initial-products.json` - Initial product catalog

#!/bin/bash
# Start the ENHANCED Inventory Management Flink Job WITH ORDER DEDUCTION
#
# This version includes:
# - Product inventory tracking (from file + Kafka)
# - Order-based inventory deduction (from order-events topic)
# - Real-time inventory updates when orders are placed

echo "🚀 Starting ENHANCED Inventory Management Job (WITH ORDER DEDUCTION)..."

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set environment variables with absolute paths
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092}
export INITIAL_PRODUCTS_FILE=${INITIAL_PRODUCTS_FILE:-$SCRIPT_DIR/data/initial-products.json}
export PARALLELISM=${PARALLELISM:-1}

echo "📂 Configuration:"
echo "   Products file: $INITIAL_PRODUCTS_FILE"
echo "   Kafka: $KAFKA_BOOTSTRAP_SERVERS"
echo "   Parallelism: $PARALLELISM"
echo ""
echo "🆕 This job includes ORDER DEDUCTION:"
echo "   - Listens to order-events topic"
echo "   - Deducts inventory when orders are placed"
echo "   - Shows complete e-commerce inventory flow"
echo ""

# Check if order-events topic exists
echo "🔍 Checking for order-events topic..."
if docker exec redpanda rpk topic list --brokers $KAFKA_BOOTSTRAP_SERVERS | grep -q "order-events"; then
    echo "✅ order-events topic exists"
else
    echo "⚠️  order-events topic not found. Creating it..."
    docker exec redpanda rpk topic create order-events --brokers $KAFKA_BOOTSTRAP_SERVERS --partitions 3
fi

# Build the job if needed
echo ""
echo "📦 Building Enhanced Inventory Flink Job..."
./gradlew :flink-inventory:shadowJar

# Run the job
echo ""
echo "▶️  Running ENHANCED Inventory Management Job..."
echo ""
java --add-opens java.base/java.util=ALL-UNNAMED \
  -cp flink-inventory/build/libs/flink-inventory.jar \
  com.ververica.composable_job.flink.inventory.InventoryManagementJobWithOrders

echo ""
echo "✅ Enhanced Inventory Management Job completed"

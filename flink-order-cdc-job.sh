#!/bin/bash
# Start Flink CDC Job for PostgreSQL Orders
# This job reads order changes from PostgreSQL via CDC and publishes to Kafka

set -e

echo "🚀 Starting Flink Order CDC Job..."

# Configuration
export POSTGRES_HOST=${POSTGRES_HOST:-localhost}
export POSTGRES_PORT=${POSTGRES_PORT:-5432}
export POSTGRES_DB=${POSTGRES_DB:-ecommerce}
export POSTGRES_USER=${POSTGRES_USER:-postgres}
export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-postgres}
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092}
export PARALLELISM=${PARALLELISM:-1}

# Build the job
echo "📦 Building Flink Order CDC job..."
./gradlew :flink-order-cdc:shadowJar

# Check if PostgreSQL is ready
echo "🔍 Checking PostgreSQL connection..."
if ! docker exec postgres-cdc pg_isready -U postgres -d ecommerce > /dev/null 2>&1; then
    echo "❌ PostgreSQL is not ready. Make sure Docker Compose is running:"
    echo "   docker compose up -d postgres"
    exit 1
fi

echo "✅ PostgreSQL is ready"

# Run the job
echo "🏃 Running Flink Order CDC job..."
echo ""
echo "Configuration:"
echo "  PostgreSQL: ${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
echo "  Kafka: ${KAFKA_BOOTSTRAP_SERVERS}"
echo "  Parallelism: ${PARALLELISM}"
echo ""

java -cp flink-order-cdc/build/libs/flink-order-cdc-1.0.0-SNAPSHOT-all.jar \
    com.ververica.composable_job.flink.ordercdc.OrderCDCJob

echo ""
echo "✅ Job completed"

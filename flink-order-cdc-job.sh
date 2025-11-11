#!/bin/bash
# Start Flink CDC Job for PostgreSQL Orders
# This job reads order changes from PostgreSQL via CDC and publishes to Kafka

set -e

# Configuration
#   The environment varaibles are for indication only.
#   Actual environment values are configured in the Flink docker compose services
export POSTGRES_HOST=${POSTGRES_HOST:-postgres}
export POSTGRES_PORT=${POSTGRES_PORT:-5432}
export POSTGRES_DB=${POSTGRES_DB:-ecommerce}
export POSTGRES_USER=${POSTGRES_USER:-postgres}
export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-postgres}
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-redpanda:9092}
export PARALLELISM=${PARALLELISM:-1}

echo "🚀 Starting Flink Order CDC Job..."
echo ""
echo "Configuration:"
echo "  PostgreSQL: ${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
echo "  Kafka: ${KAFKA_BOOTSTRAP_SERVERS}"
echo "  Parallelism: ${PARALLELISM}"
echo ""

# Check if PostgreSQL is ready
echo "🔍 Checking PostgreSQL connection..."
if ! docker exec postgres-cdc pg_isready -U postgres -d ecommerce > /dev/null 2>&1; then
    echo "❌ PostgreSQL is not ready. Make sure Docker Compose is running:"
    echo "   docker compose up -d postgres"
    exit 1
fi

echo "✅ PostgreSQL is ready"

# Build the job
echo "📦 Building Flink Order CDC Job..."
./gradlew :flink-order-cdc:shadowJar

##
## Deploy the Flink job to a local standalone Flink cluster
##
# Copy the Jar file
echo ""
echo "📋 Copying Flink Order CDC Job Jar..."
docker cp flink-order-cdc/build/libs/flink-order-cdc-1.0.0-SNAPSHOT-all.jar \
  jobmanager:/tmp/flink-order-cdc-1.0.0-SNAPSHOT-all.jar

# Deploy the Job
echo ""
echo "▶️  Deploying Flink Order CDC Job..."
echo ""
docker exec jobmanager /opt/flink/bin/flink run -d \
  -Denv.java.opts="--add-opens java.base/java.util=ALL-UNNAMED" \
  -c com.ververica.composable_job.flink.ordercdc.OrderCDCJob \
  /tmp/flink-order-cdc-1.0.0-SNAPSHOT-all.jar

echo ""
echo "✅ Flink Order CDC Job deployed"

##
## Alternative way to run the Flink job using a MiniCluster
##
# # Configuration
# export POSTGRES_HOST=${POSTGRES_HOST:-localhost}
# export POSTGRES_PORT=${POSTGRES_PORT:-5432}
# export POSTGRES_DB=${POSTGRES_DB:-ecommerce}
# export POSTGRES_USER=${POSTGRES_USER:-postgres}
# export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-postgres}
# export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:19092}
# export PARALLELISM=${PARALLELISM:-1}

# java -cp flink-order-cdc/build/libs/flink-order-cdc-1.0.0-SNAPSHOT-all.jar \
#     com.ververica.composable_job.flink.ordercdc.OrderCDCJob

# echo ""
# echo "✅ Job completed"

## Incorrect JDK installation.

```bash
sdk install java 11.0.25-tem
sdk install java 17.0.13-tem
```

The default JDK should be _openjdk 17.0.13 2024-10-15_.

```bash
$ java --version
openjdk 17.0.13 2024-10-15
```

Otherwise,

```bash
sdk use java 17.0.13-tem
```

## Connect to Instaclustr Instances

```bash
# Kafka
# eg) nc -vz 54.79.220.204 9092
nc -vz <bootstrap-address> 9092
# Output:
# Connection to 54.79.220.204 9092 port [tcp/*] succeeded!

# PostgreSQL
nc -vz 15.134.112.202 5432
# Output:
# Connection to 15.134.112.202 5432 port [tcp/postgresql] succeeded!
```

## Environment Variables

Make sure correct Kafka and PostgreSQL connection details.

```properties
## Kafka
# Use a single boostrap address in case Flink complains eg) 3.104.148.209:9092
export KAFKA_BOOTSTRAP_SERVERS="[REPLACE_WITH_YOUR_KAFKA_BOOTSTRAP_SERVERS]"
export KAFKA_USER="[REPLACE_WITH_YOUR_KAFKA_USERNAME]" # Default: ickafka
export KAFKA_PASSWORD="[REPLACE_WITH_YOUR_KAFKA_PASSWORD]"

## PostgreSQL
export POSTGRES_HOST="15.134.112.202,13.238.187.138"
export POSTGRES_PORT="5432"
export POSTGRES_DB="[REPLACE_WITH_YOUR_UNIQUE_DATABASE_NAME]"     # <useranme>_db
export POSTGRES_USER="[REPLACE_WITH_YOUR_DATABASE_USERNAME]"      # firstname + first character of last name
export POSTGRES_PASSWORD="[REPLACE_WITH_YOUR_DATABASE_PASSWORD]"  # same to username
# Replication slot name configured in Flink CDC
export POSTGRES_REPLICATION_SLOT="flink_order_cdc_slot"

## File path for Hybrid Source
export INITIAL_PRODUCTS_FILE="/tmp/initial-products.json"

## Flink config
export PARALLELISM=1
```

## Quarkus API application.properties refer to correct connection details

```properties
...
# Connection Details (from environment variables)
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
kafka.user=${KAFKA_USER}
kafka.password=${KAFKA_PASSWORD}
postgres.host=${POSTGRES_HOST}
postgres.port=${POSTGRES_PORT}
postgres.db=${POSTGRES_DB}
postgres.username=${POSTGRES_USER}
postgres.password=${POSTGRES_PASSWORD}
postgres.jdbc.url=jdbc:postgresql://${postgres.host}:${postgres.port}/${postgres.db}
...
```

```bash
## Make sure application.properties are updated correctly
cp quarkus-api/src/main/resources/application.properties.remote \
   quarkus-api/src/main/resources/application.properties
```

## Existing containers prevent from launching docker compose services

```bash
Error response from daemon: Conflict. The container name "/jobmanager" is already in use by container "61f339758f02774d7acdcd3021c4d2b9f8f9653eaea03d126bef121210689517". You have to remove (or rename) that container to be able to reuse that name.
```

Two ways to fix

```bash
# Remove all containers in the stack
docker compose -f compose-remote.yml down

# Remove a specific container
docker compose -f compose-remote.yml down jobmanager
```

## Kafka topics creation + PostgreSQL intialization

```bash
python scripts/manage_topics.py --action create
...
python scripts/manage_topics.py --action list
# ...
# [2026-02-16 16:19:46,352] INFO: Found 14 topics:
# [2026-02-16 16:19:46,352] INFO:  - __consumer_offsets
# [2026-02-16 16:19:46,352] INFO:  - basket-patterns
# [2026-02-16 16:19:46,352] INFO:  - ecommerce_events
# [2026-02-16 16:19:46,352] INFO:  - ecommerce_processing_fanout
# [2026-02-16 16:19:46,352] INFO:  - instaclustr-sla
# [2026-02-16 16:19:46,352] INFO:  - inventory-events
# [2026-02-16 16:19:46,352] INFO:  - inventory_updates
# [2026-02-16 16:19:46,352] INFO:  - order-events
# [2026-02-16 16:19:46,352] INFO:  - processing_fanout
# [2026-02-16 16:19:46,352] INFO:  - product-recommendations
# [2026-02-16 16:19:46,352] INFO:  - product-updates
# [2026-02-16 16:19:46,352] INFO:  - recommendations
# [2026-02-16 16:19:46,352] INFO:  - shopping-cart-events
# [2026-02-16 16:19:46,352] INFO:  - websocket_fanout
# ...
```

We may need to recreate all topics

```bash
python scripts/manage_topics.py --action delete --all
python scripts/manage_topics.py --action create
```

Also, we may need to re-initialize the DB

```bash
python ./scripts/manage_db.py --action teardown
python ./scripts/manage_db.py --action init
```

## Flink Job deployment

The Flink jobs should be deployed in a separate terminal while the training platform is running.

```bash
# Flink inventory with orders
./flink-inventory-with-orders-job.sh

# Flink CDC
./flink-order-cdc-job.sh
```

## Kafka topic monitoring

Key kafka topics are

- **`products`**: The canonical topic with the clean, validated state of all products.
- **`product-updates`**: The input topic for raw, real-time changes to the product catalog.
- **`order-events`**: Carries commands to deduct product quantity from inventory after a sale.
- **`inventory-events`**: A detailed audit log of every state change (e.g., price, inventory) for a product.
- **`inventory-alerts`**: A filtered stream for business-critical notifications like "low stock."

Their relationships are

- `products` makes a catalog of products
  - it gets consumed by the backend's Kafka consumer
- `order-events` and `product-updates` performs partial updates only (i.e. price or inventory count)
  - these records are converted into `inventory-events` and consumed by the backend's Kafka Streams app
  - these records also retruns `inventory-alerts`

## Application state is not clearned properly

We can clean up the whole training environment as

```bash
# stop training platform
./stop-platform-remote.sh

# stop artifacts
./clean.sh
```

```bash
# Delete all topics
python scripts/manage_topics.py --action delete --all

# Tear down database objects
python ./scripts/manage_db.py --action teardown
```

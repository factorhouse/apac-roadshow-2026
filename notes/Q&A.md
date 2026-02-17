## **1. Architecture & Technology Choices**

- **Q: What are CQRS and Event Sourcing, and why do we use them in this architecture?**
  - **CQRS (Command Query Responsibility Segregation)** is the architectural pattern of separating the code that _changes_ data (Writes) from the code that _reads_ data (Queries).
    - **In KartShoppe:** We don't have a single "Monolithic" API.
      - **Write Path:** Transactions go to **Postgres** and optionally to the `order-events` topic in naive implementation.
      - **Read Side:** The **Quarkus** application serves data directly from a local **KTable** (memory).
    - **Benefit:** **Performance & Isolation.** Millions of users browsing the catalog (Reads) will never slow down the order processing (Writes). We can scale the Read nodes (Quarkus) without adding a single connection to the Postgres database.
  - **Event Sourcing** is an architectural pattern that persists the state of a business entity as a sequence of immutable, append-only events rather than just its current state.
    - **In KartShoppe:** It ensures the "Source of Truth" is not the current state in a database, but the **sequence of events** that led to that state. Therefore the `inventory-events` Kafka topic is our ledger. It records "Product Added", "Price Changed", "Inventory Decreased"...
    - **Benefit:** **Resilience & Auditing.** If the Quarkus "View" crashes or the database is corrupted, we don't lose data. We simply **replay** the Kafka topic from the beginning to reconstruct the perfect state of the system at any point in time.

- **Q: Why use Flink for inventory but Kafka Streams (inside Quarkus) for the API?**
  - **A:** **Separation of Concerns.** Flink is the **Write/Processing Layer** (handling heavy state, CDC, and complex joins independently of user traffic). Quarkus is the **Read/Serving Layer** (optimized for high-concurrency WebSockets and REST). This allows you to redeploy business logic (Flink) without disrupting active user connections (Quarkus).

- **Q: What happens if we auto-scale the Quarkus API to e.g. 20 pods?**
  - **A:** **It breaks.** The current implementation uses a **Partitioned KTable**. Since the `inventory-events` topic likely has less partitions, only a subset of pods will receive data. The other remaining pods will sit idle, and users connected to them will see **empty dashboards**.
  - _Fix:_ Use **GlobalKTable** (replicates all data to all pods) or the **Broadcast Pattern** (unique Group IDs to ensure every pod consumes every message).
  - _Note_: In the worshop, it is not possible to scale the Quarkus API seperately because the frontend is bundled into the Quarkus artifact.

- **Q: Why Flink CDC instead of "Dual Writes" (writing to DB + Kafka)?**
  - **A:** **Data Consistency.** Dual writes suffer from the "Two Generals Problem". If the DB commit succeeds but the Kafka write fails (network blip), your systems drift apart. CDC guarantees that if it's in the DB, it eventually gets to Kafka.

## **2. Failure Scenarios & Resiliency**

- **Q: How do we handle the race condition where inventory arrives before product details?**
  - **A:** **Buffering.** The Flink job must use a `CoProcessFunction` to buffer "Orphaned Inventory" events in a state variable until the corresponding "Product" event arrives.
  - _Current Code Reality:_ The Quarkus app simply logs a warning and ignores the partial update, meaning that specific price change is lost until the next update.

- **Q: If Flink crashes and restarts, do we send duplicate alerts?**
  - **A:** **Yes, unless handled.** Flink rewinds to the last checkpoint (e.g., 10 seconds ago) and replays events. Alerts sent during those 10 seconds are sent again.
  - _Fix:_ Downstream consumers must be **idempotent** (deduplicate based on `AlertID`) or Flink must use the `KafkaSink` with `EXACTLY_ONCE` semantics (transactional writes).

- **Q: What if a "Poison Pill" (bad schema data) hits the CDC connector?**
  - **A:** **Infinite Loop.** The Flink job will crash, restart, read the bad record, and crash again.
  - _Fix:_ Configure the Debezium/Flink deserializer to "replace corrupt records with null and filter out" or route errors to a **Dead Letter Queue (DLQ)** instead of crashing.

## **3. Scalability & Performance**

- **Q: How do we handle a "Hot Key" (e.g., one product getting 90% of traffic)?**
  - **A:** **Salting.** Standard autoscaling fails because all traffic for "Product A" goes to _one_ Flink slot. You must "salt" the keys (e.g., `ProductA-1`, `ProductA-2`) to distribute processing, then aggregate the results in a second pass.

- **Q: What happens if the WebSocket clients are too slow (Backpressure)?**
  - **A:** **OOM or Disconnect.** If the client can't read fast enough, Quarkus buffers messages in memory. Eventually, the pod runs out of RAM (OOM Kill) or the Kafka Consumer group leaves the cluster because it stops sending heartbeats, causing a "Rebalance Storm."

## **4. Operational "What-Ifs"**

- **Q: The Flink CDC job is down for 24 hours. What happens to Postgres?**
  - **A:** **Disk Failure.** The Postgres Replication Slot forces the database to keep all Write-Ahead Logs (WAL) since the crash. The disk fills up, and the **production database crashes**.
  - _Ops Rule:_ Monitor "Replication Slot Size." Drop the slot if the lag becomes dangerous.

- **Q: If we update the `initial-products.json` file, does the running job see it?**
  - **A:** **No.** The `HybridSource` reads the file _once_ at startup. If the job restarts from a checkpoint, it remembers it already finished the file and ignores changes. You must start with **Fresh State** (no savepoint) to reload the file.

- **Q: How do we upgrade the Flink job logic without losing current inventory counts?**
  - **A:** **Stateful Upgrade.**
    1. Trigger a **Savepoint** (snapshot of memory).
    2. Stop the job.
    3. Deploy new JAR.
    4. Start with `--fromSavepoint <path>`.
  - _Warning:_ You cannot easily change variable data types (e.g., `int` to `String`) during this process.

# 🎓 KartShoppe Training Setup Guide

## Overview

This guide provides complete instructions for setting up and running the KartShoppe training platform **from a blank slate**. It's designed for training sessions where students start from scratch and progressively build their understanding of Apache Flink and the ecosystem.

---

## 📋 What You'll Learn

- Building real-time data pipelines with Apache Flink
- Integrating Kafka/Redpanda for event streaming
- Using Apache Paimon for data lake storage
- Creating reactive microservices with Quarkus
- Real-time ML model deployment and inference

---

## 🎯 Training Philosophy

This setup follows a **progressive learning approach**:

1. **Start with the platform** - Get the core infrastructure running first
2. **No Flink jobs initially** - Platform works independently (you'll see the UI, can interact, but no real-time processing)
3. **Add Flink jobs progressively** - Each training module introduces ONE Flink pattern at a time
4. **See immediate results** - Every Flink job immediately enhances the platform visibly

---

## 🚀 Before Training Day

### Prerequisites

Ensure students have:
- **Docker Desktop** installed and running
- **8GB RAM** minimum (16GB recommended)
- **Internet connection** (for downloading dependencies)
- **macOS or Linux** (Windows with WSL2 works too)

### One-Time Environment Setup

**Run this script ONCE before the training:**

```bash
./setup-environment.sh
```

This installs:
- ✅ SDKMAN (Java version manager)
- ✅ Java 11 (for building Flink jobs)
- ✅ Java 17 (for running Quarkus)
- ✅ Verifies Docker Desktop
- ✅ Verifies Node.js 18+

**Estimated time:** 5-10 minutes

**Expected output:**
```
╔════════════════════════════════════════════════════════════════╗
║              ✨  Setup Completed Successfully!  ✨             ║
╚════════════════════════════════════════════════════════════════╝

Installed Components:
  ✓ Docker:     Docker version 24.x
  ✓ Node.js:    v20.x.x
  ✓ npm:        10.x.x
  ✓ SDKMAN:     5.x.x
  ✓ Java 11:    11.0.25-tem
  ✓ Java 17:    17.0.13-tem
```

---

## 📚 Training Day Schedule

### Platform Startup (30 minutes)

#### Goal
Get the core KartShoppe platform running **without any Flink jobs**.

#### Steps

1. **Start the platform:**
   ```bash
   ./start-training-platform.sh
   ```

2. **Wait for startup** (30-60 seconds)

3. **Access the application:**
   - KartShoppe App: http://localhost:8081
   - Quarkus Dev UI: http://localhost:8081/q/dev
   - Redpanda Console: http://localhost:8085

4. **Explore the UI:**
   - Browse products
   - Add items to cart
   - Notice there are NO recommendations yet (this is expected!)
   - The app is "online" but not processing events in real-time

#### What's Running?

```
┌─────────────────────────────────────────────────────────────┐
│  PLATFORM ARCHITECTURE (No Flink Yet)                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Frontend (React) ←─ Quinoa ─→ Quarkus API                  │
│                                     ↓                       │
│                                WebSockets                   │
│                                     ↓                       │
│                              Redpanda (Kafka)               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Key Point:** The platform is fully functional, but there's no real-time data processing happening yet!

---

### Session 2: Module 1 - Inventory Management (90 minutes)

#### Learning Objectives
- Understand Flink's keyed state
- Learn about event-time processing
- See real-time state updates

#### Concepts Covered
1. **Keyed State** - Per-product inventory tracking
2. **Timers** - Timeout detection for low stock
3. **Side Outputs** - Multi-way event routing
4. **File + Kafka Hybrid Source** - Bootstrap from files, continue with streaming

#### Start the Inventory Job

**In a new terminal:**
```bash
./flink-inventory-with-orders-job.sh
```

#### What Happens?

1. **Initial Load:**
   - Reads `data/initial-products.json`
   - Loads ~44 products into Flink state
   - Publishes to Kafka → Quarkus → Frontend

2. **Real-time Updates:**
   - Listens to `inventory_updates` Kafka topic
   - Updates stock levels in real-time
   - Detects low-stock conditions
   - Routes alerts via side outputs

3. **Visible in UI:**
   - Product inventory counts appear
   - Stock levels update live when you add to cart
   - Low-stock warnings (if stock drops below threshold)

#### Monitor the Job

- **Flink Dashboard:** http://localhost:8081 (if running Flink)
- **Check state:** http://localhost:8081/api/ecommerce/inventory/state
- **View Kafka topics:** http://localhost:8085 → Click `products` topic

#### Hands-On Exercise

1. Add 10 items of the same product to your cart
2. Watch the inventory count decrease in real-time
3. Check Flink dashboard to see events processed
4. View Kafka topic to see inventory update messages

---

### Session 3: Module 2 - Basket Analysis & Recommendations (90 minutes)

#### Learning Objectives
- Understand stateful stream processing
- Learn about broadcast state patterns
- See ML model deployment in Flink

#### Concepts Covered
1. **Stateful Processing** - Track basket patterns across events
2. **Broadcast State** - Distribute ML models to all tasks
3. **Co-Process Functions** - Combine multiple streams
4. **Real-time ML Inference** - Score products based on cart contents

#### Start the Basket Analysis Job

**In a new terminal:**
```bash
./flink-2-basket-analysis-job.sh
```

#### What Happens?

1. **Pattern Learning:**
   - Analyzes shopping cart events
   - Identifies frequently bought-together products
   - Builds co-occurrence matrix in Flink state

2. **Real-time Recommendations:**
   - When you add item A to cart
   - Flink finds items frequently bought with A
   - Sends recommendations via Kafka → Quarkus → WebSocket → UI

3. **Visible in UI:**
   - **Recommendations panel appears!** (right side of screen)
   - As you add items, recommendations update in real-time
   - "Customers who bought X also bought Y" style suggestions

#### Monitor the Job

- **Flink Dashboard:** http://localhost:8081 (you'll see TWO jobs now!)
- **Recommendations topic:** http://localhost:8085 → `product-recommendations`
- **Basket patterns topic:** http://localhost:8085 → `basket-patterns`

#### Hands-On Exercise

1. Add a laptop to your cart
2. Watch recommendations appear (charger, mouse, laptop bag)
3. Add the recommended mouse
4. See recommendations update based on the combination
5. Check Flink dashboard to see both jobs processing events

---

### Session 4: Module 3 - Hybrid Source with Pre-training (60 minutes)

#### Learning Objectives
- Understand hybrid sources (file → Kafka)
- Learn about warm-starting ML models
- See the difference between cold-start and warm-start

#### Concepts Covered
1. **Hybrid File Source** - Read historical data first
2. **Bounded → Unbounded Transition** - Switch from batch to streaming
3. **Model Pre-training** - Load 5000 historical patterns
4. **Checkpointing** - Save/restore Flink state

#### Stop Basket Job, Start Hybrid Version

**First, stop the current basket job:**
```bash
# Find the process
ps aux | grep basket
# Kill it
kill <PID>
```

**Then start the pre-trained version:**
```bash
./flink-3-hybrid-source-job.sh
```

#### What Happens?

1. **Pre-training Phase:**
   - Loads 5000 historical basket patterns from Paimon
   - Covers 4 product categories
   - 90-day temporal distribution
   - Takes ~10 seconds to load

2. **Live Processing:**
   - Switches to real-time Kafka stream
   - Continues learning from new baskets
   - Much better recommendations from the start!

3. **Visible Difference:**
   - **BEFORE:** Empty recommendations (cold start)
   - **AFTER:** Immediate high-quality recommendations (warm start)

#### Hands-On Exercise

1. Compare recommendation quality vs. Module 2
2. Add electronics → See well-tuned recommendations immediately
3. Add fashion items → See cross-category suggestions
4. View Paimon table: http://localhost:8082

---

### Session 5: Module 4 - Shopping Assistant (60 minutes)

#### Learning Objectives
- Understand CEP (Complex Event Processing)
- Learn about session windows
- See user journey tracking

#### Concepts Covered
1. **CEP Patterns** - Detect cart abandonment sequences
2. **Session Windows** - Group events by user sessions
3. **Fluss Integration** - Low-latency event storage
4. **AI/Chat Integration** - Real-time conversational interface

#### Start the Shopping Assistant Job

**In a new terminal:**
```bash
./flink-4-shopping-assistant-job.sh
```

#### What Happens?

1. **Pattern Detection:**
   - Detects ADD_TO_CART → (timeout) → NO PURCHASE
   - Identifies high-value cart abandonment ($100+)
   - Triggers recovery alerts

2. **Session Tracking:**
   - Groups events by user session (30-min inactivity gap)
   - Calculates session metrics (duration, items viewed, cart value)
   - Classifies sessions: PURCHASE, ABANDONED_CART, BROWSER, QUICK_VISIT

3. **AI Chat:**
   - Real-time product questions
   - Context-aware responses based on cart contents
   - Powered by Flink-backed feature store

4. **Visible in UI:**
   - **Chat panel appears!** (bottom right)
   - Ask "What laptops do you recommend?"
   - See AI responses based on real-time inventory and trends

---

## 🛑 Stopping Everything

### Stop All Flink Jobs

```bash
# Kill all Flink jobs
pkill -f "flink-inventory"
pkill -f "flink-recommendations"
pkill -f "shopping-assistant"
```

### Stop the Platform

```bash
./stop-platform.sh
```

This stops:
- Quarkus API + Frontend
- Redpanda (Kafka)
- All Docker containers

---

## 📁 Project Structure

```
Ververica-visual-demo-1/
├── 0-setup-environment.sh              # One-time prerequisite setup
├── start-training-platform.sh          # Start platform (no Flink)
├── stop-platform.sh                    # Stop everything
├──
├── flink-1-inventory-job.sh            # Module 1: Inventory management
├── flink-2-basket-analysis-job.sh      # Module 2: Recommendations
├── flink-3-hybrid-source-job.sh        # Module 3: Warm-start with history
├── flink-4-shopping-assistant-job.sh   # Module 4: AI chat
├──
├── quarkus-api/                        # Backend API (Java 17)
├── kartshoppe-frontend/                # React frontend
├── flink-inventory/                    # Flink inventory job (Java 11)
├── flink-recommendations/              # Flink basket analysis (Java 11)
├── models/                             # Shared data models
├── docker-compose.yml                  # Infrastructure config
└── data/                               # Sample data files
```

---

## 🎓 Training Tips

### For Instructors

1. **Show, then explain:** Start each Flink job, show the visible change, THEN explain the concepts
2. **Use the UI constantly:** Keep the browser open, refresh often to show real-time updates
3. **Monitor Flink dashboard:** Show the job graph, metrics, backpressure (http://localhost:8081)
4. **Explore Kafka topics:** Use Redpanda Console to see messages flowing (http://localhost:8085)
5. **Explain incrementally:** Each module builds on the previous - don't overwhelm with all patterns at once

### For Students

1. **Follow along:** Run commands in your own terminal
2. **Experiment:** Try breaking things! Stop a Flink job, see what happens, restart it
3. **Ask questions:** Why does this pattern need keyed state? Why use broadcast state here?
4. **Monitor everything:** Open all dashboards (Flink, Redpanda, Quarkus Dev UI)
5. **Read the logs:** `tail -f logs/quarkus.log` and Flink job logs show what's happening

---

## 🐛 Troubleshooting

### Platform Won't Start

```bash
# Check Docker
docker info

# Check Java version
java -version  # Should be 17+

# View logs
tail -f logs/quarkus.log
docker compose logs -f
```

### Flink Job Fails to Start

```bash
# Switch to Java 11 for building Flink jobs
sdk use java 11.0.25-tem

# Rebuild
./gradlew :flink-inventory:clean :flink-inventory:shadowJar

# Check logs
tail -f logs/inventory.log
```

### Port Conflicts

```bash
# Kill process on port 8081 (Quarkus)
lsof -ti:8081 | xargs kill -9

# Kill process on port 8082 (Flink, if running)
lsof -ti:8082 | xargs kill -9
```

### Clean Slate Reset

```bash
# Stop everything
./stop-platform.sh

# Clean Docker volumes
docker compose down -v

# Clean Gradle cache
./gradlew clean

# Restart
./start-training-platform.sh
```

---

## 🎯 Learning Outcomes

By the end of this training, students will:

✅ Understand **Flink's core concepts**: streams, state, time, windows
✅ Build **stateful stream processing** applications
✅ Integrate **Kafka/Redpanda** for event streaming
✅ Deploy **ML models in real-time** with broadcast state
✅ Use **CEP patterns** for complex event detection
✅ Combine **batch and streaming** with hybrid sources
✅ Build **reactive microservices** with Quarkus
✅ Create **real-time data pipelines** end-to-end

---

## 📖 Additional Resources

- [Apache Flink Documentation](https://flink.apache.org)
- [Apache Paimon Documentation](https://paimon.apache.org)
- [Quarkus Guides](https://quarkus.io/guides/)
- [Redpanda Documentation](https://docs.redpanda.com)
- [Ververica Academy](https://www.ververica.com/academy)

---

## 💡 Quick Reference

### Essential Commands

```bash
# Setup (run once)
./0-setup-environment.sh

# Start platform
./start-training-platform.sh

# Module 1: Inventory
./flink-inventory-with-orders-job.sh

# Module 2: Recommendations
./flink-order-cdc-job.sh
```

### Key URLs

| Service | URL |
|---------|-----|
| **KartShoppe App** | http://localhost:8081 |
| **Flink Dashboard** | http://localhost:8082 (if running) |
| **Redpanda Console** | http://localhost:8085 |
| **Quarkus Dev UI** | http://localhost:8081/q/dev |

---

**Happy Learning! 🎉**

For questions or issues, check the main [README.md](README.md) or consult your instructor.

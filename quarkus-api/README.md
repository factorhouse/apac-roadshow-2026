# KartShoppe Quarkus API

This project is the backend API for the KartShoppe e-commerce application, built with Quarkus - the Supersonic Subatomic Java Framework.

## Overview

The Quarkus API serves as the central backend for KartShoppe, providing:
- **REST API endpoints** for product catalog, shopping cart, and checkout operations
- **WebSocket connections** for real-time updates to the frontend
- **Kafka Streams integration** for real-time product inventory caching and event processing
- **Kafka Consumers** for processing product and order events
- **PostgreSQL integration** for order persistence

## Kafka Streams Integration

This application includes a sophisticated Kafka Streams implementation that provides real-time data processing and caching. The Kafka Streams component:

- **Product Cache**: Maintains a materialized view of product inventory and pricing by consuming events from Flink jobs
- **Chat Message Processing**: Processes and caches chat messages with bounded storage
- **Data Visualization**: Handles real-time data points for frontend visualizations
- **WebSocket Broadcasting**: Pushes all updates to connected clients in real-time

For detailed documentation on the Kafka Streams implementation, see: [Kafka Streams Documentation](src/main/java/com/ververica/composable_job/quarkus/kafka/streams/README.md)

## Architecture

The API integrates with the broader KartShoppe ecosystem:
- **Consumes from Kafka**: `inventory-events`, `products`, configured chat/data topics
- **Produces to Kafka**: Order events (when CDC is disabled)
- **Writes to PostgreSQL**: Orders and order items
- **Real-time Updates**: WebSocket connections to all connected browsers

---

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/websockets-next-quickstart-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Related Guides

- WebSockets Next ([guide](https://quarkus.io/guides/websockets-next-reference)): Implementation of the WebSocket API with enhanced efficiency and usability

# Azure Service Bus Demo

Demonstrates Azure Service Bus messaging patterns using the Azure Service Bus Emulator (Docker) and Spring Boot 3 with the `azure-messaging-servicebus` SDK.

---

## Stack

| Component | Version |
|-----------|---------|
| Azure Service Bus Emulator | `mcr.microsoft.com/azure-messaging/servicebus-emulator:latest` |
| Azure SQL Edge (emulator backend) | `mcr.microsoft.com/azure-sql-edge` |
| Spring Boot | 3.4.4 |
| Spring Cloud Azure | 5.22.0 |
| azure-messaging-servicebus SDK | 7.17.10 |
| Java | 21 |

**Note on Spring Cloud Azure 5.x:** `spring-cloud-azure-starter-servicebus` 5.x does **not** include a `@ServiceBusListener` annotation layer — it only bundles the raw `azure-messaging-servicebus` SDK. All consumers use `ServiceBusProcessorClient` started via `ApplicationRunner`.

---

## Patterns Implemented

| Pattern | Queue / Topic | Description |
|---------|---------------|-------------|
| Simple queue | `simple-queue` | Fire-and-forget point-to-point |
| Work queue | `work-queue` | Competing consumers — two processors on same queue |
| Pub/Sub fan-out | `pubsub-topic` → `sub-a`, `sub-b` | Every subscriber gets a copy |
| Content-based routing | `routing-topic` → `sub-all`, `sub-error` | SQL filter `level = 'error'` on `sub-error` |
| Dead-letter queue | `dlq-queue` → DLQ sub-queue | 50% simulated failures → DLQ after 3 attempts |
| Sessions (FIFO) | `session-queue` | `RequiresSession=true` — ordered delivery per `sessionId` |
| Transactions | `tx-queue` | Atomic multi-message send with commit / rollback |

---

## Quick Start

### 1. Start the emulator

```bash
cd docker
docker compose up -d
# wait ~30 s for SQL Edge to become healthy and emulator to initialise
docker compose logs -f servicebus-emulator
```

The emulator exposes AMQP on port **5672**. Namespace is `sbemulatorns`.

### 2. Run the Spring Boot app

```bash
cd spring-demo
mvn spring-boot:run
```

Swagger UI: <http://localhost:8082/swagger-ui.html>

### 3. Try the patterns

```bash
BASE=http://localhost:8082/api

# Simple queue
curl -X POST "$BASE/simple/send?message=hello"

# Work queue (competed by consumer-A and consumer-B)
curl -X POST "$BASE/work/send?message=task-1"

# Pub/Sub (sub-a AND sub-b each receive)
curl -X POST "$BASE/pubsub/publish?message=broadcast"

# Routing — info goes to sub-all only
curl -X POST "$BASE/routing/publish?level=info&message=debug-info"

# Routing — error goes to sub-all AND sub-error
curl -X POST "$BASE/routing/publish?level=error&message=something-broke"

# DLQ (50% chance of abandonment per attempt; moves to DLQ after 3 attempts)
curl -X POST "$BASE/dlq/send?message=risky-payload"

# Sessions — FIFO per sessionId
curl -X POST "$BASE/session/send?message=step-1&sessionId=order-99"
curl -X POST "$BASE/session/send?message=step-2&sessionId=order-99"

# Transactions — 3 messages committed atomically
curl -X POST "$BASE/tx/send?message=batch&count=3"
```

---

## Project Structure

```
azure-service-bus/
├── docker/
│   ├── docker-compose.yml        # Emulator + SQL Edge
│   └── config.json               # Namespace, queues, topics, subscriptions, SQL filters
└── spring-demo/
    ├── pom.xml
    └── src/
        ├── main/java/com/testingai/servicebus/
        │   ├── config/EntityNames.java       # queue/topic/subscription constants
        │   ├── util/FailureSimulator.java    # 50% random failure for DLQ demo
        │   ├── simple/                       # SimpleProducer, SimpleConsumer
        │   ├── workqueue/                    # WorkQueueProducer, ConsumerA, ConsumerB
        │   ├── pubsub/                       # PubSubPublisher, SubscriberA, SubscriberB
        │   ├── routing/                      # RoutingPublisher, ConsumerAll, ConsumerError
        │   ├── dlq/                          # DlqProducer, DlqConsumer (main + DLQ processor)
        │   ├── session/                      # SessionProducer, SessionConsumer
        │   ├── transactions/                 # TransactionalProducer, TransactionalConsumer
        │   └── controller/DemoController.java
        └── test/java/com/testingai/servicebus/
            ├── simple/SimpleProducerTest.java
            ├── workqueue/WorkQueueProducerTest.java
            ├── pubsub/PubSubPublisherTest.java
            ├── routing/RoutingPublisherTest.java
            ├── dlq/DlqProducerTest.java
            ├── session/SessionProducerTest.java
            ├── transactions/TransactionalProducerTest.java
            └── performance/DemoSimulation.java   # Gatling
```

---

## Key Concepts

### Sessions (FIFO guarantee)
The `session-queue` has `RequiresSession: true` in `config.json`. Each message carries a `sessionId`; the broker delivers all messages in the same session to the same consumer in order. The consumer uses `clientBuilder.sessionProcessor()` with `maxConcurrentSessions(2)`.

### SQL filter rules
The `routing-topic → sub-error` subscription uses a SQL filter rule defined in `config.json`:
```json
"Rules": [{"Name": "error-only", "Properties": {"FilterType": "SqlFilter", "SqlExpression": "level = 'error'"}}]
```
The publisher sets `msg.getApplicationProperties().put("level", level)` to trigger this routing.

### Dead-letter queue
`DlqConsumer` calls `ctx.abandon()` on ~50% of messages to simulate processing failures. After `MaxDeliveryCount = 3` failed attempts the broker moves the message to the DLQ sub-queue. A second `ServiceBusProcessorClient` on `.subQueue(SubQueue.DEAD_LETTER_QUEUE)` consumes and logs these dead-lettered messages.

### Transactions
`TransactionalProducer` creates a `ServiceBusTransactionContext`, sends N messages with the context, then commits. On any exception it rolls back — all or nothing.

---

## Running Unit Tests

```bash
cd spring-demo
mvn test
```

8 Mockito-based unit tests — no Docker required.

---

## Gatling Performance Test

Requires the app running on port 8082:

```bash
cd spring-demo
mvn gatling:test
```

Targets all 7 patterns with 10 concurrent users each.

---

## vs. Other Brokers

| Feature | Azure Service Bus | RabbitMQ | Kafka | AWS SQS |
|---------|-----------------|----------|-------|---------|
| Managed cloud service | Yes (Azure) | Self-hosted / CloudAMQP | Confluent / MSK | Yes (AWS) |
| Ordered delivery | Per-session | Per-queue (single consumer) | Per-partition | FIFO queues |
| Content routing | SQL filter rules | Header/topic exchanges | Consumer-side | Message attributes |
| Dead-letter queue | Built-in sub-queue | Configurable exchange | Manual DLQ topic | Built-in |
| Transactions | Yes (sender-side) | Yes (AMQP tx) | Exactly-once (idempotent producer) | No |
| Max message size | 256 KB (1 MB Premium) | Configurable | 1 MB (configurable) | 256 KB |
| Retention | Until consumed | Until consumed / TTL | Configurable (default 7 days) | Up to 14 days |

**Choose Azure Service Bus when:** running on Azure, need enterprise features (sessions, transactions, SQL filters), require strict FIFO, or need to integrate with other Azure services via Event Grid / Logic Apps.

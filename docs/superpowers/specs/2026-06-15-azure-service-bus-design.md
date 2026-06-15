# Azure Service Bus Demo — Design Spec

**Date:** 2026-06-15
**Status:** Approved

## Context

The `message-brokers/` directory contains runnable demos for Kafka, RabbitMQ, Redis Streams, and Amazon SQS. This spec adds an Azure Service Bus demo following the same structure: a Docker Compose environment (using the official Microsoft emulator) and a Spring Boot app demonstrating the key messaging patterns native to Service Bus.

## Location

```
message-brokers/azure-service-bus/
├── docker/
│   ├── docker-compose.yml
│   └── config.json
├── spring-demo/
│   ├── pom.xml
│   └── src/
└── README.md
```

## Infrastructure

### Docker Compose

Two services:

| Service | Image | Purpose |
|---|---|---|
| `sqledge` | `mcr.microsoft.com/azure-sql-edge` | Persistence backend required by the emulator |
| `servicebus-emulator` | `mcr.microsoft.com/azure-messaging/servicebus-emulator:latest` | Azure Service Bus local emulator |

The emulator exposes port **5672** (AMQP). The Spring Boot app connects via the emulator connection string:
```
Endpoint=sb://localhost;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;
```

### config.json

Declarative namespace configuration mounted into the emulator container. Defines all queues, topics, subscriptions, and SQL filter rules — no programmatic admin API calls needed at runtime.

**Queues:**

| Name | Key config |
|---|---|
| `simple-queue` | defaults |
| `work-queue` | defaults |
| `dlq-queue` | `maxDeliveryCount=3` |
| `session-queue` | `requiresSession=true` |
| `tx-queue` | defaults |

**Topics and subscriptions:**

| Topic | Subscription | Filter |
|---|---|---|
| `pubsub-topic` | `sub-a` | TrueFilter (all messages) |
| `pubsub-topic` | `sub-b` | TrueFilter (all messages) |
| `routing-topic` | `sub-all` | TrueFilter (all messages) |
| `routing-topic` | `sub-error` | SqlFilter: `level = 'error'` |

## Spring Boot Application

- **Port:** 8082
- **Package:** `com.testingai.servicebus`
- **Java:** 21
- **Spring Boot:** 3.4.x
- **Spring Cloud Azure:** 5.22.0 (`spring-cloud-azure-starter-servicebus`)
- **Additional deps:** Lombok, springdoc-openapi-starter-webmvc-ui, Gatling

### Package structure

```
com.testingai.servicebus/
├── ServiceBusDemoApplication.java
├── config/
│   └── EntityNames.java              # Queue/topic/subscription name constants
├── controller/
│   └── DemoController.java           # 7 REST endpoints
├── simple/
│   ├── SimpleProducer.java
│   └── SimpleConsumer.java
├── workqueue/
│   ├── WorkQueueProducer.java
│   ├── WorkQueueConsumerA.java
│   └── WorkQueueConsumerB.java
├── pubsub/
│   ├── PubSubPublisher.java
│   ├── PubSubSubscriberA.java
│   └── PubSubSubscriberB.java
├── routing/
│   ├── RoutingPublisher.java
│   ├── RoutingConsumerAll.java
│   └── RoutingConsumerError.java
├── dlq/
│   ├── DlqProducer.java
│   └── DlqConsumer.java              # main queue listener + DLQ listener
├── session/
│   ├── SessionProducer.java
│   └── SessionConsumer.java
├── transactions/
│   ├── TransactionalProducer.java
│   └── TransactionalConsumer.java
└── util/
    └── FailureSimulator.java
```

## Patterns

### 1. Simple queue
Point-to-point delivery. One producer sends to `simple-queue`; one consumer receives.

- **Send:** `ServiceBusTemplate.sendAsync(EntityNames.SIMPLE_QUEUE, message)`
- **Receive:** `@ServiceBusListener(destination = EntityNames.SIMPLE_QUEUE)`

### 2. Work queue (competing consumers)
Each message delivered to exactly one of two consumers. Models load distribution.

- **Send:** `ServiceBusTemplate.sendAsync(EntityNames.WORK_QUEUE, message)`
- **Receive:** `WorkQueueConsumerA` and `WorkQueueConsumerB` both annotated `@ServiceBusListener(destination = EntityNames.WORK_QUEUE)` — Service Bus delivers each message to whichever consumer polls first.

### 3. Pub/Sub
One message delivered independently to all subscribers via a topic. Models fan-out to independent downstream services.

- **Send:** `ServiceBusTemplate.sendAsync(EntityNames.PUBSUB_TOPIC, message)`
- **Receive A:** `@ServiceBusListener(destination = EntityNames.PUBSUB_TOPIC, group = "sub-a")`
- **Receive B:** `@ServiceBusListener(destination = EntityNames.PUBSUB_TOPIC, group = "sub-b")`

### 4. Routing (SQL filter subscriptions)
Messages routed to subscriptions based on an application property. Models content-based dispatch without consumer-side filtering.

- **Send:** `ServiceBusMessage` with application property `level = info|warning|error`; sent to `routing-topic`
- **Receive all:** `@ServiceBusListener(destination = EntityNames.ROUTING_TOPIC, group = "sub-all")` — receives every message (TrueFilter)
- **Receive error:** `@ServiceBusListener(destination = EntityNames.ROUTING_TOPIC, group = "sub-error")` — receives only messages where `level = 'error'` (SqlFilter declared in config.json)

REST endpoint: `POST /demo/routing?key=info|warning|error&message=`

### 5. Dead Letter Queue
Automatic poison-message handling. Consumer fails ~50% of the time; Service Bus redelivers up to `maxDeliveryCount=3`, then moves the message to the DLQ sub-queue automatically.

- **Send:** `ServiceBusTemplate.sendAsync(EntityNames.DLQ_QUEUE, message)`
- **Main consumer:** `@ServiceBusListener(destination = EntityNames.DLQ_QUEUE)` — calls `FailureSimulator.shouldFail()` (50% probability); throws `RuntimeException` on failure
- **DLQ consumer:** `@ServiceBusListener(destination = EntityNames.DLQ_QUEUE + "/$deadletterqueue")` — logs dead-lettered messages

No application retry logic needed — Service Bus handles redelivery and DLQ routing automatically.

### 6. Sessions (FIFO ordering)
Guaranteed in-order delivery per `sessionId`. Models scenarios where sequencing within a logical group is required (user action streams, ledger entries).

- **Send:** `ServiceBusSenderClient` with `message.setSessionId(sessionId)`; one `ServiceBusSenderClient` bean for `session-queue`
- **Receive:** `SessionConsumer` implements `ApplicationRunner`; builds a `ServiceBusProcessorClient` via `.sessionProcessor().queueName(...).maxConcurrentSessions(2)` and starts it at application boot. Messages within the same `sessionId` are delivered strictly in order.

REST endpoint: `POST /demo/session?message=&sessionId=`

### 7. Transactions
Atomic batch send: all messages in a batch commit together or none do. Models financial operations, state machine transitions.

- **Send:** `TransactionalProducer` creates a `ServiceBusTransactionContext` via `ServiceBusSenderClient.createTransaction()`, sends N messages within it, then calls `commitTransaction`. On exception, calls `rollbackTransaction`.
- **Receive:** `@ServiceBusListener(destination = EntityNames.TX_QUEUE)` — logs each message in the committed batch

REST endpoint: `POST /demo/transaction?message=&count=`

## REST API

| Endpoint | Pattern |
|---|---|
| `POST /demo/simple?message=` | Simple queue |
| `POST /demo/work?message=&count=` | Work queue |
| `POST /demo/pubsub?message=` | Pub/Sub |
| `POST /demo/routing?key=info\|warning\|error&message=` | Routing |
| `POST /demo/dlq?message=&count=` | Dead Letter Queue |
| `POST /demo/session?message=&sessionId=` | Sessions |
| `POST /demo/transaction?message=&count=` | Transactions |

Swagger UI at `http://localhost:8082/swagger-ui/index.html`.

## Tests

**Unit tests** (Mockito, matching Kafka's pattern): one producer test per pattern, verifying the correct SDK call is made. No integration tests.

**Gatling performance simulation** covers: simple, work queue, pub/sub, routing. Ramp from 1 to 10 users/s over 30 s, then hold for 30 s. Assertions: p95 < 500 ms, failure rate < 1%.

## What is not implemented

| Kafka pattern | Reason omitted |
|---|---|
| Log compaction | No equivalent in Service Bus |
| Kafka Streams | No embedded stream processing in Service Bus |

These omissions are documented in the high-level `message-brokers/README.md`.

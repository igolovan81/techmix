# RabbitMQ Demo — Design Spec

**Date:** 2026-06-10
**Location:** `message-brokers/rabbitmq/`

---

## Goal

A self-contained learning and demonstration project for RabbitMQ messaging patterns. Consists of:
1. A local 3-node RabbitMQ cluster (Docker Compose)
2. A single Spring Boot 3.4.4 / Java 21 / Maven application demonstrating four canonical patterns

---

## Project Structure

```
message-brokers/
└── rabbitmq/
    ├── docker/
    │   └── docker-compose.yml
    └── spring-demo/
        ├── pom.xml
        └── src/main/java/com/testingai/rabbitmq/
            ├── RabbitMqDemoApplication.java
            ├── config/
            │   ├── SimpleQueueConfig.java
            │   ├── WorkQueueConfig.java
            │   ├── PubSubConfig.java
            │   └── RoutingConfig.java
            ├── simple/
            │   ├── SimpleProducer.java
            │   └── SimpleConsumer.java
            ├── workqueue/
            │   ├── WorkQueueProducer.java
            │   └── WorkQueueConsumer.java
            ├── pubsub/
            │   ├── PubSubProducer.java
            │   ├── PubSubConsumerA.java
            │   └── PubSubConsumerB.java
            ├── routing/
            │   ├── RoutingProducer.java
            │   └── RoutingConsumer.java
            └── controller/
                └── DemoController.java
```

---

## Docker Cluster

- **Image:** `rabbitmq:4-management`
- **Nodes:** `rabbitmq1`, `rabbitmq2`, `rabbitmq3` on a shared `rabbitmq_network` bridge
- **Clustering:** shared `RABBITMQ_ERLANG_COOKIE`; nodes 2 and 3 join node 1 via shell entrypoint
- **Exposed ports (host):**
  - `rabbitmq1`: `5672` (AMQP), `15672` (Management UI)
  - `rabbitmq2`, `rabbitmq3`: internal only
- **Management UI:** `http://localhost:15672` — credentials `guest/guest`
- **Spring Boot connects to:** `rabbitmq1:5672`

---

## Spring Boot Application

**Coordinates:** `com.testingai:rabbitmq-demo:1.0.0`
**Key dependencies:** `spring-boot-starter-amqp`, `spring-boot-starter-web`, `lombok`

### Pattern 1 — Simple Queue

| Element | Value |
|---|---|
| Queue | `simple.queue` (classic, durable) |
| Producer | `SimpleProducer.send(String message)` via `RabbitTemplate.convertAndSend()` |
| Consumer | `SimpleConsumer` — `@RabbitListener(queues = "simple.queue")`, logs received message |

### Pattern 2 — Work Queues (Competing Consumers)

| Element | Value |
|---|---|
| Queue | `work.queue` (quorum, durable) |
| Prefetch | 1 (fair dispatch) |
| Producer | `WorkQueueProducer.send(String message, int count)` — sends `count` messages |
| Consumer | `WorkQueueConsumer` — two `@RabbitListener` methods (`worker1`, `worker2`) each annotated with `@RabbitListener(queues = "work.queue", containerFactory = "workQueueContainerFactory")`; `WorkQueueConfig` declares a `SimpleRabbitListenerContainerFactory` with `concurrency=2` so both methods run as independent concurrent consumers competing on the same queue; each sleeps proportional to the number of dots in the message to demonstrate fair dispatch |

### Pattern 3 — Pub/Sub (Fanout Exchange)

| Element | Value |
|---|---|
| Exchange | `pubsub.fanout` (fanout, durable) |
| Queues | `pubsub.queue.a`, `pubsub.queue.b` (both bound to the exchange, no routing key) |
| Producer | `PubSubProducer.send(String message)` — sends to exchange with empty routing key |
| Consumers | `PubSubConsumerA`, `PubSubConsumerB` — each listens on its own queue; both receive every message |

### Pattern 4 — Routing (Direct Exchange)

| Element | Value |
|---|---|
| Exchange | `routing.direct` (direct, durable) |
| Queues | `routing.queue.all` (bound to keys `info`, `warning`, `error`), `routing.queue.error` (bound to key `error` only) |
| Producer | `RoutingProducer.send(String routingKey, String message)` |
| Consumers | `RoutingConsumer` — one `@RabbitListener` per queue; `routingQueueAll` receives info/warning/error, `routingQueueError` receives error only |

---

## REST API

All endpoints are on `DemoController` (`/demo`). They accept query parameters and return a plain-text confirmation.

| Method | Path | Parameters | Triggers |
|---|---|---|---|
| POST | `/demo/simple` | `message` | Simple queue send |
| POST | `/demo/work` | `message`, `count` (default 5) | Work queue — sends `count` messages |
| POST | `/demo/pubsub` | `message` | Fanout broadcast |
| POST | `/demo/routing` | `key` (`info`/`warning`/`error`), `message` | Direct exchange route |

---

## Configuration (`application.yml`)

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        prefetch: 1
```

---

## Testing

- Unit tests for each producer using `RabbitTemplate` mock
- Integration tests skipped (require live broker) — noted with `@Disabled` and a comment explaining how to run with the Docker cluster up

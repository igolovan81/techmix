# Per-Queue TTL — Design Spec

**Date:** 2026-06-11
**Scope:** Add `x-message-ttl` (5000 ms) to all six queues across the four RabbitMQ patterns in `message-brokers/rabbitmq/spring-demo`

---

## Overview

All queues currently have no message TTL — messages sit indefinitely. This change adds a 5-second TTL to every queue via `QueueBuilder.ttl()`. Messages that are not consumed within 5 seconds are silently dropped by RabbitMQ.

---

## Config Changes

Each config class gains a `MESSAGE_TTL_MS = 5000` constant and switches queue declarations to `QueueBuilder` with `.ttl(MESSAGE_TTL_MS)`.

### SimpleQueueConfig

Replace `new Queue(QUEUE_NAME, true)` with `QueueBuilder`:

```java
public static final int MESSAGE_TTL_MS = 5000;

@Bean
public Queue simpleQueue() {
    return QueueBuilder.durable(QUEUE_NAME).ttl(MESSAGE_TTL_MS).build();
}
```

### WorkQueueConfig

Chain `.ttl()` onto the existing quorum builder:

```java
public static final int MESSAGE_TTL_MS = 5000;

@Bean
public Queue workQueue() {
    return QueueBuilder.durable(QUEUE_NAME).quorum().ttl(MESSAGE_TTL_MS).build();
}
```

### PubSubConfig

Replace both `new Queue(...)` with `QueueBuilder`:

```java
public static final int MESSAGE_TTL_MS = 5000;

@Bean
public Queue pubSubQueueA() {
    return QueueBuilder.durable(QUEUE_A).ttl(MESSAGE_TTL_MS).build();
}

@Bean
public Queue pubSubQueueB() {
    return QueueBuilder.durable(QUEUE_B).ttl(MESSAGE_TTL_MS).build();
}
```

### RoutingConfig

Replace both `new Queue(...)` with `QueueBuilder`:

```java
public static final int MESSAGE_TTL_MS = 5000;

@Bean
public Queue routingQueueAll() {
    return QueueBuilder.durable(QUEUE_ALL).ttl(MESSAGE_TTL_MS).build();
}

@Bean
public Queue routingQueueError() {
    return QueueBuilder.durable(QUEUE_ERROR).ttl(MESSAGE_TTL_MS).build();
}
```

---

## Tests

One new test class per config — unit tests, no broker required. Each test instantiates the config class directly, calls the queue bean method(s), and asserts the `x-message-ttl` argument equals `5000`.

| Test class | Queues verified |
|---|---|
| `SimpleQueueConfigTest` | `simple.queue` |
| `WorkQueueConfigTest` | `work.queue` |
| `PubSubConfigTest` | `pubsub.queue.a`, `pubsub.queue.b` |
| `RoutingConfigTest` | `routing.queue.all`, `routing.queue.error` |

---

## Files to Change

### Config files (add TTL)
- `config/SimpleQueueConfig.java`
- `config/WorkQueueConfig.java`
- `config/PubSubConfig.java`
- `config/RoutingConfig.java`

### New test files
- `src/test/java/com/testingai/rabbitmq/config/SimpleQueueConfigTest.java`
- `src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java`
- `src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java`
- `src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java`

---

## Out of Scope

- Dead-letter exchange (DLX) for expired messages
- Per-message TTL
- Configurable TTL via `application.yml`
- Changes to producers, consumers, or controller

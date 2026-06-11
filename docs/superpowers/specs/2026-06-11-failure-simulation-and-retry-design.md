# Failure Simulation & Retry Logic — Design Spec

**Date:** 2026-06-11
**Scope:** Add 5% random failure simulation and pattern-specific RabbitMQ-level retry to all four messaging patterns in `message-brokers/rabbitmq/spring-demo`

---

## Overview

Each consumer gains a `FailureSimulator.maybeThrow()` call that throws `RuntimeException` with 5% probability. Each pattern then handles the failure using a different RabbitMQ-native retry strategy, demonstrating three distinct approaches:

| Pattern | Retry strategy | Max attempts |
|---|---|---|
| Simple | Redelivered flag (Option C) | 2 (1 retry) |
| Work Queue | `x-delivery-limit` on quorum queue (Option A) | 3 |
| Pub/Sub | DLX retry chain with 2s delay (Option B) | 3 |
| Routing | `x-delivery-limit` on quorum queue (Option A) | 3 |

---

## Section 1: Failure Simulation Utility

**New file:** `src/main/java/com/testingai/rabbitmq/util/FailureSimulator.java`

```java
package com.testingai.rabbitmq.util;

public class FailureSimulator {
    private static final double FAILURE_RATE = 0.05;

    public static void maybeThrow(String context) {
        if (Math.random() < FAILURE_RATE) {
            throw new RuntimeException("Simulated 5% failure in " + context);
        }
    }
}
```

Called at the top of every consumer's try block before `basicAck`. On failure, the `RuntimeException` propagates to the catch block where pattern-specific nack logic fires.

---

## Section 2: Simple Pattern — Option C (Redelivered Flag)

**Strategy:** On first failure, nack with requeue=true. If RabbitMQ redelivers and it fails again, nack with requeue=false (drop). Max 2 attempts total.

**Config change:** None — `SimpleQueueConfig` unchanged.

**Consumer change (`SimpleConsumer`):**
- Add `@Header(AmqpHeaders.REDELIVERED) boolean redelivered` parameter
- Try block: call `FailureSimulator.maybeThrow("[SimpleConsumer]")` before log
- On `RuntimeException`: `channel.basicNack(deliveryTag, false, !redelivered)`
- Log the requeue decision

```java
@RabbitListener(queues = SimpleQueueConfig.QUEUE_NAME, containerFactory = "simpleContainerFactory")
public void receive(String message, Channel channel,
                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                    @Header(AmqpHeaders.REDELIVERED) boolean redelivered) throws IOException {
    try {
        FailureSimulator.maybeThrow("[SimpleConsumer]");
        log.info("[SimpleConsumer] Received: {}", message);
        channel.basicAck(deliveryTag, false);
    } catch (RuntimeException e) {
        log.warn("[SimpleConsumer] Failed (redelivered={}), requeue={}: {}", redelivered, !redelivered, e.getMessage());
        channel.basicNack(deliveryTag, false, !redelivered);
    } catch (IOException e) {
        throw e;
    }
}
```

---

## Section 3: Work Queue Pattern — Option A (x-delivery-limit)

**Strategy:** Always nack with requeue=true on failure. RabbitMQ's native delivery tracking on the quorum queue drops the message after 3 failed delivery attempts.

**Config change (`WorkQueueConfig`):**
- Add `.deliveryLimit(3)` to the quorum queue builder

```java
@Bean
public Queue workQueue() {
    return QueueBuilder.durable(QUEUE_NAME).quorum().deliveryLimit(3).ttl(MESSAGE_TTL_MS).build();
}
```

**Consumer change (`WorkQueueConsumer` — both worker1 and worker2):**
- Try block: call `FailureSimulator.maybeThrow("[Worker1]")` after Processing log
- On `RuntimeException`: `channel.basicNack(deliveryTag, false, true)`

```java
@RabbitListener(queues = WorkQueueConfig.QUEUE_NAME, containerFactory = "workQueueContainerFactory")
public void worker1(String message, Channel channel,
                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException, InterruptedException {
    try {
        log.info("[Worker1] Processing: {}", message);
        FailureSimulator.maybeThrow("[Worker1]");
        simulateWork(message);
        log.info("[Worker1] Done: {}", message);
        channel.basicAck(deliveryTag, false);
    } catch (RuntimeException e) {
        log.warn("[Worker1] Failed, requeuing for retry: {}", e.getMessage());
        channel.basicNack(deliveryTag, false, true);
    } catch (IOException e) {
        throw e;
    }
}
```

---

## Section 4: Pub/Sub Pattern — Option B (DLX Retry Chain)

**Strategy:** Failed messages are dead-lettered to a retry queue with a 2-second TTL. After TTL, they return to the original queue. After 3 DLX cycles (tracked via `x-death` header count), the message is silently discarded via `basicAck`.

**Config changes (`PubSubConfig`):**

Main queues gain DLX configuration pointing to the default exchange with per-queue routing keys:

```java
@Bean
public Queue pubSubQueueA() {
    return QueueBuilder.durable(QUEUE_A)
            .ttl(MESSAGE_TTL_MS)
            .deadLetterExchange("")
            .deadLetterRoutingKey(RETRY_QUEUE_A)
            .build();
}

@Bean
public Queue pubSubQueueB() {
    return QueueBuilder.durable(QUEUE_B)
            .ttl(MESSAGE_TTL_MS)
            .deadLetterExchange("")
            .deadLetterRoutingKey(RETRY_QUEUE_B)
            .build();
}
```

Two new retry queues (TTL=2000ms, DLX back to original):

```java
public static final String RETRY_QUEUE_A = "pubsub.retry.queue.a";
public static final String RETRY_QUEUE_B = "pubsub.retry.queue.b";
public static final int RETRY_DELAY_MS = 2000;
public static final int MAX_RETRIES = 3;

@Bean
public Queue pubSubRetryQueueA() {
    return QueueBuilder.durable(RETRY_QUEUE_A)
            .ttl(RETRY_DELAY_MS)
            .deadLetterExchange("")
            .deadLetterRoutingKey(QUEUE_A)
            .build();
}

@Bean
public Queue pubSubRetryQueueB() {
    return QueueBuilder.durable(RETRY_QUEUE_B)
            .ttl(RETRY_DELAY_MS)
            .deadLetterExchange("")
            .deadLetterRoutingKey(QUEUE_B)
            .build();
}
```

**Consumer changes (`PubSubConsumerA` and `PubSubConsumerB`):**
- Add `@Header(value = "x-death", required = false) List<Map<String, Object>> xDeath` parameter
- Try block: call `FailureSimulator.maybeThrow("[PubSubConsumerA]")`
- On `RuntimeException`: check x-death count; if ≥ 3 → `basicAck` (discard); else → `basicNack(requeue=false)` → into retry chain

```java
@RabbitListener(queues = PubSubConfig.QUEUE_A, containerFactory = "pubSubContainerFactory")
public void receive(String message, Channel channel,
                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                    @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
    try {
        FailureSimulator.maybeThrow("[PubSubConsumerA]");
        log.info("[PubSubConsumerA] Received: {}", message);
        channel.basicAck(deliveryTag, false);
    } catch (RuntimeException e) {
        long retryCount = xDeath == null ? 0L : (Long) xDeath.get(0).get("count");
        if (retryCount >= PubSubConfig.MAX_RETRIES) {
            log.error("[PubSubConsumerA] Max retries ({}) exceeded, discarding: {}", PubSubConfig.MAX_RETRIES, message);
            channel.basicAck(deliveryTag, false);
        } else {
            log.warn("[PubSubConsumerA] Failed (retry {}/{}), sending to retry queue: {}", retryCount + 1, PubSubConfig.MAX_RETRIES, e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    } catch (IOException e) {
        throw e;
    }
}
```

---

## Section 5: Routing Pattern — Option A (x-delivery-limit, convert to quorum)

**Strategy:** Convert both routing queues from classic to quorum. Set `x-delivery-limit = 3`. On failure nack with requeue=true; RabbitMQ drops after 3 attempts.

**Config changes (`RoutingConfig`):**
- Add `.quorum().deliveryLimit(3)` to both queue builders

```java
@Bean
public Queue routingQueueAll() {
    return QueueBuilder.durable(QUEUE_ALL).quorum().deliveryLimit(3).ttl(MESSAGE_TTL_MS).build();
}

@Bean
public Queue routingQueueError() {
    return QueueBuilder.durable(QUEUE_ERROR).quorum().deliveryLimit(3).ttl(MESSAGE_TTL_MS).build();
}
```

**Consumer changes (`RoutingConsumer` — both receiveAll and receiveError):**
- Try block: call `FailureSimulator.maybeThrow("[RoutingConsumer/ALL]")`
- On `RuntimeException`: `channel.basicNack(deliveryTag, false, true)`

```java
@RabbitListener(queues = RoutingConfig.QUEUE_ALL, containerFactory = "routingContainerFactory")
public void receiveAll(String message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
    try {
        FailureSimulator.maybeThrow("[RoutingConsumer/ALL]");
        log.info("[RoutingConsumer/ALL] Received: {}", message);
        channel.basicAck(deliveryTag, false);
    } catch (RuntimeException e) {
        log.warn("[RoutingConsumer/ALL] Failed, requeuing for retry: {}", e.getMessage());
        channel.basicNack(deliveryTag, false, true);
    } catch (IOException e) {
        throw e;
    }
}
```

---

## Files to Change

### New files
- `src/main/java/com/testingai/rabbitmq/util/FailureSimulator.java`

### Modified source files
- `src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java` — add redelivered header, failure simulation, nack logic
- `src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java` — add deliveryLimit(3)
- `src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java` — add failure simulation, nack logic
- `src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java` — add DLX config to main queues, add retry queues + constants
- `src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java` — add x-death header, failure simulation, DLX nack logic
- `src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java` — same as PubSubConsumerA
- `src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java` — convert to quorum, add deliveryLimit(3)
- `src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java` — add failure simulation, nack logic

### Modified test files
- `src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java` — update tests for new signature + nack behaviour
- `src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java` — update tests for nack behaviour + add failure simulation tests
- `src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java` — update for new signature + DLX nack logic tests
- `src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java` — same
- `src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java` — update for nack behaviour
- `src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java` — add deliveryLimit assertion
- `src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java` — add DLX argument assertions + retry queue TTL assertions
- `src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java` — add quorum + deliveryLimit assertions

### New test files
- `src/test/java/com/testingai/rabbitmq/util/FailureSimulatorTest.java`

---

## Out of Scope
- Dead-letter queues for messages that exceed retry limits (messages are dropped)
- Metrics or monitoring of failed/retried messages
- Configurable failure rate or retry counts via `application.yml`

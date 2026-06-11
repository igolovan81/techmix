# Manual Acknowledgment — Design Spec

**Date:** 2026-06-11
**Scope:** Add `AcknowledgeMode.MANUAL` to all four RabbitMQ messaging patterns in `message-brokers/rabbitmq/spring-demo`

---

## Overview

All four existing consumers (`SimpleConsumer`, `WorkQueueConsumer`, `PubSubConsumerA/B`, `RoutingConsumer`) currently rely on Spring's default `AUTO` ack mode. This change adds per-pattern `SimpleRabbitListenerContainerFactory` beans and updates each consumer to explicitly call `channel.basicAck` / `channel.basicNack`.

---

## Configuration Changes

Each existing config class gains one `SimpleRabbitListenerContainerFactory` bean. The factory is injected with Spring's `ConnectionFactory`, sets `AcknowledgeMode.MANUAL`, and each `@RabbitListener` references it by name.

| Config class | New bean name | Consumer(s) |
|---|---|---|
| `SimpleQueueConfig` | `simpleContainerFactory` | `SimpleConsumer` |
| `WorkQueueConfig` | `workQueueContainerFactory` | `WorkQueueConsumer` (worker1, worker2) |
| `PubSubConfig` | `pubSubContainerFactory` | `PubSubConsumerA`, `PubSubConsumerB` |
| `RoutingConfig` | `routingContainerFactory` | `RoutingConsumer` (receiveAll, receiveError) |

Factory bean pattern (same for all four):

```java
@Bean
public SimpleRabbitListenerContainerFactory <name>ContainerFactory(ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    return factory;
}
```

The existing `prefetch: 1` in `application.yml` continues to apply globally via Spring Boot auto-configuration and does not need to be set on the factory explicitly.

---

## Consumer Changes

Every listener method gains two additional parameters and an explicit ack/nack call.

**Signature change (all consumers):**

```java
// before
public void receive(String message) throws InterruptedException

// after
public void receive(String message, Channel channel,
                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
        throws IOException, InterruptedException
```

**Body pattern:**

```java
try {
    // existing logic unchanged
    channel.basicAck(deliveryTag, false);
} catch (Exception e) {
    log.error("[Consumer] Failed: {}", message, e);
    channel.basicNack(deliveryTag, false, true); // requeue=true
}
```

**`@RabbitListener` annotation update** — each annotation gains `containerFactory`:

```java
@RabbitListener(queues = XConfig.QUEUE_NAME, containerFactory = "xContainerFactory")
```

---

## Files to Change

### Config files (add factory bean)
- `config/SimpleQueueConfig.java`
- `config/WorkQueueConfig.java`
- `config/PubSubConfig.java`
- `config/RoutingConfig.java`

### Consumer files (update signature + ack logic)
- `simple/SimpleConsumer.java`
- `workqueue/WorkQueueConsumer.java`
- `pubsub/PubSubConsumerA.java`
- `pubsub/PubSubConsumerB.java`
- `routing/RoutingConsumer.java`

---

## Error Handling

- On success: `channel.basicAck(deliveryTag, false)` — single message, no batch ack
- On failure: `channel.basicNack(deliveryTag, false, true)` — single message, requeue=true for all patterns
- `IOException` from `basicAck`/`basicNack` propagates out of the method; Spring AMQP will log and recover the channel

---

## Out of Scope

- Dead-letter queues / DLX configuration
- Retry policies
- Changes to producers, controller, or tests

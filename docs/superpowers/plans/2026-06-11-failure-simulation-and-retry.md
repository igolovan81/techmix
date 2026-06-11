# Failure Simulation & Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 5% random failure simulation to all consumers and wire up pattern-specific RabbitMQ-level retry: redelivered-flag for Simple, x-delivery-limit for Work Queue and Routing, DLX retry chain for Pub/Sub.

**Architecture:** A shared `FailureSimulator` utility throws `RuntimeException` with 5% probability. Each consumer's try/catch is restructured to let the exception propagate to a pattern-specific nack handler. Config classes are updated where needed (delivery limit on quorum queues, DLX on pub/sub queues). Tests use `MockedStatic<FailureSimulator>` to control failure injection deterministically.

**Tech Stack:** Spring AMQP, RabbitMQ quorum queues, JUnit 5, Mockito (MockedStatic for static method control), AssertJ.

---

## File Map

| Action | File |
|--------|------|
| Create | `src/main/java/com/testingai/rabbitmq/util/FailureSimulator.java` |
| Create | `src/test/java/com/testingai/rabbitmq/util/FailureSimulatorTest.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java` |
| Modify | `src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java` |
| Modify | `src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java` |

---

## Task 1: FailureSimulator Utility

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/util/FailureSimulator.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/util/FailureSimulatorTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/testingai/rabbitmq/util/FailureSimulatorTest.java`:

```java
package com.testingai.rabbitmq.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailureSimulatorTest {

    @Test
    void maybeThrow_shouldThrowRuntimeExceptionOccasionally() {
        int failures = 0;
        for (int i = 0; i < 500; i++) {
            try {
                FailureSimulator.maybeThrow("test");
            } catch (RuntimeException e) {
                failures++;
            }
        }
        assertThat(failures).isGreaterThan(0).isLessThan(500);
    }

    @Test
    void maybeThrow_shouldIncludeContextInMessage() {
        RuntimeException caught = null;
        for (int i = 0; i < 1000 && caught == null; i++) {
            try {
                FailureSimulator.maybeThrow("myContext");
            } catch (RuntimeException e) {
                caught = e;
            }
        }
        assertThat(caught).isNotNull();
        assertThat(caught.getMessage()).contains("myContext");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=FailureSimulatorTest
```

Expected: COMPILATION ERROR — `FailureSimulator` does not exist yet.

- [ ] **Step 3: Create `FailureSimulator`**

Create `src/main/java/com/testingai/rabbitmq/util/FailureSimulator.java`:

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

- [ ] **Step 4: Run test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=FailureSimulatorTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/util/FailureSimulator.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/util/FailureSimulatorTest.java
git commit -m "feat: add FailureSimulator utility with 5% failure rate"
```

---

## Task 2: Simple Pattern — Redelivered Flag (Option C)

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java`

- [ ] **Step 1: Replace `SimpleConsumerTest`**

Replace the full content of `SimpleConsumerTest.java` with:

```java
package com.testingai.rabbitmq.simple;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleConsumerTest {

    @InjectMocks
    private SimpleConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.receive("hello", channel, 1L, false);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void receive_shouldNackWithRequeueOnFirstFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receive("hello", channel, 1L, false);
            verify(channel).basicNack(1L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    @Test
    void receive_shouldNackWithoutRequeueOnRedeliveredFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receive("hello", channel, 1L, true);
            verify(channel).basicNack(1L, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=SimpleConsumerTest
```

Expected: COMPILATION ERROR — `SimpleConsumer.receive` does not yet accept `boolean redelivered` parameter.

- [ ] **Step 3: Update `SimpleConsumer`**

Replace the full content of `SimpleConsumer.java` with:

```java
package com.testingai.rabbitmq.simple;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.SimpleQueueConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SimpleConsumer {

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
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=SimpleConsumerTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java
git commit -m "feat: add failure simulation and redelivered-flag retry to simple consumer"
```

---

## Task 3: Work Queue Pattern — x-delivery-limit (Option A)

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java`

- [ ] **Step 1: Add delivery limit test to `WorkQueueConfigTest`**

Replace the full content of `WorkQueueConfigTest.java` with:

```java
package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class WorkQueueConfigTest {

    private final WorkQueueConfig config = new WorkQueueConfig();

    @Test
    void workQueue_shouldHaveTtlOf5000ms() {
        Queue queue = config.workQueue();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }

    @Test
    void workQueue_shouldHaveDeliveryLimitOf3() {
        Queue queue = config.workQueue();
        assertThat(queue.getArguments()).containsEntry("x-delivery-limit", 3);
    }
}
```

- [ ] **Step 2: Run test to verify new test fails**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=WorkQueueConfigTest
```

Expected: `workQueue_shouldHaveDeliveryLimitOf3` FAILS — `x-delivery-limit` not yet present.

- [ ] **Step 3: Update `WorkQueueConfig`**

Replace the full content of `WorkQueueConfig.java` with:

```java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkQueueConfig {

    public static final String QUEUE_NAME = "work.queue";
    public static final int MESSAGE_TTL_MS = 5000;
    public static final int DELIVERY_LIMIT = 3;

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(QUEUE_NAME).quorum().deliveryLimit(DELIVERY_LIMIT).ttl(MESSAGE_TTL_MS).build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory workQueueContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
```

- [ ] **Step 4: Run config test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=WorkQueueConfigTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Replace `WorkQueueConsumerTest`**

Replace the full content of `WorkQueueConsumerTest.java` with:

```java
package com.testingai.rabbitmq.workqueue;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkQueueConsumerTest {

    @InjectMocks
    private WorkQueueConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void worker1_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.worker1("task", channel, 1L);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void worker1_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.worker1("task", channel, 1L);
            verify(channel).basicNack(1L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    @Test
    void worker2_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.worker2("task", channel, 2L);
            verify(channel).basicAck(2L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void worker2_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.worker2("task", channel, 2L);
            verify(channel).basicNack(2L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }
}
```

- [ ] **Step 6: Run consumer test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=WorkQueueConsumerTest
```

Expected: COMPILATION ERROR — `WorkQueueConsumer` does not yet call `FailureSimulator.maybeThrow`.

- [ ] **Step 7: Update `WorkQueueConsumer`**

Replace the full content of `WorkQueueConsumer.java` with:

```java
package com.testingai.rabbitmq.workqueue;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.WorkQueueConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class WorkQueueConsumer {

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

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME, containerFactory = "workQueueContainerFactory")
    public void worker2(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException, InterruptedException {
        try {
            log.info("[Worker2] Processing: {}", message);
            FailureSimulator.maybeThrow("[Worker2]");
            simulateWork(message);
            log.info("[Worker2] Done: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            log.warn("[Worker2] Failed, requeuing for retry: {}", e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException e) {
            throw e;
        }
    }

    private void simulateWork(String message) throws InterruptedException {
        long dots = message.chars().filter(c -> c == '.').count();
        Thread.sleep(dots * 1000);
    }
}
```

- [ ] **Step 8: Run consumer test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=WorkQueueConsumerTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java
git commit -m "feat: add failure simulation and x-delivery-limit retry to work queue"
```

---

## Task 4: Pub/Sub Pattern — DLX Retry Chain (Option B)

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java`

- [ ] **Step 1: Replace `PubSubConfigTest`**

Replace the full content of `PubSubConfigTest.java` with:

```java
package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class PubSubConfigTest {

    private final PubSubConfig config = new PubSubConfig();

    @Test
    void pubSubQueueA_shouldHaveTtlOf5000ms() {
        Queue queue = config.pubSubQueueA();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }

    @Test
    void pubSubQueueB_shouldHaveTtlOf5000ms() {
        Queue queue = config.pubSubQueueB();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }

    @Test
    void pubSubQueueA_shouldHaveDlxRoutingKeyToRetryQueue() {
        Queue queue = config.pubSubQueueA();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.RETRY_QUEUE_A);
    }

    @Test
    void pubSubQueueB_shouldHaveDlxRoutingKeyToRetryQueue() {
        Queue queue = config.pubSubQueueB();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.RETRY_QUEUE_B);
    }

    @Test
    void pubSubRetryQueueA_shouldHaveTtlOf2000ms() {
        Queue queue = config.pubSubRetryQueueA();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", PubSubConfig.RETRY_DELAY_MS);
    }

    @Test
    void pubSubRetryQueueA_shouldHaveDlxBackToMainQueue() {
        Queue queue = config.pubSubRetryQueueA();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.QUEUE_A);
    }

    @Test
    void pubSubRetryQueueB_shouldHaveTtlOf2000ms() {
        Queue queue = config.pubSubRetryQueueB();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", PubSubConfig.RETRY_DELAY_MS);
    }

    @Test
    void pubSubRetryQueueB_shouldHaveDlxBackToMainQueue() {
        Queue queue = config.pubSubRetryQueueB();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "");
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-routing-key", PubSubConfig.QUEUE_B);
    }
}
```

- [ ] **Step 2: Run config tests to verify new ones fail**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=PubSubConfigTest
```

Expected: 6 new tests FAIL — DLX config and retry queues not yet present.

- [ ] **Step 3: Update `PubSubConfig`**

Replace the full content of `PubSubConfig.java` with:

```java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubSubConfig {

    public static final String EXCHANGE_NAME = "pubsub.fanout";
    public static final String QUEUE_A = "pubsub.queue.a";
    public static final String QUEUE_B = "pubsub.queue.b";
    public static final String RETRY_QUEUE_A = "pubsub.retry.queue.a";
    public static final String RETRY_QUEUE_B = "pubsub.retry.queue.b";
    public static final int MESSAGE_TTL_MS = 5000;
    public static final int RETRY_DELAY_MS = 2000;
    public static final int MAX_RETRIES = 3;

    @Bean
    public FanoutExchange pubSubExchange() {
        return new FanoutExchange(EXCHANGE_NAME, true, false);
    }

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

    @Bean
    public Binding bindingA(FanoutExchange pubSubExchange, Queue pubSubQueueA) {
        return BindingBuilder.bind(pubSubQueueA).to(pubSubExchange);
    }

    @Bean
    public Binding bindingB(FanoutExchange pubSubExchange, Queue pubSubQueueB) {
        return BindingBuilder.bind(pubSubQueueB).to(pubSubExchange);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory pubSubContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
```

- [ ] **Step 4: Run config tests to verify they pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=PubSubConfigTest
```

Expected: `Tests run: 8, Failures: 0, Errors: 0`

- [ ] **Step 5: Replace `PubSubConsumerATest`**

Replace the full content of `PubSubConsumerATest.java` with:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerATest {

    @InjectMocks
    private PubSubConsumerA consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.receive("broadcast", channel, 1L, null);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void receive_shouldNackToDlxOnFirstFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receive("broadcast", channel, 1L, null);
            verify(channel).basicNack(1L, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    @Test
    void receive_shouldDiscardAfterMaxRetries() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            List<Map<String, Object>> xDeath = List.of(Map.of("count", (long) PubSubConfig.MAX_RETRIES));
            consumer.receive("broadcast", channel, 1L, xDeath);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }
}
```

- [ ] **Step 6: Replace `PubSubConsumerBTest`**

Replace the full content of `PubSubConsumerBTest.java` with:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerBTest {

    @InjectMocks
    private PubSubConsumerB consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.receive("broadcast", channel, 1L, null);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void receive_shouldNackToDlxOnFirstFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receive("broadcast", channel, 1L, null);
            verify(channel).basicNack(1L, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    @Test
    void receive_shouldDiscardAfterMaxRetries() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            List<Map<String, Object>> xDeath = List.of(Map.of("count", (long) PubSubConfig.MAX_RETRIES));
            consumer.receive("broadcast", channel, 1L, xDeath);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }
}
```

- [ ] **Step 7: Run consumer tests to verify they fail**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest="PubSubConsumerATest,PubSubConsumerBTest"
```

Expected: COMPILATION ERROR — `PubSubConsumerA/B.receive` does not yet accept `xDeath` parameter.

- [ ] **Step 8: Update `PubSubConsumerA`**

Replace the full content of `PubSubConsumerA.java` with:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PubSubConsumerA {

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
}
```

- [ ] **Step 9: Update `PubSubConsumerB`**

Replace the full content of `PubSubConsumerB.java` with:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PubSubConsumerB {

    @RabbitListener(queues = PubSubConfig.QUEUE_B, containerFactory = "pubSubContainerFactory")
    public void receive(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath) throws IOException {
        try {
            FailureSimulator.maybeThrow("[PubSubConsumerB]");
            log.info("[PubSubConsumerB] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            long retryCount = xDeath == null ? 0L : (Long) xDeath.get(0).get("count");
            if (retryCount >= PubSubConfig.MAX_RETRIES) {
                log.error("[PubSubConsumerB] Max retries ({}) exceeded, discarding: {}", PubSubConfig.MAX_RETRIES, message);
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("[PubSubConsumerB] Failed (retry {}/{}), sending to retry queue: {}", retryCount + 1, PubSubConfig.MAX_RETRIES, e.getMessage());
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (IOException e) {
            throw e;
        }
    }
}
```

- [ ] **Step 10: Run consumer tests to verify they pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest="PubSubConsumerATest,PubSubConsumerBTest"
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 11: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java
git commit -m "feat: add failure simulation and DLX retry chain to pub/sub pattern"
```

---

## Task 5: Routing Pattern — Quorum + x-delivery-limit (Option A)

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java`
- Modify: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java`

- [ ] **Step 1: Add delivery limit tests to `RoutingConfigTest`**

Replace the full content of `RoutingConfigTest.java` with:

```java
package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingConfigTest {

    private final RoutingConfig config = new RoutingConfig();

    @Test
    void routingQueueAll_shouldHaveTtlOf5000ms() {
        Queue queue = config.routingQueueAll();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }

    @Test
    void routingQueueError_shouldHaveTtlOf5000ms() {
        Queue queue = config.routingQueueError();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }

    @Test
    void routingQueueAll_shouldHaveDeliveryLimitOf3() {
        Queue queue = config.routingQueueAll();
        assertThat(queue.getArguments()).containsEntry("x-delivery-limit", 3);
    }

    @Test
    void routingQueueError_shouldHaveDeliveryLimitOf3() {
        Queue queue = config.routingQueueError();
        assertThat(queue.getArguments()).containsEntry("x-delivery-limit", 3);
    }
}
```

- [ ] **Step 2: Run config tests to verify new ones fail**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=RoutingConfigTest
```

Expected: `routingQueueAll_shouldHaveDeliveryLimitOf3` and `routingQueueError_shouldHaveDeliveryLimitOf3` FAIL.

- [ ] **Step 3: Update `RoutingConfig`**

Replace the full content of `RoutingConfig.java` with:

```java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {

    public static final String EXCHANGE_NAME = "routing.direct";
    public static final String QUEUE_ALL = "routing.queue.all";
    public static final String QUEUE_ERROR = "routing.queue.error";
    public static final String KEY_INFO = "info";
    public static final String KEY_WARNING = "warning";
    public static final String KEY_ERROR = "error";
    public static final int MESSAGE_TTL_MS = 5000;
    public static final int DELIVERY_LIMIT = 3;

    @Bean
    public DirectExchange routingExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue routingQueueAll() {
        return QueueBuilder.durable(QUEUE_ALL).quorum().deliveryLimit(DELIVERY_LIMIT).ttl(MESSAGE_TTL_MS).build();
    }

    @Bean
    public Queue routingQueueError() {
        return QueueBuilder.durable(QUEUE_ERROR).quorum().deliveryLimit(DELIVERY_LIMIT).ttl(MESSAGE_TTL_MS).build();
    }

    @Bean
    public Binding bindingAllInfo(DirectExchange routingExchange, Queue routingQueueAll) {
        return BindingBuilder.bind(routingQueueAll).to(routingExchange).with(KEY_INFO);
    }

    @Bean
    public Binding bindingAllWarning(DirectExchange routingExchange, Queue routingQueueAll) {
        return BindingBuilder.bind(routingQueueAll).to(routingExchange).with(KEY_WARNING);
    }

    @Bean
    public Binding bindingAllError(DirectExchange routingExchange, Queue routingQueueAll) {
        return BindingBuilder.bind(routingQueueAll).to(routingExchange).with(KEY_ERROR);
    }

    @Bean
    public Binding bindingErrorOnly(DirectExchange routingExchange, Queue routingQueueError) {
        return BindingBuilder.bind(routingQueueError).to(routingExchange).with(KEY_ERROR);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory routingContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
```

- [ ] **Step 4: Run config tests to verify they pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=RoutingConfigTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Replace `RoutingConsumerTest`**

Replace the full content of `RoutingConsumerTest.java` with:

```java
package com.testingai.rabbitmq.routing;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingConsumerTest {

    @InjectMocks
    private RoutingConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void receiveAll_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.receiveAll("info message", channel, 1L);
            verify(channel).basicAck(1L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void receiveAll_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receiveAll("info message", channel, 1L);
            verify(channel).basicNack(1L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }

    @Test
    void receiveError_shouldAckOnSuccess() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            consumer.receiveError("error message", channel, 2L);
            verify(channel).basicAck(2L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void receiveError_shouldNackWithRequeueOnSimulatedFailure() throws Exception {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            consumer.receiveError("error message", channel, 2L);
            verify(channel).basicNack(2L, false, true);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }
    }
}
```

- [ ] **Step 6: Run consumer test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=RoutingConsumerTest
```

Expected: COMPILATION ERROR — `RoutingConsumer` does not yet call `FailureSimulator.maybeThrow`.

- [ ] **Step 7: Update `RoutingConsumer`**

Replace the full content of `RoutingConsumer.java` with:

```java
package com.testingai.rabbitmq.routing;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.RoutingConfig;
import com.testingai.rabbitmq.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class RoutingConsumer {

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

    @RabbitListener(queues = RoutingConfig.QUEUE_ERROR, containerFactory = "routingContainerFactory")
    public void receiveError(String message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            FailureSimulator.maybeThrow("[RoutingConsumer/ERROR-ONLY]");
            log.info("[RoutingConsumer/ERROR-ONLY] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            log.warn("[RoutingConsumer/ERROR-ONLY] Failed, requeuing for retry: {}", e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException e) {
            throw e;
        }
    }
}
```

- [ ] **Step 8: Run consumer test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=RoutingConsumerTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java
git commit -m "feat: add failure simulation and x-delivery-limit retry to routing pattern"
```

---

## Task 6: Full Test Suite Verification

- [ ] **Step 1: Run all tests**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test
```

Expected: `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`

The 44 tests break down as:
- `FailureSimulatorTest` — 2 new
- `SimpleConsumerTest` — 3 (was 2, +1)
- `WorkQueueConfigTest` — 2 (was 1, +1)
- `WorkQueueConsumerTest` — 4 (unchanged count, new behavior)
- `PubSubConfigTest` — 8 (was 2, +6)
- `PubSubConsumerATest` — 3 (was 2, +1)
- `PubSubConsumerBTest` — 3 (was 2, +1)
- `RoutingConfigTest` — 4 (was 2, +2)
- `RoutingConsumerTest` — 4 (unchanged count, new behavior)
- Existing unchanged: `SimpleQueueConfigTest` (1) + `SimpleProducerTest` (1) + `WorkQueueProducerTest` (2) + `PubSubProducerTest` (1) + `RoutingProducerTest` (2) + `DemoControllerTest` (?) + `RabbitMqDemoApplicationTest` (1) = remaining

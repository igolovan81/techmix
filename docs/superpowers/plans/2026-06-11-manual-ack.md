# Manual Acknowledgment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `AcknowledgeMode.MANUAL` via per-pattern container factories and update every consumer to explicitly `basicAck` / `basicNack`.

**Architecture:** Each existing `*Config` class gains one `SimpleRabbitListenerContainerFactory` bean with `AcknowledgeMode.MANUAL`. Each consumer method gains a `Channel` + delivery-tag header parameter, wraps its body in try/catch, and calls `basicAck` on success or `basicNack(requeue=true)` on failure.

**Tech Stack:** Spring AMQP (`spring-boot-starter-amqp`), RabbitMQ Java client (`com.rabbitmq.client.Channel`), JUnit 5, Mockito.

---

## File Map

| Action | File |
|--------|------|
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java` |

---

## Task 1: Simple Pattern

**Files:**
- Modify: `src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java`
- Modify: `src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java`
- Create: `src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java`

- [ ] **Step 1: Write the failing test**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java`:

```java
package com.testingai.rabbitmq.simple;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleConsumerTest {

    @InjectMocks
    private SimpleConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        consumer.receive("hello", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receive_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.receive("hello", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest=SimpleConsumerTest -pl message-brokers/rabbitmq/spring-demo
```

Expected: **COMPILATION ERROR** — `SimpleConsumer.receive` does not yet accept `Channel` and `deliveryTag` parameters.

- [ ] **Step 3: Add factory bean to `SimpleQueueConfig`**

Replace the full content of `SimpleQueueConfig.java` with:

```java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleQueueConfig {

    public static final String QUEUE_NAME = "simple.queue";

    @Bean
    public Queue simpleQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory simpleContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
```

- [ ] **Step 4: Update `SimpleConsumer`**

Replace the full content of `SimpleConsumer.java` with:

```java
package com.testingai.rabbitmq.simple;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.SimpleQueueConfig;
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
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[SimpleConsumer] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[SimpleConsumer] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest=SimpleConsumerTest -pl message-brokers/rabbitmq/spring-demo
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleConsumerTest.java
git commit -m "feat: add manual ack to simple queue pattern"
```

---

## Task 2: Work Queue Pattern

**Files:**
- Modify: `src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java`
- Modify: `src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java`
- Create: `src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java`

- [ ] **Step 1: Write the failing test**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java`:

```java
package com.testingai.rabbitmq.workqueue;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkQueueConsumerTest {

    @InjectMocks
    private WorkQueueConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void worker1_shouldAckOnSuccess() throws Exception {
        consumer.worker1("task", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void worker1_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.worker1("task", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }

    @Test
    void worker2_shouldAckOnSuccess() throws Exception {
        consumer.worker2("task", channel, 2L);
        verify(channel).basicAck(2L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void worker2_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(2L, false);
        consumer.worker2("task", channel, 2L);
        verify(channel).basicNack(2L, false, true);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest=WorkQueueConsumerTest -pl message-brokers/rabbitmq/spring-demo
```

Expected: **COMPILATION ERROR** — `WorkQueueConsumer` workers do not yet accept `Channel` and `deliveryTag` parameters.

- [ ] **Step 3: Add factory bean to `WorkQueueConfig`**

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

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(QUEUE_NAME).quorum().build();
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

- [ ] **Step 4: Update `WorkQueueConsumer`**

Replace the full content of `WorkQueueConsumer.java` with:

```java
package com.testingai.rabbitmq.workqueue;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.WorkQueueConfig;
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
            simulateWork(message);
            log.info("[Worker1] Done: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[Worker1] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME, containerFactory = "workQueueContainerFactory")
    public void worker2(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException, InterruptedException {
        try {
            log.info("[Worker2] Processing: {}", message);
            simulateWork(message);
            log.info("[Worker2] Done: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[Worker2] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void simulateWork(String message) throws InterruptedException {
        long dots = message.chars().filter(c -> c == '.').count();
        Thread.sleep(dots * 1000);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest=WorkQueueConsumerTest -pl message-brokers/rabbitmq/spring-demo
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumerTest.java
git commit -m "feat: add manual ack to work queue pattern"
```

---

## Task 3: Pub/Sub Pattern

**Files:**
- Modify: `src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java`
- Modify: `src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java`
- Modify: `src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java`
- Create: `src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java`
- Create: `src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java`

- [ ] **Step 1: Write the failing tests**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java`:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerATest {

    @InjectMocks
    private PubSubConsumerA consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        consumer.receive("broadcast", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receive_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.receive("broadcast", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }
}
```

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java`:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerBTest {

    @InjectMocks
    private PubSubConsumerB consumer;

    @Mock
    private Channel channel;

    @Test
    void receive_shouldAckOnSuccess() throws Exception {
        consumer.receive("broadcast", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receive_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.receive("broadcast", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest="PubSubConsumerATest,PubSubConsumerBTest" -pl message-brokers/rabbitmq/spring-demo
```

Expected: **COMPILATION ERROR** — consumers do not yet accept `Channel` and `deliveryTag` parameters.

- [ ] **Step 3: Add factory bean to `PubSubConfig`**

Replace the full content of `PubSubConfig.java` with:

```java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubSubConfig {

    public static final String EXCHANGE_NAME = "pubsub.fanout";
    public static final String QUEUE_A = "pubsub.queue.a";
    public static final String QUEUE_B = "pubsub.queue.b";

    @Bean
    public FanoutExchange pubSubExchange() {
        return new FanoutExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue pubSubQueueA() {
        return new Queue(QUEUE_A, true);
    }

    @Bean
    public Queue pubSubQueueB() {
        return new Queue(QUEUE_B, true);
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

- [ ] **Step 4: Update `PubSubConsumerA`**

Replace the full content of `PubSubConsumerA.java` with:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class PubSubConsumerA {

    @RabbitListener(queues = PubSubConfig.QUEUE_A, containerFactory = "pubSubContainerFactory")
    public void receive(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[PubSubConsumerA] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[PubSubConsumerA] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

- [ ] **Step 5: Update `PubSubConsumerB`**

Replace the full content of `PubSubConsumerB.java` with:

```java
package com.testingai.rabbitmq.pubsub;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class PubSubConsumerB {

    @RabbitListener(queues = PubSubConfig.QUEUE_B, containerFactory = "pubSubContainerFactory")
    public void receive(String message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[PubSubConsumerB] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[PubSubConsumerB] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest="PubSubConsumerATest,PubSubConsumerBTest" -pl message-brokers/rabbitmq/spring-demo
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerATest.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubConsumerBTest.java
git commit -m "feat: add manual ack to pub/sub pattern"
```

---

## Task 4: Routing Pattern

**Files:**
- Modify: `src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java`
- Modify: `src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java`
- Create: `src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java`

- [ ] **Step 1: Write the failing test**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java`:

```java
package com.testingai.rabbitmq.routing;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingConsumerTest {

    @InjectMocks
    private RoutingConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void receiveAll_shouldAckOnSuccess() throws Exception {
        consumer.receiveAll("info message", channel, 1L);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receiveAll_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(1L, false);
        consumer.receiveAll("info message", channel, 1L);
        verify(channel).basicNack(1L, false, true);
    }

    @Test
    void receiveError_shouldAckOnSuccess() throws Exception {
        consumer.receiveError("error message", channel, 2L);
        verify(channel).basicAck(2L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void receiveError_shouldNackWithRequeueOnChannelException() throws Exception {
        doThrow(new IOException("channel error")).when(channel).basicAck(2L, false);
        consumer.receiveError("error message", channel, 2L);
        verify(channel).basicNack(2L, false, true);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest=RoutingConsumerTest -pl message-brokers/rabbitmq/spring-demo
```

Expected: **COMPILATION ERROR** — `RoutingConsumer` methods do not yet accept `Channel` and `deliveryTag` parameters.

- [ ] **Step 3: Add factory bean to `RoutingConfig`**

Replace the full content of `RoutingConfig.java` with:

```java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
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

    @Bean
    public DirectExchange routingExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue routingQueueAll() {
        return new Queue(QUEUE_ALL, true);
    }

    @Bean
    public Queue routingQueueError() {
        return new Queue(QUEUE_ERROR, true);
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

- [ ] **Step 4: Update `RoutingConsumer`**

Replace the full content of `RoutingConsumer.java` with:

```java
package com.testingai.rabbitmq.routing;

import com.rabbitmq.client.Channel;
import com.testingai.rabbitmq.config.RoutingConfig;
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
            log.info("[RoutingConsumer/ALL] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[RoutingConsumer/ALL] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    @RabbitListener(queues = RoutingConfig.QUEUE_ERROR, containerFactory = "routingContainerFactory")
    public void receiveError(String message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("[RoutingConsumer/ERROR-ONLY] Received: {}", message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[RoutingConsumer/ERROR-ONLY] Failed: {}", message, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -Dtest=RoutingConsumerTest -pl message-brokers/rabbitmq/spring-demo
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java \
        message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingConsumerTest.java
git commit -m "feat: add manual ack to routing pattern"
```

---

## Task 5: Full Test Suite Verification

- [ ] **Step 1: Run all tests**

```bash
mvn -f message-brokers/rabbitmq/spring-demo/pom.xml test \
  -pl message-brokers/rabbitmq/spring-demo
```

Expected output (no RabbitMQ connection needed — all tests are unit tests):
```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

The 14 tests break down as:
- `SimpleConsumerTest` — 2
- `WorkQueueConsumerTest` — 4
- `PubSubConsumerATest` — 2
- `PubSubConsumerBTest` — 2
- `RoutingConsumerTest` — 4

> Note: `RabbitMqDemoApplicationTest`, `SimpleProducerTest`, `WorkQueueProducerTest`, `PubSubProducerTest`, `RoutingProducerTest`, and `DemoControllerTest` are unaffected by this change and continue to pass.

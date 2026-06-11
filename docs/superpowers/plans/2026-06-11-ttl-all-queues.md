# TTL All Queues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 5-second `x-message-ttl` to all six queues across the four RabbitMQ patterns by switching queue declarations to `QueueBuilder.ttl()`.

**Architecture:** Each config class gains a `MESSAGE_TTL_MS = 5000` constant and switches `new Queue(...)` calls to `QueueBuilder.durable(...).ttl(MESSAGE_TTL_MS).build()`. The work queue already uses `QueueBuilder` — only `.ttl()` is chained. Four new unit test classes verify the TTL argument on each queue without a broker.

**Tech Stack:** Spring AMQP (`spring-boot-starter-amqp`), JUnit 5, AssertJ.

---

## File Map

| Action | File |
|--------|------|
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/SimpleQueueConfigTest.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java` |
| Modify | `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java` |
| Create | `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java` |

---

## Task 1: Simple Queue TTL

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/SimpleQueueConfigTest.java`

- [ ] **Step 1: Write the failing test**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/SimpleQueueConfigTest.java`:

```java
package com.testingai.rabbitmq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleQueueConfigTest {

    private final SimpleQueueConfig config = new SimpleQueueConfig();

    @Test
    void simpleQueue_shouldHaveTtlOf5000ms() {
        Queue queue = config.simpleQueue();
        assertThat(queue.getArguments()).containsEntry("x-message-ttl", 5000);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=SimpleQueueConfigTest
```

Expected: `FAILED — expected map to contain entry "x-message-ttl"=5000 but could not find it` (or similar AssertJ failure, since the queue currently has no TTL argument).

- [ ] **Step 3: Update `SimpleQueueConfig`**

Replace the full content of `SimpleQueueConfig.java` with:

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
public class SimpleQueueConfig {

    public static final String QUEUE_NAME = "simple.queue";
    public static final int MESSAGE_TTL_MS = 5000;

    @Bean
    public Queue simpleQueue() {
        return QueueBuilder.durable(QUEUE_NAME).ttl(MESSAGE_TTL_MS).build();
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

- [ ] **Step 4: Run test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=SimpleQueueConfigTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/SimpleQueueConfigTest.java
git commit -m "feat: add 5s TTL to simple queue"
```

---

## Task 2: Work Queue TTL

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java`

- [ ] **Step 1: Write the failing test**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java`:

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
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=WorkQueueConfigTest
```

Expected: `FAILED — expected map to contain entry "x-message-ttl"=5000 but could not find it`.

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

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(QUEUE_NAME).quorum().ttl(MESSAGE_TTL_MS).build();
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

- [ ] **Step 4: Run test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=WorkQueueConfigTest
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/WorkQueueConfigTest.java
git commit -m "feat: add 5s TTL to work queue"
```

---

## Task 3: Pub/Sub TTL

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java`

- [ ] **Step 1: Write the failing test**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java`:

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
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=PubSubConfigTest
```

Expected: `FAILED — expected map to contain entry "x-message-ttl"=5000 but could not find it` for both tests.

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
    public static final int MESSAGE_TTL_MS = 5000;

    @Bean
    public FanoutExchange pubSubExchange() {
        return new FanoutExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue pubSubQueueA() {
        return QueueBuilder.durable(QUEUE_A).ttl(MESSAGE_TTL_MS).build();
    }

    @Bean
    public Queue pubSubQueueB() {
        return QueueBuilder.durable(QUEUE_B).ttl(MESSAGE_TTL_MS).build();
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

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=PubSubConfigTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/PubSubConfigTest.java
git commit -m "feat: add 5s TTL to pub/sub queues"
```

---

## Task 4: Routing TTL

**Files:**
- Modify: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java`

- [ ] **Step 1: Write the failing test**

Create `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java`:

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
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=RoutingConfigTest
```

Expected: `FAILED — expected map to contain entry "x-message-ttl"=5000 but could not find it` for both tests.

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

    @Bean
    public DirectExchange routingExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue routingQueueAll() {
        return QueueBuilder.durable(QUEUE_ALL).ttl(MESSAGE_TTL_MS).build();
    }

    @Bean
    public Queue routingQueueError() {
        return QueueBuilder.durable(QUEUE_ERROR).ttl(MESSAGE_TTL_MS).build();
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

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test -Dtest=RoutingConfigTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java \
        message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/config/RoutingConfigTest.java
git commit -m "feat: add 5s TTL to routing queues"
```

---

## Task 5: Full Test Suite Verification

- [ ] **Step 1: Run all tests**

```bash
cd message-brokers/rabbitmq/spring-demo && mvn test
```

Expected: `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`

The 32 tests break down as:
- New config tests: `SimpleQueueConfigTest` (1) + `WorkQueueConfigTest` (1) + `PubSubConfigTest` (2) + `RoutingConfigTest` (2) = **6 new**
- Existing tests from manual-ack and producers: **26 unchanged**

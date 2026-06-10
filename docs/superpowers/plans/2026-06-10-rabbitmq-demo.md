# RabbitMQ Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a 3-node RabbitMQ cluster (Docker Compose) and a single Spring Boot 3.4.4/Java 21/Maven app demonstrating four messaging patterns: simple queue, work queues, pub/sub, and routing.

**Architecture:** One config class per pattern declares queues/exchanges/bindings as Spring `@Bean`s. Each pattern package holds a producer and one or more consumers. A REST controller is the single entry point for triggering demos. The Docker cluster auto-forms via static peer discovery in a shared `rabbitmq.conf`.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring AMQP (`spring-boot-starter-amqp`), Lombok, JUnit 5, Mockito, Docker Compose, RabbitMQ 4 with management plugin.

---

## File Map

**Docker:**
- Create: `message-brokers/rabbitmq/docker/rabbitmq.conf`
- Create: `message-brokers/rabbitmq/docker/docker-compose.yml`

**Spring Boot scaffold:**
- Create: `message-brokers/rabbitmq/spring-demo/pom.xml`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/RabbitMqDemoApplication.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/resources/application.yml`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/RabbitMqDemoApplicationTest.java`

**Simple queue:**
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleProducerTest.java`

**Work queue:**
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueProducerTest.java`

**Pub/Sub:**
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubProducerTest.java`

**Routing:**
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingProducerTest.java`

**Controller:**
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/controller/DemoController.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/controller/DemoControllerTest.java`

---

### Task 1: Docker Cluster

**Files:**
- Create: `message-brokers/rabbitmq/docker/rabbitmq.conf`
- Create: `message-brokers/rabbitmq/docker/docker-compose.yml`

- [ ] **Step 1: Create `rabbitmq.conf`**

```
# message-brokers/rabbitmq/docker/rabbitmq.conf
cluster_formation.peer_discovery_backend = rabbit_peer_discovery_classic_config
cluster_formation.classic_config.nodes.1 = rabbit@rabbitmq1
cluster_formation.classic_config.nodes.2 = rabbit@rabbitmq2
cluster_formation.classic_config.nodes.3 = rabbit@rabbitmq3
vm_memory_high_watermark.relative = 0.4
```

- [ ] **Step 2: Create `docker-compose.yml`**

```yaml
# message-brokers/rabbitmq/docker/docker-compose.yml
services:
  rabbitmq1:
    image: rabbitmq:4-management
    hostname: rabbitmq1
    container_name: rabbitmq1
    environment:
      RABBITMQ_ERLANG_COOKIE: "SWQOKODSQALRPCLNMEQG"
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - ./rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf
    networks:
      - rabbitmq_network
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  rabbitmq2:
    image: rabbitmq:4-management
    hostname: rabbitmq2
    container_name: rabbitmq2
    environment:
      RABBITMQ_ERLANG_COOKIE: "SWQOKODSQALRPCLNMEQG"
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    depends_on:
      rabbitmq1:
        condition: service_healthy
    volumes:
      - ./rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf
    networks:
      - rabbitmq_network

  rabbitmq3:
    image: rabbitmq:4-management
    hostname: rabbitmq3
    container_name: rabbitmq3
    environment:
      RABBITMQ_ERLANG_COOKIE: "SWQOKODSQALRPCLNMEQG"
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    depends_on:
      rabbitmq1:
        condition: service_healthy
    volumes:
      - ./rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf
    networks:
      - rabbitmq_network

networks:
  rabbitmq_network:
    driver: bridge
```

- [ ] **Step 3: Start the cluster and verify all 3 nodes are listed**

```bash
cd message-brokers/rabbitmq/docker
docker compose up -d
# Wait ~30s for nodes to form the cluster, then:
docker exec rabbitmq1 rabbitmqctl cluster_status
```

Expected output contains:
```
Cluster name: rabbit@rabbitmq1
Running Nodes
rabbit@rabbitmq1
rabbit@rabbitmq2
rabbit@rabbitmq3
```

Management UI available at `http://localhost:15672` (guest/guest).

- [ ] **Step 4: Commit**

```bash
git add message-brokers/rabbitmq/docker/
git commit -m "feat: add 3-node RabbitMQ Docker cluster"
```

---

### Task 2: Maven Project Scaffold

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/pom.xml`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/RabbitMqDemoApplication.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/resources/application.yml`
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/RabbitMqDemoApplicationTest.java`

- [ ] **Step 1: Write the scaffold test (it compiles only after the classes exist)**

```java
// message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/RabbitMqDemoApplicationTest.java
package com.testingai.rabbitmq;

import org.junit.jupiter.api.Test;

class RabbitMqDemoApplicationTest {

    @Test
    void mainClassExists() {
        // passes once RabbitMqDemoApplication compiles
        new RabbitMqDemoApplication();
    }
}
```

- [ ] **Step 2: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.4</version>
    </parent>

    <groupId>com.testingai</groupId>
    <artifactId>rabbitmq-demo</artifactId>
    <version>1.0.0</version>
    <name>RabbitMQ Demo</name>
    <description>Learning and demonstration project for RabbitMQ messaging patterns</description>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.38</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>3.4.4</version>
                <configuration>
                    <mainClass>com.testingai.rabbitmq.RabbitMqDemoApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Create `RabbitMqDemoApplication.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/RabbitMqDemoApplication.java
package com.testingai.rabbitmq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RabbitMqDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitMqDemoApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `application.yml`**

```yaml
# message-brokers/rabbitmq/spring-demo/src/main/resources/application.yml
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

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=RabbitMqDemoApplicationTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 6: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/
git commit -m "feat: scaffold Spring Boot RabbitMQ demo project"
```

---

### Task 3: Simple Queue Pattern

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleProducerTest.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java`

- [ ] **Step 1: Write the failing test**

```java
// message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/simple/SimpleProducerTest.java
package com.testingai.rabbitmq.simple;

import com.testingai.rabbitmq.config.SimpleQueueConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private SimpleProducer simpleProducer;

    @Test
    void send_shouldConvertAndSendToSimpleQueue() {
        simpleProducer.send("hello");
        verify(rabbitTemplate).convertAndSend(SimpleQueueConfig.QUEUE_NAME, "hello");
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compilation error — classes don't exist yet)**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=SimpleProducerTest 2>&1 | grep -E "ERROR|FAIL|cannot find"
```

Expected: compilation error mentioning `SimpleProducer` and `SimpleQueueConfig` cannot be found.

- [ ] **Step 3: Create `SimpleQueueConfig.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/SimpleQueueConfig.java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimpleQueueConfig {

    public static final String QUEUE_NAME = "simple.queue";

    @Bean
    public Queue simpleQueue() {
        return new Queue(QUEUE_NAME, true);
    }
}
```

- [ ] **Step 4: Create `SimpleProducer.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleProducer.java
package com.testingai.rabbitmq.simple;

import com.testingai.rabbitmq.config.SimpleQueueConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpleProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(String message) {
        rabbitTemplate.convertAndSend(SimpleQueueConfig.QUEUE_NAME, message);
    }
}
```

- [ ] **Step 5: Create `SimpleConsumer.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/simple/SimpleConsumer.java
package com.testingai.rabbitmq.simple;

import com.testingai.rabbitmq.config.SimpleQueueConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SimpleConsumer {

    @RabbitListener(queues = SimpleQueueConfig.QUEUE_NAME)
    public void receive(String message) {
        log.info("[SimpleConsumer] Received: {}", message);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=SimpleProducerTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 7: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/
git commit -m "feat: add simple queue pattern"
```

---

### Task 4: Work Queue Pattern

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueProducerTest.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java`

- [ ] **Step 1: Write the failing test**

```java
// message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/workqueue/WorkQueueProducerTest.java
package com.testingai.rabbitmq.workqueue;

import com.testingai.rabbitmq.config.WorkQueueConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkQueueProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private WorkQueueProducer workQueueProducer;

    @Test
    void send_shouldSendExactlyCountMessages() {
        workQueueProducer.send("task", 3);
        verify(rabbitTemplate, times(3))
                .convertAndSend(eq(WorkQueueConfig.QUEUE_NAME), anyString());
    }

    @Test
    void send_shouldPrependSequenceNumberToMessage() {
        workQueueProducer.send("task", 1);
        verify(rabbitTemplate).convertAndSend(WorkQueueConfig.QUEUE_NAME, "1: task");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=WorkQueueProducerTest 2>&1 | grep -E "ERROR|FAIL|cannot find"
```

Expected: compilation error — `WorkQueueProducer` and `WorkQueueConfig` not found.

- [ ] **Step 3: Create `WorkQueueConfig.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/WorkQueueConfig.java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkQueueConfig {

    public static final String QUEUE_NAME = "work.queue";

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(QUEUE_NAME).quorum().build();
    }
}
```

- [ ] **Step 4: Create `WorkQueueProducer.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueProducer.java
package com.testingai.rabbitmq.workqueue;

import com.testingai.rabbitmq.config.WorkQueueConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(String message, int count) {
        for (int i = 1; i <= count; i++) {
            rabbitTemplate.convertAndSend(WorkQueueConfig.QUEUE_NAME, i + ": " + message);
        }
    }
}
```

- [ ] **Step 5: Create `WorkQueueConsumer.java`**

Each `@RabbitListener` creates an independent listener container with `prefetch=1` (from `application.yml`), so `worker1` and `worker2` compete fairly for messages. The sleep simulates work proportional to dots in the message body (e.g. `"task.."` sleeps 2 seconds).

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/workqueue/WorkQueueConsumer.java
package com.testingai.rabbitmq.workqueue;

import com.testingai.rabbitmq.config.WorkQueueConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WorkQueueConsumer {

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME)
    public void worker1(String message) throws InterruptedException {
        log.info("[Worker1] Processing: {}", message);
        simulateWork(message);
        log.info("[Worker1] Done: {}", message);
    }

    @RabbitListener(queues = WorkQueueConfig.QUEUE_NAME)
    public void worker2(String message) throws InterruptedException {
        log.info("[Worker2] Processing: {}", message);
        simulateWork(message);
        log.info("[Worker2] Done: {}", message);
    }

    private void simulateWork(String message) throws InterruptedException {
        long dots = message.chars().filter(c -> c == '.').count();
        Thread.sleep(dots * 1000);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=WorkQueueProducerTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/
git commit -m "feat: add work queue pattern with competing consumers"
```

---

### Task 5: Pub/Sub Pattern

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubProducerTest.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java`

- [ ] **Step 1: Write the failing test**

```java
// message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/pubsub/PubSubProducerTest.java
package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PubSubProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PubSubProducer pubSubProducer;

    @Test
    void send_shouldPublishToFanoutExchangeWithEmptyRoutingKey() {
        pubSubProducer.send("broadcast");
        verify(rabbitTemplate).convertAndSend(PubSubConfig.EXCHANGE_NAME, "", "broadcast");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=PubSubProducerTest 2>&1 | grep -E "ERROR|FAIL|cannot find"
```

Expected: compilation error — `PubSubProducer` and `PubSubConfig` not found.

- [ ] **Step 3: Create `PubSubConfig.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/PubSubConfig.java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
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
}
```

- [ ] **Step 4: Create `PubSubProducer.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubProducer.java
package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PubSubProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(String message) {
        rabbitTemplate.convertAndSend(PubSubConfig.EXCHANGE_NAME, "", message);
    }
}
```

- [ ] **Step 5: Create `PubSubConsumerA.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerA.java
package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerA {

    @RabbitListener(queues = PubSubConfig.QUEUE_A)
    public void receive(String message) {
        log.info("[PubSubConsumerA] Received: {}", message);
    }
}
```

- [ ] **Step 6: Create `PubSubConsumerB.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/pubsub/PubSubConsumerB.java
package com.testingai.rabbitmq.pubsub;

import com.testingai.rabbitmq.config.PubSubConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerB {

    @RabbitListener(queues = PubSubConfig.QUEUE_B)
    public void receive(String message) {
        log.info("[PubSubConsumerB] Received: {}", message);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=PubSubProducerTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 8: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/
git commit -m "feat: add pub/sub fanout pattern"
```

---

### Task 6: Routing Pattern

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingProducerTest.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingProducer.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java`

- [ ] **Step 1: Write the failing test**

```java
// message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/routing/RoutingProducerTest.java
package com.testingai.rabbitmq.routing;

import com.testingai.rabbitmq.config.RoutingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoutingProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RoutingProducer routingProducer;

    @Test
    void send_shouldPublishToDirectExchangeWithGivenRoutingKey() {
        routingProducer.send("error", "something broke");
        verify(rabbitTemplate).convertAndSend(RoutingConfig.EXCHANGE_NAME, "error", "something broke");
    }

    @Test
    void send_shouldPublishToDirectExchangeWithInfoKey() {
        routingProducer.send("info", "all good");
        verify(rabbitTemplate).convertAndSend(RoutingConfig.EXCHANGE_NAME, "info", "all good");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=RoutingProducerTest 2>&1 | grep -E "ERROR|FAIL|cannot find"
```

Expected: compilation error — `RoutingProducer` and `RoutingConfig` not found.

- [ ] **Step 3: Create `RoutingConfig.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/config/RoutingConfig.java
package com.testingai.rabbitmq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
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
}
```

- [ ] **Step 4: Create `RoutingProducer.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingProducer.java
package com.testingai.rabbitmq.routing;

import com.testingai.rabbitmq.config.RoutingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutingProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(String routingKey, String message) {
        rabbitTemplate.convertAndSend(RoutingConfig.EXCHANGE_NAME, routingKey, message);
    }
}
```

- [ ] **Step 5: Create `RoutingConsumer.java`**

`receiveAll` listens on `routing.queue.all` (bound to info + warning + error). `receiveError` listens on `routing.queue.error` (bound to error only). Sending an `error` message will log in both consumers; sending `info` or `warning` logs only in `receiveAll`.

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/routing/RoutingConsumer.java
package com.testingai.rabbitmq.routing;

import com.testingai.rabbitmq.config.RoutingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RoutingConsumer {

    @RabbitListener(queues = RoutingConfig.QUEUE_ALL)
    public void receiveAll(String message) {
        log.info("[RoutingConsumer/ALL] Received: {}", message);
    }

    @RabbitListener(queues = RoutingConfig.QUEUE_ERROR)
    public void receiveError(String message) {
        log.info("[RoutingConsumer/ERROR-ONLY] Received: {}", message);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=RoutingProducerTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/
git commit -m "feat: add routing direct exchange pattern"
```

---

### Task 7: REST Controller

**Files:**
- Create: `message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/controller/DemoControllerTest.java`
- Create: `message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/controller/DemoController.java`

- [ ] **Step 1: Write the failing test**

`@WebMvcTest` loads only the web layer; no AMQP auto-configuration is triggered. All four producers are mocked with `@MockitoBean` (available in Spring Boot 3.4+).

```java
// message-brokers/rabbitmq/spring-demo/src/test/java/com/testingai/rabbitmq/controller/DemoControllerTest.java
package com.testingai.rabbitmq.controller;

import com.testingai.rabbitmq.pubsub.PubSubProducer;
import com.testingai.rabbitmq.routing.RoutingProducer;
import com.testingai.rabbitmq.simple.SimpleProducer;
import com.testingai.rabbitmq.workqueue.WorkQueueProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimpleProducer simpleProducer;

    @MockitoBean
    private WorkQueueProducer workQueueProducer;

    @MockitoBean
    private PubSubProducer pubSubProducer;

    @MockitoBean
    private RoutingProducer routingProducer;

    @Test
    void simple_shouldReturn200AndDelegateSend() throws Exception {
        mockMvc.perform(post("/demo/simple").param("message", "hello"))
                .andExpect(status().isOk());
        verify(simpleProducer).send("hello");
    }

    @Test
    void work_shouldReturn200AndDelegateSendWithDefaultCount() throws Exception {
        mockMvc.perform(post("/demo/work").param("message", "task"))
                .andExpect(status().isOk());
        verify(workQueueProducer).send("task", 5);
    }

    @Test
    void work_shouldPassExplicitCount() throws Exception {
        mockMvc.perform(post("/demo/work").param("message", "task..").param("count", "3"))
                .andExpect(status().isOk());
        verify(workQueueProducer).send("task..", 3);
    }

    @Test
    void pubsub_shouldReturn200AndDelegateSend() throws Exception {
        mockMvc.perform(post("/demo/pubsub").param("message", "broadcast"))
                .andExpect(status().isOk());
        verify(pubSubProducer).send("broadcast");
    }

    @Test
    void routing_shouldReturn200AndDelegateSend() throws Exception {
        mockMvc.perform(post("/demo/routing").param("key", "error").param("message", "boom"))
                .andExpect(status().isOk());
        verify(routingProducer).send("error", "boom");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test -Dtest=DemoControllerTest 2>&1 | grep -E "ERROR|FAIL|cannot find"
```

Expected: compilation error — `DemoController` not found.

- [ ] **Step 3: Create `DemoController.java`**

```java
// message-brokers/rabbitmq/spring-demo/src/main/java/com/testingai/rabbitmq/controller/DemoController.java
package com.testingai.rabbitmq.controller;

import com.testingai.rabbitmq.pubsub.PubSubProducer;
import com.testingai.rabbitmq.routing.RoutingProducer;
import com.testingai.rabbitmq.simple.SimpleProducer;
import com.testingai.rabbitmq.workqueue.WorkQueueProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final SimpleProducer simpleProducer;
    private final WorkQueueProducer workQueueProducer;
    private final PubSubProducer pubSubProducer;
    private final RoutingProducer routingProducer;

    @PostMapping("/simple")
    public ResponseEntity<String> simple(@RequestParam String message) {
        simpleProducer.send(message);
        return ResponseEntity.ok("Sent to simple.queue: " + message);
    }

    @PostMapping("/work")
    public ResponseEntity<String> work(
            @RequestParam String message,
            @RequestParam(defaultValue = "5") int count) {
        workQueueProducer.send(message, count);
        return ResponseEntity.ok("Sent " + count + " messages to work.queue");
    }

    @PostMapping("/pubsub")
    public ResponseEntity<String> pubsub(@RequestParam String message) {
        pubSubProducer.send(message);
        return ResponseEntity.ok("Broadcast to pubsub.fanout: " + message);
    }

    @PostMapping("/routing")
    public ResponseEntity<String> routing(
            @RequestParam String key,
            @RequestParam String message) {
        routingProducer.send(key, message);
        return ResponseEntity.ok("Routed to routing.direct with key=" + key + ": " + message);
    }
}
```

- [ ] **Step 4: Run all tests to verify everything passes**

```bash
cd message-brokers/rabbitmq/spring-demo
mvn test
```

Expected: `BUILD SUCCESS`, all tests passed (10 tests total: 1 scaffold + 1 simple + 2 work + 1 pubsub + 2 routing + 5 controller - note: count may vary by exact test methods).

- [ ] **Step 5: Commit**

```bash
git add message-brokers/rabbitmq/spring-demo/src/
git commit -m "feat: add REST controller wiring all four messaging patterns"
```

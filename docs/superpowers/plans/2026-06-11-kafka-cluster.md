# Kafka Cluster & Spring Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a 3-broker KRaft Kafka cluster (Docker) and a Spring Boot demo app demonstrating 7 messaging patterns, mirroring the existing `message-brokers/rabbitmq/` project structure.

**Architecture:** Three `confluentinc/cp-kafka:7.8.0` containers in KRaft combined mode (broker + controller, no ZooKeeper) share a Docker bridge network with a `provectuslabs/kafka-ui` management console. A single Spring Boot 3.4.4 app exposes REST endpoints for all 7 patterns — simple, work queue, pub/sub, partitioning, transactions, compaction, Kafka Streams — organized per-pattern package, identical to the RabbitMQ demo layout.

**Tech Stack:** Java 21, Spring Boot 3.4.4, spring-kafka, kafka-streams, spring-kafka-test (EmbeddedKafka), Mockito, Lombok, springdoc-openapi 2.8.6, Gatling 3.13.1, Docker Compose.

---

## File Map

```
message-brokers/kafka/
├── docker/
│   └── docker-compose.yml
└── spring-demo/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/testingai/kafka/
        │   │   ├── KafkaDemoApplication.java
        │   │   ├── config/
        │   │   │   ├── TopicConfig.java          — NewTopic beans + container factories
        │   │   │   └── KafkaStreamsConfig.java    — word-count topology + @EnableKafkaStreams
        │   │   ├── controller/
        │   │   │   └── DemoController.java        — 7 REST endpoints
        │   │   ├── simple/
        │   │   │   ├── SimpleProducer.java
        │   │   │   └── SimpleConsumer.java
        │   │   ├── workqueue/
        │   │   │   ├── WorkQueueProducer.java
        │   │   │   └── WorkQueueConsumer.java     — worker1 + worker2 listeners
        │   │   ├── pubsub/
        │   │   │   ├── PubSubProducer.java
        │   │   │   ├── PubSubConsumerA.java       — group-a
        │   │   │   └── PubSubConsumerB.java       — group-b
        │   │   ├── partitioning/
        │   │   │   ├── PartitioningProducer.java  — sends with explicit key
        │   │   │   └── PartitioningConsumer.java  — logs key + partition
        │   │   ├── transactions/
        │   │   │   ├── TransactionalProducer.java — uses transactionalKafkaTemplate
        │   │   │   └── TransactionalConsumer.java — isolation.level=read_committed
        │   │   ├── compaction/
        │   │   │   ├── CompactionProducer.java
        │   │   │   └── CompactionConsumer.java
        │   │   ├── streams/
        │   │   │   ├── StreamsProducer.java
        │   │   │   └── WordCountConsumer.java
        │   │   └── util/
        │   │       └── FailureSimulator.java
        │   └── resources/
        │       └── application.yml
        └── test/
            ├── java/com/testingai/kafka/
            │   ├── KafkaDemoApplicationTest.java
            │   ├── config/
            │   │   └── TopicConfigTest.java
            │   ├── controller/
            │   │   └── DemoControllerTest.java
            │   ├── simple/
            │   │   ├── SimpleProducerTest.java
            │   │   └── SimpleConsumerTest.java
            │   ├── workqueue/
            │   │   ├── WorkQueueProducerTest.java
            │   │   └── WorkQueueConsumerTest.java
            │   ├── pubsub/
            │   │   ├── PubSubProducerTest.java
            │   │   ├── PubSubConsumerATest.java
            │   │   └── PubSubConsumerBTest.java
            │   ├── partitioning/
            │   │   ├── PartitioningProducerTest.java
            │   │   └── PartitioningConsumerTest.java
            │   ├── transactions/
            │   │   ├── TransactionalProducerTest.java
            │   │   └── TransactionalConsumerTest.java
            │   ├── compaction/
            │   │   ├── CompactionProducerTest.java
            │   │   └── CompactionConsumerTest.java
            │   ├── streams/
            │   │   ├── StreamsProducerTest.java
            │   │   └── WordCountConsumerTest.java
            │   ├── util/
            │   │   └── FailureSimulatorTest.java
            │   └── performance/
            │       └── DemoSimulation.java
            └── resources/
                └── application.yml                — overrides replication-factor: 1
```

---

### Task 1: Docker Cluster

**Files:**
- Create: `message-brokers/kafka/docker/docker-compose.yml`

- [ ] **Step 1: Create the docker-compose.yml**

```yaml
# message-brokers/kafka/docker/docker-compose.yml
services:
  kafka1:
    image: confluentinc/cp-kafka:7.8.0
    hostname: kafka1
    container_name: kafka1
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: INTERNAL://kafka1:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://kafka1:9093
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka1:29092,EXTERNAL://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka1:9093,2@kafka2:9093,3@kafka3:9093
      CLUSTER_ID: 4L6g3nShT-eMCtK--X86sw
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_LOG_DIRS: /var/lib/kafka/data
    networks:
      - kafka_network
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 5s
      retries: 10

  kafka2:
    image: confluentinc/cp-kafka:7.8.0
    hostname: kafka2
    container_name: kafka2
    ports:
      - "9093:9092"
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: INTERNAL://kafka2:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://kafka2:9093
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka2:29092,EXTERNAL://localhost:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka1:9093,2@kafka2:9093,3@kafka3:9093
      CLUSTER_ID: 4L6g3nShT-eMCtK--X86sw
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_LOG_DIRS: /var/lib/kafka/data
    networks:
      - kafka_network

  kafka3:
    image: confluentinc/cp-kafka:7.8.0
    hostname: kafka3
    container_name: kafka3
    ports:
      - "9094:9092"
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: INTERNAL://kafka3:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://kafka3:9093
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka3:29092,EXTERNAL://localhost:9094
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka1:9093,2@kafka2:9093,3@kafka3:9093
      CLUSTER_ID: 4L6g3nShT-eMCtK--X86sw
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_LOG_DIRS: /var/lib/kafka/data
    networks:
      - kafka_network

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka1:29092,kafka2:29092,kafka3:29092
    depends_on:
      kafka1:
        condition: service_healthy
    networks:
      - kafka_network

networks:
  kafka_network:
    driver: bridge
```

- [ ] **Step 2: Start the cluster and verify**

```bash
cd message-brokers/kafka/docker
docker compose up -d
```

Wait ~30 seconds, then:

```bash
docker compose ps
```

Expected: all 4 services UP, kafka1 healthy.

```bash
docker exec kafka1 kafka-broker-api-versions --bootstrap-server localhost:9092
```

Expected: prints API version list with no errors.

- [ ] **Step 3: Open Kafka UI**

Open http://localhost:8090 in a browser. Expected: "local" cluster visible with 3 brokers listed.

- [ ] **Step 4: Stop the cluster**

```bash
docker compose down
```

- [ ] **Step 5: Commit**

```bash
git add message-brokers/kafka/docker/docker-compose.yml
git commit -m "feat: add 3-broker KRaft Kafka cluster with Kafka UI"
```

---

### Task 2: Spring Boot Project Skeleton

**Files:**
- Create: `message-brokers/kafka/spring-demo/pom.xml`
- Create: `message-brokers/kafka/spring-demo/src/main/java/com/testingai/kafka/KafkaDemoApplication.java`
- Create: `message-brokers/kafka/spring-demo/src/main/resources/application.yml`
- Create: `message-brokers/kafka/spring-demo/src/test/java/com/testingai/kafka/KafkaDemoApplicationTest.java`
- Create: `message-brokers/kafka/spring-demo/src/test/resources/application.yml`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/testingai/kafka/KafkaDemoApplicationTest.java
package com.testingai.kafka;

import org.junit.jupiter.api.Test;

class KafkaDemoApplicationTest {

    @Test
    void mainClassExists() {
        new KafkaDemoApplication();
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cd message-brokers/kafka/spring-demo
mvn test -Dtest=KafkaDemoApplicationTest
```

Expected: FAIL — `KafkaDemoApplication` does not exist yet.

- [ ] **Step 3: Create pom.xml**

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
    <artifactId>kafka-demo</artifactId>
    <version>1.0.0</version>
    <name>Kafka Demo</name>
    <description>Learning and demonstration project for Apache Kafka messaging patterns</description>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <gatling.version>3.13.1</gatling.version>
        <gatling-maven-plugin.version>4.15.0</gatling-maven-plugin.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.kafka</groupId>
            <artifactId>kafka-streams</artifactId>
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
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.6</version>
        </dependency>
        <dependency>
            <groupId>io.gatling.highcharts</groupId>
            <artifactId>gatling-charts-highcharts</artifactId>
            <version>${gatling.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.38</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-Dnet.bytebuddy.experimental=true</argLine>
                    <excludes>
                        <exclude>**/DemoSimulation.java</exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.kafka.KafkaDemoApplication</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>io.gatling</groupId>
                <artifactId>gatling-maven-plugin</artifactId>
                <version>${gatling-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create the main application class**

```java
// src/main/java/com/testingai/kafka/KafkaDemoApplication.java
package com.testingai.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaDemoApplication.class, args);
    }
}
```

- [ ] **Step 5: Create application.yml**

```yaml
# src/main/resources/application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    consumer:
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    streams:
      application-id: kafka-demo-streams
      default-key-serde: org.apache.kafka.common.serialization.Serdes$StringSerde
      default-value-serde: org.apache.kafka.common.serialization.Serdes$StringSerde
      properties:
        commit.interval.ms: 1000

kafka:
  demo:
    replication-factor: 3
```

- [ ] **Step 6: Create test application.yml**

```yaml
# src/test/resources/application.yml
kafka:
  demo:
    replication-factor: 1
```

- [ ] **Step 7: Run the test — should pass now**

```bash
mvn test -Dtest=KafkaDemoApplicationTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add message-brokers/kafka/spring-demo/
git commit -m "feat: add Kafka Spring Boot project skeleton"
```

---

### Task 3: TopicConfig, FailureSimulator, and Container Factories

**Files:**
- Create: `src/main/java/com/testingai/kafka/config/TopicConfig.java`
- Create: `src/main/java/com/testingai/kafka/util/FailureSimulator.java`
- Create: `src/test/java/com/testingai/kafka/config/TopicConfigTest.java`
- Create: `src/test/java/com/testingai/kafka/util/FailureSimulatorTest.java`

All paths below are relative to `message-brokers/kafka/spring-demo/`.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/config/TopicConfigTest.java
package com.testingai.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TopicConfigTest {

    private TopicConfig config;

    @BeforeEach
    void setUp() {
        config = new TopicConfig();
        ReflectionTestUtils.setField(config, "replicationFactor", 1);
    }

    @Test
    void simpleTopic_hasOnePartition() {
        NewTopic topic = config.simpleTopic();
        assertThat(topic.name()).isEqualTo("simple.topic");
        assertThat(topic.numPartitions()).isEqualTo(1);
    }

    @Test
    void workTopic_hasThreePartitions() {
        NewTopic topic = config.workTopic();
        assertThat(topic.name()).isEqualTo("work.topic");
        assertThat(topic.numPartitions()).isEqualTo(3);
    }

    @Test
    void compactedTopic_hasCompactPolicy() {
        NewTopic topic = config.compactedTopic();
        assertThat(topic.configs()).containsEntry("cleanup.policy", "compact");
    }
}
```

```java
// src/test/java/com/testingai/kafka/util/FailureSimulatorTest.java
package com.testingai.kafka.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class FailureSimulatorTest {

    @Test
    void maybeThrow_doesNotThrowMostOfTheTime() {
        int failures = 0;
        for (int i = 0; i < 1000; i++) {
            try {
                FailureSimulator.maybeThrow("test");
            } catch (RuntimeException e) {
                failures++;
            }
        }
        // With 5% failure rate, expect roughly 50 failures; accept 10-150 range
        assertThat(failures).isBetween(5, 200);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=TopicConfigTest,FailureSimulatorTest
```

Expected: FAIL — classes do not exist.

- [ ] **Step 3: Create FailureSimulator**

```java
// src/main/java/com/testingai/kafka/util/FailureSimulator.java
package com.testingai.kafka.util;

public class FailureSimulator {

    private static final double FAILURE_RATE = 0.05;

    private FailureSimulator() {}

    public static void maybeThrow(String context) {
        if (Math.random() < FAILURE_RATE) {
            throw new RuntimeException("Simulated 5% failure in " + context);
        }
    }
}
```

- [ ] **Step 4: Create TopicConfig**

```java
// src/main/java/com/testingai/kafka/config/TopicConfig.java
package com.testingai.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class TopicConfig {

    public static final String SIMPLE_TOPIC = "simple.topic";
    public static final String WORK_TOPIC = "work.topic";
    public static final String PUBSUB_TOPIC = "pubsub.topic";
    public static final String PARTITION_TOPIC = "partition.topic";
    public static final String TX_OUTPUT_TOPIC = "tx-output.topic";
    public static final String COMPACTED_TOPIC = "compacted.topic";
    public static final String STREAMS_INPUT_TOPIC = "streams-input.topic";
    public static final String STREAMS_WORDCOUNT_OUTPUT = "streams-wordcount-output";

    @Value("${kafka.demo.replication-factor:3}")
    private int replicationFactor;

    // ── Topic declarations ──────────────────────────────────────────────────

    @Bean
    public org.apache.kafka.clients.admin.NewTopic simpleTopic() {
        return TopicBuilder.name(SIMPLE_TOPIC).partitions(1).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic workTopic() {
        return TopicBuilder.name(WORK_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic pubSubTopic() {
        return TopicBuilder.name(PUBSUB_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic partitionTopic() {
        return TopicBuilder.name(PARTITION_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic txOutputTopic() {
        return TopicBuilder.name(TX_OUTPUT_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic compactedTopic() {
        return TopicBuilder.name(COMPACTED_TOPIC)
                .partitions(1)
                .replicas(replicationFactor)
                .config("cleanup.policy", "compact")
                .config("segment.ms", "5000")
                .config("min.cleanable.dirty.ratio", "0.01")
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic streamsInputTopic() {
        return TopicBuilder.name(STREAMS_INPUT_TOPIC).partitions(3).replicas(replicationFactor).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic streamsWordcountOutput() {
        return TopicBuilder.name(STREAMS_WORDCOUNT_OUTPUT).partitions(3).replicas(replicationFactor).build();
    }

    // ── Default listener container factory (with retry) ────────────────────

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @org.springframework.beans.factory.annotation.Qualifier("kafkaConsumerFactory")
            ConsumerFactory<String, String> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(500L, 2L)));
        return factory;
    }

    // ── Transactional producer (exactly-once writes) ────────────────────────

    @Bean
    public DefaultKafkaProducerFactory<String, String> transactionalProducerFactory(
            KafkaProperties kafkaProperties) {
        var factory = new DefaultKafkaProducerFactory<String, String>(
                kafkaProperties.buildProducerProperties(null));
        factory.setTransactionIdPrefix("tx-demo-");
        return factory;
    }

    @Bean
    public KafkaTemplate<String, String> transactionalKafkaTemplate(
            DefaultKafkaProducerFactory<String, String> transactionalProducerFactory) {
        return new KafkaTemplate<>(transactionalProducerFactory);
    }

    // ── read_committed consumer factory (for transactional consumer) ────────

    @Bean
    public ConsumerFactory<String, String> readCommittedConsumerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> transactionalContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @org.springframework.beans.factory.annotation.Qualifier("readCommittedConsumerFactory")
            ConsumerFactory<String, String> readCommittedConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        configurer.configure(factory, readCommittedConsumerFactory);
        return factory;
    }
}
```

- [ ] **Step 5: Run tests — should pass**

```bash
mvn test -Dtest=TopicConfigTest,FailureSimulatorTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/testingai/kafka/config/TopicConfig.java \
        src/main/java/com/testingai/kafka/util/FailureSimulator.java \
        src/test/java/com/testingai/kafka/config/TopicConfigTest.java \
        src/test/java/com/testingai/kafka/util/FailureSimulatorTest.java \
        src/test/resources/application.yml
git commit -m "feat: add TopicConfig, FailureSimulator, and container factories"
```

---

### Task 4: Simple Pattern

**Files:**
- Create: `src/main/java/com/testingai/kafka/simple/SimpleProducer.java`
- Create: `src/main/java/com/testingai/kafka/simple/SimpleConsumer.java`
- Create: `src/test/java/com/testingai/kafka/simple/SimpleProducerTest.java`
- Create: `src/test/java/com/testingai/kafka/simple/SimpleConsumerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/simple/SimpleProducerTest.java
package com.testingai.kafka.simple;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private SimpleProducer producer;

    @Test
    void send_shouldSendMessageToSimpleTopic() {
        producer.send("hello");
        verify(kafkaTemplate).send(TopicConfig.SIMPLE_TOPIC, "hello");
    }
}
```

```java
// src/test/java/com/testingai/kafka/simple/SimpleConsumerTest.java
package com.testingai.kafka.simple;

import com.testingai.kafka.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class SimpleConsumerTest {

    @InjectMocks
    private SimpleConsumer consumer;

    @Test
    void receive_shouldNotThrowOnSuccess() {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            assertThatCode(() -> consumer.receive("hello")).doesNotThrowAnyException();
        }
    }

    @Test
    void receive_shouldPropagateExceptionOnSimulatedFailure() {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            assertThatThrownBy(() -> consumer.receive("hello"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Simulated");
        }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=SimpleProducerTest,SimpleConsumerTest
```

Expected: FAIL — classes do not exist.

- [ ] **Step 3: Implement SimpleProducer**

```java
// src/main/java/com/testingai/kafka/simple/SimpleProducer.java
package com.testingai.kafka.simple;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpleProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message) {
        kafkaTemplate.send(TopicConfig.SIMPLE_TOPIC, message);
    }
}
```

- [ ] **Step 4: Implement SimpleConsumer**

```java
// src/main/java/com/testingai/kafka/simple/SimpleConsumer.java
package com.testingai.kafka.simple;

import com.testingai.kafka.config.TopicConfig;
import com.testingai.kafka.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SimpleConsumer {

    @KafkaListener(topics = TopicConfig.SIMPLE_TOPIC, groupId = "simple-group")
    public void receive(String message) {
        FailureSimulator.maybeThrow("[SimpleConsumer]");
        log.info("[SimpleConsumer] Received: {}", message);
    }
}
```

- [ ] **Step 5: Run tests — should pass**

```bash
mvn test -Dtest=SimpleProducerTest,SimpleConsumerTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/testingai/kafka/simple/ \
        src/test/java/com/testingai/kafka/simple/
git commit -m "feat: add simple producer/consumer pattern"
```

---

### Task 5: Work Queue Pattern

**Files:**
- Create: `src/main/java/com/testingai/kafka/workqueue/WorkQueueProducer.java`
- Create: `src/main/java/com/testingai/kafka/workqueue/WorkQueueConsumer.java`
- Create: `src/test/java/com/testingai/kafka/workqueue/WorkQueueProducerTest.java`
- Create: `src/test/java/com/testingai/kafka/workqueue/WorkQueueConsumerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/workqueue/WorkQueueProducerTest.java
package com.testingai.kafka.workqueue;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkQueueProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private WorkQueueProducer producer;

    @Test
    void send_shouldSendCountMessagesToWorkTopic() {
        producer.send("task", 3);
        verify(kafkaTemplate, times(3)).send(TopicConfig.WORK_TOPIC, "task");
    }
}
```

```java
// src/test/java/com/testingai/kafka/workqueue/WorkQueueConsumerTest.java
package com.testingai.kafka.workqueue;

import com.testingai.kafka.util.FailureSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class WorkQueueConsumerTest {

    @InjectMocks
    private WorkQueueConsumer consumer;

    @Test
    void worker1_shouldNotThrowOnSuccess() throws InterruptedException {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            assertThatCode(() -> consumer.worker1("task")).doesNotThrowAnyException();
        }
    }

    @Test
    void worker1_shouldPropagateExceptionOnSimulatedFailure() {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString()))
                    .thenThrow(new RuntimeException("Simulated"));
            assertThatThrownBy(() -> consumer.worker1("task"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void worker2_shouldNotThrowOnSuccess() throws InterruptedException {
        try (MockedStatic<FailureSimulator> mock = mockStatic(FailureSimulator.class)) {
            mock.when(() -> FailureSimulator.maybeThrow(anyString())).thenAnswer(inv -> null);
            assertThatCode(() -> consumer.worker2("task")).doesNotThrowAnyException();
        }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=WorkQueueProducerTest,WorkQueueConsumerTest
```

Expected: FAIL — classes do not exist.

- [ ] **Step 3: Implement WorkQueueProducer**

```java
// src/main/java/com/testingai/kafka/workqueue/WorkQueueProducer.java
package com.testingai.kafka.workqueue;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkQueueProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message, int count) {
        for (int i = 0; i < count; i++) {
            kafkaTemplate.send(TopicConfig.WORK_TOPIC, message);
        }
    }
}
```

- [ ] **Step 4: Implement WorkQueueConsumer**

```java
// src/main/java/com/testingai/kafka/workqueue/WorkQueueConsumer.java
package com.testingai.kafka.workqueue;

import com.testingai.kafka.config.TopicConfig;
import com.testingai.kafka.util.FailureSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WorkQueueConsumer {

    @KafkaListener(topics = TopicConfig.WORK_TOPIC, groupId = "work-group", id = "worker1")
    public void worker1(String message) throws InterruptedException {
        log.info("[Worker1] Processing: {}", message);
        FailureSimulator.maybeThrow("[Worker1]");
        simulateWork(message);
        log.info("[Worker1] Done: {}", message);
    }

    @KafkaListener(topics = TopicConfig.WORK_TOPIC, groupId = "work-group", id = "worker2")
    public void worker2(String message) throws InterruptedException {
        log.info("[Worker2] Processing: {}", message);
        FailureSimulator.maybeThrow("[Worker2]");
        simulateWork(message);
        log.info("[Worker2] Done: {}", message);
    }

    private void simulateWork(String message) throws InterruptedException {
        long dots = message.chars().filter(c -> c == '.').count();
        Thread.sleep(dots * 1000);
    }
}
```

- [ ] **Step 5: Run tests — should pass**

```bash
mvn test -Dtest=WorkQueueProducerTest,WorkQueueConsumerTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/testingai/kafka/workqueue/ \
        src/test/java/com/testingai/kafka/workqueue/
git commit -m "feat: add work queue producer/consumer pattern"
```

---

### Task 6: Pub/Sub Pattern

**Files:**
- Create: `src/main/java/com/testingai/kafka/pubsub/PubSubProducer.java`
- Create: `src/main/java/com/testingai/kafka/pubsub/PubSubConsumerA.java`
- Create: `src/main/java/com/testingai/kafka/pubsub/PubSubConsumerB.java`
- Create: `src/test/java/com/testingai/kafka/pubsub/PubSubProducerTest.java`
- Create: `src/test/java/com/testingai/kafka/pubsub/PubSubConsumerATest.java`
- Create: `src/test/java/com/testingai/kafka/pubsub/PubSubConsumerBTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/pubsub/PubSubProducerTest.java
package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PubSubProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private PubSubProducer producer;

    @Test
    void send_shouldBroadcastToPubSubTopic() {
        producer.send("broadcast");
        verify(kafkaTemplate).send(TopicConfig.PUBSUB_TOPIC, "broadcast");
    }
}
```

```java
// src/test/java/com/testingai/kafka/pubsub/PubSubConsumerATest.java
package com.testingai.kafka.pubsub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerATest {

    @InjectMocks
    private PubSubConsumerA consumer;

    @Test
    void receive_shouldNotThrow() {
        assertThatCode(() -> consumer.receive("broadcast")).doesNotThrowAnyException();
    }
}
```

```java
// src/test/java/com/testingai/kafka/pubsub/PubSubConsumerBTest.java
package com.testingai.kafka.pubsub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class PubSubConsumerBTest {

    @InjectMocks
    private PubSubConsumerB consumer;

    @Test
    void receive_shouldNotThrow() {
        assertThatCode(() -> consumer.receive("broadcast")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=PubSubProducerTest,PubSubConsumerATest,PubSubConsumerBTest
```

- [ ] **Step 3: Implement the three classes**

```java
// src/main/java/com/testingai/kafka/pubsub/PubSubProducer.java
package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PubSubProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message) {
        kafkaTemplate.send(TopicConfig.PUBSUB_TOPIC, message);
    }
}
```

```java
// src/main/java/com/testingai/kafka/pubsub/PubSubConsumerA.java
package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerA {

    @KafkaListener(topics = TopicConfig.PUBSUB_TOPIC, groupId = "group-a")
    public void receive(String message) {
        log.info("[PubSubConsumerA] Received: {}", message);
    }
}
```

```java
// src/main/java/com/testingai/kafka/pubsub/PubSubConsumerB.java
package com.testingai.kafka.pubsub;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PubSubConsumerB {

    @KafkaListener(topics = TopicConfig.PUBSUB_TOPIC, groupId = "group-b")
    public void receive(String message) {
        log.info("[PubSubConsumerB] Received: {}", message);
    }
}
```

- [ ] **Step 4: Run tests — should pass**

```bash
mvn test -Dtest=PubSubProducerTest,PubSubConsumerATest,PubSubConsumerBTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/testingai/kafka/pubsub/ \
        src/test/java/com/testingai/kafka/pubsub/
git commit -m "feat: add pub/sub producer and dual consumer-group pattern"
```

---

### Task 7: Partitioning Pattern

**Files:**
- Create: `src/main/java/com/testingai/kafka/partitioning/PartitioningProducer.java`
- Create: `src/main/java/com/testingai/kafka/partitioning/PartitioningConsumer.java`
- Create: `src/test/java/com/testingai/kafka/partitioning/PartitioningProducerTest.java`
- Create: `src/test/java/com/testingai/kafka/partitioning/PartitioningConsumerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/partitioning/PartitioningProducerTest.java
package com.testingai.kafka.partitioning;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartitioningProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private PartitioningProducer producer;

    @Test
    void send_shouldSendWithKeyToPartitionTopic() {
        producer.send("error", "something broke");
        verify(kafkaTemplate).send(TopicConfig.PARTITION_TOPIC, "error", "something broke");
    }
}
```

```java
// src/test/java/com/testingai/kafka/partitioning/PartitioningConsumerTest.java
package com.testingai.kafka.partitioning;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class PartitioningConsumerTest {

    @InjectMocks
    private PartitioningConsumer consumer;

    @Test
    void receive_shouldNotThrow() {
        var record = new ConsumerRecord<>("partition.topic", 1, 0L, "error", "something broke");
        assertThatCode(() -> consumer.receive(record)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=PartitioningProducerTest,PartitioningConsumerTest
```

- [ ] **Step 3: Implement both classes**

```java
// src/main/java/com/testingai/kafka/partitioning/PartitioningProducer.java
package com.testingai.kafka.partitioning;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PartitioningProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String key, String message) {
        kafkaTemplate.send(TopicConfig.PARTITION_TOPIC, key, message);
    }
}
```

```java
// src/main/java/com/testingai/kafka/partitioning/PartitioningConsumer.java
package com.testingai.kafka.partitioning;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PartitioningConsumer {

    @KafkaListener(topics = TopicConfig.PARTITION_TOPIC, groupId = "partition-group")
    public void receive(ConsumerRecord<String, String> record) {
        log.info("[PartitioningConsumer] key={} partition={} value={}",
                record.key(), record.partition(), record.value());
    }
}
```

- [ ] **Step 4: Run tests — should pass**

```bash
mvn test -Dtest=PartitioningProducerTest,PartitioningConsumerTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/testingai/kafka/partitioning/ \
        src/test/java/com/testingai/kafka/partitioning/
git commit -m "feat: add key-based partitioning producer/consumer pattern"
```

---

### Task 8: Transactions Pattern (Exactly-Once)

**Files:**
- Create: `src/main/java/com/testingai/kafka/transactions/TransactionalProducer.java`
- Create: `src/main/java/com/testingai/kafka/transactions/TransactionalConsumer.java`
- Create: `src/test/java/com/testingai/kafka/transactions/TransactionalProducerTest.java`
- Create: `src/test/java/com/testingai/kafka/transactions/TransactionalConsumerTest.java`

`TransactionalProducer` injects `transactionalKafkaTemplate` (declared in `TopicConfig`). It wraps a batch of sends in `executeInTransaction()` — all succeed or none are visible to `read_committed` consumers. `TransactionalConsumer` uses `containerFactory = "transactionalContainerFactory"` so it reads with `isolation.level=read_committed`.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/transactions/TransactionalProducerTest.java
package com.testingai.kafka.transactions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalProducerTest {

    @Mock
    private KafkaTemplate<String, String> transactionalKafkaTemplate;

    @InjectMocks
    private TransactionalProducer producer;

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldCallExecuteInTransaction() {
        when(transactionalKafkaTemplate.executeInTransaction(any())).thenReturn(null);
        producer.send("hello", 3);
        verify(transactionalKafkaTemplate).executeInTransaction(any());
    }
}
```

```java
// src/test/java/com/testingai/kafka/transactions/TransactionalConsumerTest.java
package com.testingai.kafka.transactions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class TransactionalConsumerTest {

    @InjectMocks
    private TransactionalConsumer consumer;

    @Test
    void receive_shouldNotThrow() {
        assertThatCode(() -> consumer.receive("committed-message")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=TransactionalProducerTest,TransactionalConsumerTest
```

- [ ] **Step 3: Implement TransactionalProducer**

```java
// src/main/java/com/testingai/kafka/transactions/TransactionalProducer.java
package com.testingai.kafka.transactions;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionalProducer {

    private final KafkaTemplate<String, String> transactionalKafkaTemplate;

    public TransactionalProducer(
            @Qualifier("transactionalKafkaTemplate") KafkaTemplate<String, String> transactionalKafkaTemplate) {
        this.transactionalKafkaTemplate = transactionalKafkaTemplate;
    }

    public void send(String message, int count) {
        transactionalKafkaTemplate.executeInTransaction(ops -> {
            for (int i = 0; i < count; i++) {
                ops.send(TopicConfig.TX_OUTPUT_TOPIC, "tx-key-" + i, message + "-" + i);
            }
            log.info("[TransactionalProducer] Committed {} messages in one transaction", count);
            return null;
        });
    }
}
```

- [ ] **Step 4: Implement TransactionalConsumer**

```java
// src/main/java/com/testingai/kafka/transactions/TransactionalConsumer.java
package com.testingai.kafka.transactions;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionalConsumer {

    @KafkaListener(topics = TopicConfig.TX_OUTPUT_TOPIC, groupId = "tx-group",
                   containerFactory = "transactionalContainerFactory")
    public void receive(String message) {
        log.info("[TransactionalConsumer] read_committed: {}", message);
    }
}
```

- [ ] **Step 5: Run tests — should pass**

```bash
mvn test -Dtest=TransactionalProducerTest,TransactionalConsumerTest
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/testingai/kafka/transactions/ \
        src/test/java/com/testingai/kafka/transactions/
git commit -m "feat: add exactly-once transactional producer/consumer pattern"
```

---

### Task 9: Compaction Pattern

**Files:**
- Create: `src/main/java/com/testingai/kafka/compaction/CompactionProducer.java`
- Create: `src/main/java/com/testingai/kafka/compaction/CompactionConsumer.java`
- Create: `src/test/java/com/testingai/kafka/compaction/CompactionProducerTest.java`
- Create: `src/test/java/com/testingai/kafka/compaction/CompactionConsumerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/compaction/CompactionProducerTest.java
package com.testingai.kafka.compaction;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompactionProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private CompactionProducer producer;

    @Test
    void send_shouldSendKeyValueToCompactedTopic() {
        producer.send("user-1", "Alice");
        verify(kafkaTemplate).send(TopicConfig.COMPACTED_TOPIC, "user-1", "Alice");
    }
}
```

```java
// src/test/java/com/testingai/kafka/compaction/CompactionConsumerTest.java
package com.testingai.kafka.compaction;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class CompactionConsumerTest {

    @InjectMocks
    private CompactionConsumer consumer;

    @Test
    void receive_shouldNotThrow() {
        var record = new ConsumerRecord<>("compacted.topic", 0, 0L, "user-1", "Alice");
        assertThatCode(() -> consumer.receive(record)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=CompactionProducerTest,CompactionConsumerTest
```

- [ ] **Step 3: Implement both classes**

```java
// src/main/java/com/testingai/kafka/compaction/CompactionProducer.java
package com.testingai.kafka.compaction;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompactionProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String key, String value) {
        kafkaTemplate.send(TopicConfig.COMPACTED_TOPIC, key, value);
        log.info("[CompactionProducer] Sent key={} value={}", key, value);
    }
}
```

```java
// src/main/java/com/testingai/kafka/compaction/CompactionConsumer.java
package com.testingai.kafka.compaction;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompactionConsumer {

    @KafkaListener(topics = TopicConfig.COMPACTED_TOPIC, groupId = "compaction-group")
    public void receive(ConsumerRecord<String, String> record) {
        log.info("[CompactionConsumer] key={} value={}", record.key(), record.value());
    }
}
```

- [ ] **Step 4: Run tests — should pass**

```bash
mvn test -Dtest=CompactionProducerTest,CompactionConsumerTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/testingai/kafka/compaction/ \
        src/test/java/com/testingai/kafka/compaction/
git commit -m "feat: add log-compaction producer/consumer pattern"
```

---

### Task 10: Kafka Streams Pattern (Word Count)

**Files:**
- Create: `src/main/java/com/testingai/kafka/config/KafkaStreamsConfig.java`
- Create: `src/main/java/com/testingai/kafka/streams/StreamsProducer.java`
- Create: `src/main/java/com/testingai/kafka/streams/WordCountConsumer.java`
- Create: `src/test/java/com/testingai/kafka/streams/StreamsProducerTest.java`
- Create: `src/test/java/com/testingai/kafka/streams/WordCountConsumerTest.java`

`KafkaStreamsConfig` defines the word-count topology: input topic → split on whitespace → group by word → count → output topic. `@EnableKafkaStreams` lives here (not on the main class) so `@WebMvcTest` doesn't trigger streams startup.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/testingai/kafka/streams/StreamsProducerTest.java
package com.testingai.kafka.streams;

import com.testingai.kafka.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StreamsProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private StreamsProducer producer;

    @Test
    void send_shouldSendToStreamsInputTopic() {
        producer.send("hello world");
        verify(kafkaTemplate).send(TopicConfig.STREAMS_INPUT_TOPIC, "hello world");
    }
}
```

```java
// src/test/java/com/testingai/kafka/streams/WordCountConsumerTest.java
package com.testingai.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class WordCountConsumerTest {

    @InjectMocks
    private WordCountConsumer consumer;

    @Test
    void receive_shouldNotThrow() {
        var record = new ConsumerRecord<>("streams-wordcount-output", 0, 0L, "hello", "3");
        assertThatCode(() -> consumer.receive(record)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=StreamsProducerTest,WordCountConsumerTest
```

- [ ] **Step 3: Implement KafkaStreamsConfig**

```java
// src/main/java/com/testingai/kafka/config/KafkaStreamsConfig.java
package com.testingai.kafka.config;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.util.Arrays;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Bean
    public KStream<String, String> wordCountStream(StreamsBuilder builder) {
        KStream<String, String> input = builder.stream(TopicConfig.STREAMS_INPUT_TOPIC);
        input.flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\s+")))
             .groupBy((key, word) -> word)
             .count(Materialized.as("word-count-store"))
             .toStream()
             .mapValues(Object::toString)
             .to(TopicConfig.STREAMS_WORDCOUNT_OUTPUT);
        return input;
    }
}
```

- [ ] **Step 4: Implement StreamsProducer**

```java
// src/main/java/com/testingai/kafka/streams/StreamsProducer.java
package com.testingai.kafka.streams;

import com.testingai.kafka.config.TopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message) {
        kafkaTemplate.send(TopicConfig.STREAMS_INPUT_TOPIC, message);
        log.info("[StreamsProducer] Sent: {}", message);
    }
}
```

- [ ] **Step 5: Implement WordCountConsumer**

```java
// src/main/java/com/testingai/kafka/streams/WordCountConsumer.java
package com.testingai.kafka.streams;

import com.testingai.kafka.config.TopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WordCountConsumer {

    @KafkaListener(topics = TopicConfig.STREAMS_WORDCOUNT_OUTPUT, groupId = "wordcount-group")
    public void receive(ConsumerRecord<String, String> record) {
        log.info("[WordCountConsumer] word={} count={}", record.key(), record.value());
    }
}
```

- [ ] **Step 6: Run tests — should pass**

```bash
mvn test -Dtest=StreamsProducerTest,WordCountConsumerTest
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/testingai/kafka/config/KafkaStreamsConfig.java \
        src/main/java/com/testingai/kafka/streams/ \
        src/test/java/com/testingai/kafka/streams/
git commit -m "feat: add Kafka Streams word-count topology"
```

---

### Task 11: DemoController

**Files:**
- Create: `src/main/java/com/testingai/kafka/controller/DemoController.java`
- Create: `src/test/java/com/testingai/kafka/controller/DemoControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/testingai/kafka/controller/DemoControllerTest.java
package com.testingai.kafka.controller;

import com.testingai.kafka.compaction.CompactionProducer;
import com.testingai.kafka.partitioning.PartitioningProducer;
import com.testingai.kafka.pubsub.PubSubProducer;
import com.testingai.kafka.simple.SimpleProducer;
import com.testingai.kafka.streams.StreamsProducer;
import com.testingai.kafka.transactions.TransactionalProducer;
import com.testingai.kafka.workqueue.WorkQueueProducer;
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

    @MockitoBean private SimpleProducer simpleProducer;
    @MockitoBean private WorkQueueProducer workQueueProducer;
    @MockitoBean private PubSubProducer pubSubProducer;
    @MockitoBean private PartitioningProducer partitioningProducer;
    @MockitoBean private TransactionalProducer transactionalProducer;
    @MockitoBean private CompactionProducer compactionProducer;
    @MockitoBean private StreamsProducer streamsProducer;

    @Test
    void simple_shouldReturn200AndDelegate() throws Exception {
        mockMvc.perform(post("/demo/simple").param("message", "hello"))
                .andExpect(status().isOk());
        verify(simpleProducer).send("hello");
    }

    @Test
    void work_shouldReturn200WithDefaultCount() throws Exception {
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
    void pubsub_shouldReturn200AndDelegate() throws Exception {
        mockMvc.perform(post("/demo/pubsub").param("message", "broadcast"))
                .andExpect(status().isOk());
        verify(pubSubProducer).send("broadcast");
    }

    @Test
    void partition_shouldReturn200AndDelegate() throws Exception {
        mockMvc.perform(post("/demo/partition").param("key", "error").param("message", "boom"))
                .andExpect(status().isOk());
        verify(partitioningProducer).send("error", "boom");
    }

    @Test
    void transaction_shouldReturn200WithDefaultCount() throws Exception {
        mockMvc.perform(post("/demo/transaction").param("message", "hello"))
                .andExpect(status().isOk());
        verify(transactionalProducer).send("hello", 3);
    }

    @Test
    void compaction_shouldReturn200AndDelegate() throws Exception {
        mockMvc.perform(post("/demo/compaction").param("key", "user-1").param("value", "Alice"))
                .andExpect(status().isOk());
        verify(compactionProducer).send("user-1", "Alice");
    }

    @Test
    void streams_shouldReturn200AndDelegate() throws Exception {
        mockMvc.perform(post("/demo/streams").param("message", "hello world"))
                .andExpect(status().isOk());
        verify(streamsProducer).send("hello world");
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
mvn test -Dtest=DemoControllerTest
```

Expected: FAIL — `DemoController` does not exist.

- [ ] **Step 3: Implement DemoController**

```java
// src/main/java/com/testingai/kafka/controller/DemoController.java
package com.testingai.kafka.controller;

import com.testingai.kafka.compaction.CompactionProducer;
import com.testingai.kafka.partitioning.PartitioningProducer;
import com.testingai.kafka.pubsub.PubSubProducer;
import com.testingai.kafka.simple.SimpleProducer;
import com.testingai.kafka.streams.StreamsProducer;
import com.testingai.kafka.transactions.TransactionalProducer;
import com.testingai.kafka.workqueue.WorkQueueProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
@Tag(name = "Kafka Demo", description = "Triggers for the seven Kafka messaging patterns")
public class DemoController {

    private final SimpleProducer simpleProducer;
    private final WorkQueueProducer workQueueProducer;
    private final PubSubProducer pubSubProducer;
    private final PartitioningProducer partitioningProducer;
    private final TransactionalProducer transactionalProducer;
    private final CompactionProducer compactionProducer;
    private final StreamsProducer streamsProducer;

    @PostMapping("/simple")
    @Operation(summary = "Send to simple topic")
    public ResponseEntity<String> simple(
            @Parameter(description = "Text to send") @RequestParam String message) {
        simpleProducer.send(message);
        return ResponseEntity.ok("Sent to simple.topic: " + message);
    }

    @PostMapping("/work")
    @Operation(summary = "Send to work topic")
    public ResponseEntity<String> work(
            @Parameter(description = "Text to send; dots simulate work (e.g. task..)") @RequestParam String message,
            @Parameter(description = "Number of messages (default 5)") @RequestParam(defaultValue = "5") int count) {
        workQueueProducer.send(message, count);
        return ResponseEntity.ok("Sent " + count + " messages to work.topic");
    }

    @PostMapping("/pubsub")
    @Operation(summary = "Broadcast to both consumer groups")
    public ResponseEntity<String> pubsub(
            @Parameter(description = "Text to broadcast") @RequestParam String message) {
        pubSubProducer.send(message);
        return ResponseEntity.ok("Broadcast to pubsub.topic: " + message);
    }

    @PostMapping("/partition")
    @Operation(summary = "Route by key to a specific partition")
    public ResponseEntity<String> partition(
            @Parameter(description = "Routing key — one of: info, warning, error",
                       schema = @Schema(allowableValues = {"info", "warning", "error"}))
            @RequestParam String key,
            @Parameter(description = "Text to route") @RequestParam String message) {
        partitioningProducer.send(key, message);
        return ResponseEntity.ok("Sent to partition.topic with key=" + key + ": " + message);
    }

    @PostMapping("/transaction")
    @Operation(summary = "Send a batch atomically (exactly-once)")
    public ResponseEntity<String> transaction(
            @Parameter(description = "Text to send") @RequestParam String message,
            @Parameter(description = "Batch size (default 3)") @RequestParam(defaultValue = "3") int count) {
        transactionalProducer.send(message, count);
        return ResponseEntity.ok("Committed " + count + " messages to tx-output.topic");
    }

    @PostMapping("/compaction")
    @Operation(summary = "Upsert a key/value pair to the compacted topic")
    public ResponseEntity<String> compaction(
            @Parameter(description = "Record key") @RequestParam String key,
            @Parameter(description = "Record value") @RequestParam String value) {
        compactionProducer.send(key, value);
        return ResponseEntity.ok("Sent key=" + key + " value=" + value + " to compacted.topic");
    }

    @PostMapping("/streams")
    @Operation(summary = "Send text to the Kafka Streams word-count topology")
    public ResponseEntity<String> streams(
            @Parameter(description = "Space-separated words to count") @RequestParam String message) {
        streamsProducer.send(message);
        return ResponseEntity.ok("Sent to streams-input.topic: " + message);
    }
}
```

- [ ] **Step 4: Run test — should pass**

```bash
mvn test -Dtest=DemoControllerTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Run the full test suite**

```bash
mvn test
```

Expected: all tests pass, `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/testingai/kafka/controller/ \
        src/test/java/com/testingai/kafka/controller/
git commit -m "feat: add DemoController with 7 REST endpoints"
```

---

### Task 12: Gatling Performance Simulation

**Files:**
- Create: `src/test/java/com/testingai/kafka/performance/DemoSimulation.java`

This file is excluded from `mvn test` by the Surefire config. Run it with `mvn gatling:test` while the cluster and app are running.

- [ ] **Step 1: Create the simulation**

```java
// src/test/java/com/testingai/kafka/performance/DemoSimulation.java
package com.testingai.kafka.performance;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class DemoSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8080");

    ScenarioBuilder simpleScenario = scenario("Simple Topic")
            .exec(http("POST /demo/simple")
                    .post("/demo/simple")
                    .formParam("message", "perf-test")
                    .check(status().is(200)));

    ScenarioBuilder workScenario = scenario("Work Queue")
            .exec(http("POST /demo/work (3 msgs)")
                    .post("/demo/work")
                    .formParam("message", "task..")
                    .formParam("count", "3")
                    .check(status().is(200)));

    ScenarioBuilder pubsubScenario = scenario("PubSub")
            .exec(http("POST /demo/pubsub")
                    .post("/demo/pubsub")
                    .formParam("message", "perf-broadcast")
                    .check(status().is(200)));

    ScenarioBuilder partitionScenario = scenario("Partitioning")
            .exec(http("POST /demo/partition")
                    .post("/demo/partition")
                    .formParam("key", "info")
                    .formParam("message", "perf-route")
                    .check(status().is(200)));

    {
        setUp(
                simpleScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                ),
                workScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                ),
                pubsubScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                ),
                partitionScenario.injectOpen(
                        rampUsersPerSec(1).to(10).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(10).during(Duration.ofSeconds(30))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95).lt(500),
                        global().failedRequests().percent().lt(1.0)
                );
    }
}
```

- [ ] **Step 2: Verify the file compiles**

```bash
mvn compile -Dskip.tests=true
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/testingai/kafka/performance/DemoSimulation.java
git commit -m "feat: add Gatling performance simulation for Kafka demo"
```

---

### Task 13: README

**Files:**
- Create: `message-brokers/kafka/README.md`

- [ ] **Step 1: Create README.md**

```markdown
# Kafka Demo

A 3-broker KRaft Kafka cluster and a Spring Boot demo app demonstrating seven messaging patterns: simple, work queue, pub/sub, partitioning, transactions (exactly-once), log compaction, and Kafka Streams.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker

All commands below assume your working directory is `message-brokers/kafka/`.

## Start the cluster

```bash
cd docker
docker compose up -d
```

Wait ~30 seconds for the cluster to form, then verify:

```bash
docker exec kafka1 kafka-broker-api-versions --bootstrap-server localhost:9092
```

Kafka UI: http://localhost:8090

## Run the app

```bash
cd spring-demo
mvn spring-boot:run
```

## Trigger endpoints

```bash
# Simple topic
curl -X POST "http://localhost:8080/demo/simple?message=hello"

# Work queue (dispatches 5 messages by default)
curl -X POST "http://localhost:8080/demo/work?message=task..&count=5"

# Pub/Sub — both consumer groups receive the message
curl -X POST "http://localhost:8080/demo/pubsub?message=broadcast"

# Partitioning — key determines the partition (info | warning | error)
curl -X POST "http://localhost:8080/demo/partition?key=error&message=boom"

# Transactions — sends a batch atomically (exactly-once)
curl -X POST "http://localhost:8080/demo/transaction?message=hello&count=3"

# Compaction — upserts a key/value pair; only the latest per key is retained
curl -X POST "http://localhost:8080/demo/compaction?key=user-1&value=Alice"

# Kafka Streams — sends text through the word-count topology
curl -X POST "http://localhost:8080/demo/streams?message=hello+world+hello"
```

## Swagger UI

http://localhost:8080/swagger-ui/index.html

## Run performance tests

Requires the cluster and app to be running. Start the app in a separate terminal if needed, then run:

```bash
cd spring-demo
mvn gatling:test
```

HTML report: `target/gatling/<timestamp>/index.html`

## Stop the cluster

```bash
cd docker
docker compose down
```
```

- [ ] **Step 2: Commit**

```bash
git add message-brokers/kafka/README.md
git commit -m "docs: add Kafka demo README"
```

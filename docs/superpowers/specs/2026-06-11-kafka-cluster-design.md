# Kafka Cluster & Spring Demo Design

**Date:** 2026-06-11
**Status:** Approved

## Overview

A 3-broker KRaft Kafka cluster (Docker) and a Spring Boot demo app covering 7 messaging patterns, structured to mirror the existing `message-brokers/rabbitmq/` project.

## Directory Structure

```
message-brokers/
└── kafka/
    ├── docker/
    │   └── docker-compose.yml
    └── spring-demo/
        ├── pom.xml
        └── src/
            └── main/
                ├── java/com/testingai/kafka/
                │   ├── KafkaDemoApplication.java
                │   ├── config/
                │   │   ├── TopicConfig.java
                │   │   └── KafkaStreamsConfig.java
                │   ├── controller/
                │   │   └── DemoController.java
                │   ├── simple/
                │   │   ├── SimpleProducer.java
                │   │   └── SimpleConsumer.java
                │   ├── workqueue/
                │   │   ├── WorkQueueProducer.java
                │   │   └── WorkQueueConsumer.java
                │   ├── pubsub/
                │   │   ├── PubSubProducer.java
                │   │   ├── PubSubConsumerA.java
                │   │   └── PubSubConsumerB.java
                │   ├── partitioning/
                │   │   ├── PartitioningProducer.java
                │   │   └── PartitioningConsumer.java
                │   ├── transactions/
                │   │   ├── TransactionalProducer.java
                │   │   └── TransactionalConsumer.java
                │   ├── compaction/
                │   │   ├── CompactionProducer.java
                │   │   └── CompactionConsumer.java
                │   ├── streams/
                │   │   ├── StreamsProducer.java
                │   │   └── WordCountConsumer.java
                │   └── util/
                │       └── FailureSimulator.java
                └── resources/
                    └── application.yml
```

## Docker Cluster

- **Image:** `confluentinc/cp-kafka:7.8.0` (KRaft combined mode — broker + controller role)
- **Brokers:** `kafka1` (port 9092), `kafka2` (9093), `kafka3` (9094)
- **UI:** `provectuslabs/kafka-ui` at port 8090
- **Network:** shared `kafka_network` bridge
- **No ZooKeeper** — KRaft mode only

Each broker is configured via environment variables: `KAFKA_NODE_ID`, `KAFKA_PROCESS_ROLES=broker,controller`, `KAFKA_CONTROLLER_QUORUM_VOTERS` listing all 3 nodes, and `CLUSTER_ID` shared across all brokers.

## Messaging Patterns

| # | Pattern | Topic | Partitions | Detail |
|---|---------|-------|-----------|--------|
| 1 | Simple | `simple.topic` | 1 | 1 consumer group, 1 consumer — direct RabbitMQ simple queue analogue |
| 2 | Work Queue | `work.topic` | 3 | 2 consumers in `work-group` — partitions distributed across consumers |
| 3 | Pub/Sub | `pubsub.topic` | 3 | `group-a` and `group-b` each receive all messages independently |
| 4 | Partitioning | `partition.topic` | 3 | Producer sends with key (`info`/`warning`/`error`); same key always lands on same partition |
| 5 | Transactions | `tx-output.topic` | 3 | Exactly-once: `TransactionalProducer` sends a batch atomically to `tx-output.topic`; `TransactionalConsumer` reads with `isolation.level=read_committed` so uncommitted messages are invisible |
| 6 | Compaction | `compacted.topic` | 1 | `cleanup.policy=compact`; producer sends key/value updates, only latest per key retained |
| 7 | Kafka Streams | `streams-input.topic` → `streams-wordcount-output` | 3 | Word-count topology via KafkaStreams API; `WordCountConsumer` tails output |

## REST Endpoints

All triggered via `DemoController` at `POST /demo/*`:

```
POST /demo/simple?message=hello
POST /demo/work?message=task&count=5
POST /demo/pubsub?message=broadcast
POST /demo/partition?key=error&message=boom
POST /demo/transaction?message=hello&count=3
POST /demo/compaction?key=user-1&value=Alice
POST /demo/streams?message=hello+world
```

Swagger UI available at `http://localhost:8080/swagger-ui/index.html`.

## Spring Boot Configuration

**Spring Boot version:** 3.4.x  
**Java:** 21

**Dependencies:**
- `spring-boot-starter-web`
- `spring-kafka` (includes KafkaStreams support)
- `springdoc-openapi-starter-webmvc-ui`
- `lombok`
- `spring-boot-starter-test` + `spring-kafka-test` (EmbeddedKafka)
- `gatling-charts-highcharts` (performance tests)

**`application.yml`** key settings:
- `spring.kafka.bootstrap-servers: localhost:9092`
- `auto-offset-reset: earliest`
- `transaction-id-prefix: tx-` (transactional producer)
- Streams: `application-id: kafka-demo-streams`, string serdes as default

**Topic creation:** All topics declared as `NewTopic` beans in `TopicConfig`. Spring Kafka creates them on startup. `compacted.topic` gets `cleanup.policy=compact` via `TopicBuilder`.

## Failure Simulation

`FailureSimulator` utility (same ~5% random failure rate as RabbitMQ demo) applied to `SimpleConsumer` and `WorkQueueConsumer` to demonstrate retry/redelivery behavior.

## Testing

- Each producer and consumer class has a unit test using `@EmbeddedKafka`
- `DemoControllerTest` uses `MockMvc`
- Gatling performance simulation in `src/test/java/.../performance/DemoSimulation.java`

## README

`kafka/README.md` follows the same format as `rabbitmq/README.md`:
- Prerequisites, start cluster, verify, run app, curl examples, Swagger UI link, performance tests, stop cluster.

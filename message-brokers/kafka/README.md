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

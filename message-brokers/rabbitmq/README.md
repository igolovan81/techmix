# RabbitMQ Demo

A 3-node RabbitMQ Docker cluster and a Spring Boot demo app demonstrating four messaging patterns: simple queue, work queues, pub/sub (fanout exchange), and routing (direct exchange).

## Prerequisites

- Java 21
- Maven 3.9+
- Docker

## Start the cluster

```bash
cd docker
docker compose up -d
```

Wait ~30 seconds for the cluster to form, then verify:

```bash
docker exec rabbitmq1 rabbitmqctl cluster_status
```

Management UI: http://localhost:15672 (guest / guest)

## Run the app

```bash
cd spring-demo
mvn spring-boot:run
```

## Trigger endpoints

```bash
# Simple queue
curl -X POST "http://localhost:8080/demo/simple?message=hello"

# Work queue (dispatches 5 messages by default)
curl -X POST "http://localhost:8080/demo/work?message=task..&count=5"

# Pub/Sub — broadcast to all fanout subscribers
curl -X POST "http://localhost:8080/demo/pubsub?message=broadcast"

# Routing — direct exchange with routing key (info | warning | error)
curl -X POST "http://localhost:8080/demo/routing?key=error&message=boom"
```

## Swagger UI

http://localhost:8080/swagger-ui.html

## Run performance tests

Requires the cluster and app to be running (see above).

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

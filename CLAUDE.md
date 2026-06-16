# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Message broker demos (all six modules — run from the module root)

```bash
cd message-brokers/<broker>/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app to be running first
```

### Backend REST API

```bash
cd backend/rest-api

mvn clean package
mvn test
mvn test -Dtest=ClassName
mvn fmt:format                       # enforce Google Java Format before committing
```

### Frontend (Angular 20 with SSR)

```bash
cd frontend/angular

npm install
npm start                            # dev server on :4200
npm test                             # Karma/Jasmine
npm run build                        # production build
```

### Infrastructure

```bash
# Shared: SonarQube (:9000), H2 (:81/:1521), Postgres (:5432), Oracle (:1522), backend (:8080), frontend (:4200→80)
docker compose up -d

# Per broker — each has its own compose file
docker compose -f message-brokers/kafka/docker/docker-compose.yml up -d
docker compose -f message-brokers/azure-service-bus/docker/docker-compose.yml up -d
docker compose -f message-brokers/rabbitmq/docker/docker-compose.yml up -d
docker compose -f message-brokers/redis/docker/docker-compose.yml up -d
docker compose -f message-brokers/sqs/docker/docker-compose.yml up -d
docker compose -f message-brokers/pulsar/docker/docker-compose.yml up -d
```

## Architecture

### Repository layout

| Path | Description |
|---|---|
| `backend/rest-api/` | Spring Boot REST API (JPA, Liquibase, Spring Security, H2 in tests) |
| `backend/hackerrank/` | Isolated Maven project for algorithm problems |
| `frontend/angular/` | Angular 20 with SSR via Express |
| `message-brokers/<broker>/spring-demo/` | Six independent Spring Boot 3.4.4 demo apps (Java 21, Lombok) |
| `docker-compose.yml` | Shared infrastructure stack |

### Message broker demos

Each broker demo is a standalone Spring Boot app. The same internal structure repeats across all six:

- **`DemoController`** — single REST controller that triggers all messaging patterns
- **Package-per-pattern** — `simple/`, `workqueue/`, `pubsub/`, plus broker-specific packages
- **`util/FailureSimulator`** — shared utility, see [code review rules](.claude/rules/code-review.md)
- **Entity/queue/topic constants** — one central constants class per module (`EntityNames`, `QueueNames`, `TopicConfig`, `StreamKeys`)
- **`src/test/.../performance/DemoSimulation.java`** — Gatling load test, excluded from `mvn test` via surefire config; run explicitly with `mvn gatling:test`

**Broker-specific patterns and ports:**

| Broker | Extra patterns | App port | Infrastructure |
|---|---|---|---|
| Kafka | `partitioning/`, `compaction/`, `streams/` (Kafka Streams), `transactions/` | 8080 (default) | 3-node KRaft cluster :9092–:9094, kafka-ui :8090 |
| Azure Service Bus | `session/`, `routing/` (topic subscriptions), `dlq/`, `transactions/` | 8082 | Emulator (AMQP :5672) + Azure SQL Edge |
| SQS | `fifo/`, `fanout/` (SNS→SQS), `dlq/` | 8081 | LocalStack :4566 (region us-east-1) |
| Redis | `pending/` (PEL recovery), `fanout/`, `trimming/` | 8080 (default) | 6-node cluster :6379–:6384 (Redis Streams) |
| RabbitMQ | `routing/` | 8080 (default) | :5672 guest/guest |
| Apache Pulsar | `routing/` (Key_Shared), `transactions/` | 8083 | Standalone :6650, admin :8085 |

### Backend REST API

Standard layered architecture: `controller → service → repository → entity`. H2 in-memory for tests, Postgres in production. Liquibase manages schema migrations. `fmt-maven-plugin` (Google Java Format) is wired into the build — run `mvn fmt:format` before committing Java changes.

## Coding Standards (`.claude/rules/`)

Detailed coding conventions are in `.claude/rules/` — autoloaded by Claude Code when editing matching file paths:

- `.claude/rules/code-review.md` — review rules for all Java and TypeScript sources;
  `FailureSimulator` consistency across message broker modules (5% `FAILURE_RATE`, `maybeThrow(String context)`, Kafka module as reference);
  modern Java 17/21 LTS feature preference (records, sealed classes, pattern matching for `instanceof` and `switch`, record patterns,
  text blocks, `SequencedCollection` API, virtual threads) — flagged only on lines modified by the PR

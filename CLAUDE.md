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

### NoSQL database demos (run from the module root)

```bash
cd noSQL/<database>/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app to be running first
```

### CQRS/Event Sourcing demos (Axon Framework — run from the module root)

```bash
cd cqrs-event-sourcing/axon/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app and Axon Server running first
```

### Template engine demos (both modules — run from the module root, no docker infrastructure required)

```bash
cd template-engines/<engine>/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app to be running first
```

### Saga pattern demo (run from the module root, no docker infrastructure required)

```bash
cd distributed-transactions/saga/spring-demo

mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn gatling:test                     # load test — requires the app to be running first
```

### gRPC communication protocol demo (run from the reactor root, no docker infrastructure required)

```bash
cd communication-protocols

mvn clean package                                            # build both apps (reactor build)
mvn test                                                      # unit tests for both modules (Gatling excluded automatically)
mvn test -pl grpc/client-demo -Dtest=ClassName                 # single test class
mvn -pl grpc/server-demo spring-boot:run                       # run the server first (gRPC :9090)
mvn -pl grpc/client-demo spring-boot:run                       # then the client (REST :8091)
mvn gatling:test -pl grpc/client-demo                           # Gatling load test — requires both apps running first
mvn verify -Pjmeter-load-test -pl grpc/client-demo              # JMeter load test — requires both apps running first
```

### GraphQL communication protocol demo (run from the reactor root, no docker infrastructure required)

```bash
cd communication-protocols

mvn clean package                                      # build (part of the reactor build)
mvn test -pl graphql/spring-demo                        # unit tests (Gatling excluded automatically)
mvn test -pl graphql/spring-demo -Dtest=ClassName        # single test class
docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d   # Postgres :5433
mvn -pl graphql/spring-demo spring-boot:run              # run the app (GraphiQL at :8092/graphiql)
mvn gatling:test -pl graphql/spring-demo                 # Gatling load test — requires the app running first
mvn verify -Pjmeter-load-test -pl graphql/spring-demo    # JMeter load test — requires the app running first
```

### Webhooks communication protocol demo (run from the reactor root, no docker infrastructure required)

```bash
cd communication-protocols

mvn clean package                                                      # build both apps (reactor build)
mvn test -pl webhooks/producer-demo,webhooks/consumer-demo              # unit tests for both modules (Gatling excluded automatically)
mvn test -pl webhooks/consumer-demo -Dtest=ClassName                    # single test class
mvn -pl webhooks/consumer-demo spring-boot:run                          # run the consumer first (receives on :8097)
mvn -pl webhooks/producer-demo spring-boot:run                          # then the producer (dispatches from :8096)
mvn gatling:test -pl webhooks/producer-demo                             # Gatling load test — requires both apps running first
mvn verify -Pjmeter-load-test -pl webhooks/producer-demo                # JMeter load test — requires both apps running first
```

### WebSocket communication protocol demo (run from the reactor root, no docker infrastructure required)

```bash
cd communication-protocols

mvn clean package                                      # build (part of the reactor build)
mvn test -pl websockets/spring-demo                     # unit tests (Gatling excluded automatically)
mvn test -pl websockets/spring-demo -Dtest=ClassName    # single test class
mvn -pl websockets/spring-demo spring-boot:run          # run the app (test client at :8098/ws-client/index.html)
mvn gatling:test -pl websockets/spring-demo             # Gatling load test — requires the app running first
```

### Camunda workflow engine demo (run from the reactor root — requires Docker for both the app and `mvn test`)

```bash
docker compose -f workflow-engines/camunda/docker/docker-compose.yml up -d   # Camunda 8 (Zeebe/Operate/Tasklist) + Elasticsearch

cd workflow-engines

mvn clean package                                      # build (part of the reactor build)
mvn test -pl camunda/spring-demo                        # unit + integration tests — requires a working Docker daemon (Testcontainers), NOT the compose stack above
mvn test -pl camunda/spring-demo -Dtest=ClassName        # single test class
mvn -pl camunda/spring-demo spring-boot:run              # run the app (:8093) against the compose stack; Operate/Tasklist UI at :8080 (redirects to /operate, /tasklist)
mvn gatling:test -pl camunda/spring-demo                 # Gatling load test — requires the app running first
```

### Project Reactor demo (run from the reactor root, no docker infrastructure required)

```bash
cd reactive-programming

mvn clean package                                                  # build both apps (reactor build)
mvn test                                                            # unit tests for both modules (Gatling excluded automatically)
mvn test -pl project-reactor/spring-demo -Dtest=ClassName            # single test class
mvn -pl project-reactor/upstream-demo spring-boot:run                 # run the upstream service first (:8095)
mvn -pl project-reactor/spring-demo spring-boot:run                   # then the main demo app (:8094)
mvn gatling:test -pl project-reactor/spring-demo                       # load test — requires both apps running first
```

### Spring Boot starter demo (run from the reactor root, no docker infrastructure required)

```bash
cd spring-boot-starters

mvn clean package                                            # build the starter and the demo together (reactor build)
mvn test                                                     # unit tests for both modules
mvn test -pl request-logging/spring-demo -Dtest=ClassName    # single test class in the demo
mvn spring-boot:run -pl request-logging/spring-demo -am      # run the demo app (-am builds the starter first)
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

### Frontend Angular capabilities demo (self-contained, no backend)

```bash
cd frontend/angular-demo

npm install
npm start                            # dev server on :4201
npm test                             # Jasmine/Karma unit tests
npx playwright test                  # e2e tests (auto-starts the dev server)
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
docker compose -f cqrs-event-sourcing/axon/docker/docker-compose.yml up -d
```

## Architecture

### Repository layout

| Path | Description |
|---|---|
| `backend/rest-api/` | Spring Boot REST API (JPA, Liquibase, Spring Security, H2 in tests) |
| `backend/hackerrank/` | Isolated Maven project for algorithm problems |
| `frontend/angular/` | Angular 20 with SSR via Express |
| `frontend/angular-demo/` | Self-contained Angular 21 app touring the framework's capabilities (signals, forms, routing guards/resolvers, `@defer`, etc.) — no backend |
| `message-brokers/<broker>/spring-demo/` | Six independent Spring Boot 3.4.4 demo apps (Java 21, Lombok) |
| `noSQL/<database>/spring-demo/` | NoSQL database demo apps, same conventions as `message-brokers/` (currently: MongoDB) |
| `cqrs-event-sourcing/<framework>/spring-demo/` | CQRS/event-sourcing framework demo apps, same conventions as `message-brokers/` (currently: Axon Framework) |
| `template-engines/<engine>/spring-demo/` | Template-engine demo apps, same conventions as `message-brokers/` (currently: Handlebars, FreeMarker) — no external infrastructure required |
| `distributed-transactions/<pattern>/spring-demo/` | Distributed-transaction pattern demo apps, same conventions as `message-brokers/` (currently: Saga, both choreography and orchestration) — no external infrastructure required |
| `communication-protocols/grpc/{server-demo,client-demo}/` | gRPC demo — two independent Spring Boot apps covering all four RPC patterns (unary, server/client/bidi streaming); `server-demo` must be started before `client-demo` — no external infrastructure required |
| `communication-protocols/graphql/spring-demo/` | GraphQL demo — single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, subscription, cursor-pagination, and an e-commerce domain (Category/User/Order) against Postgres; `mvn test` needs no external infrastructure (H2), but running the app needs `docker compose -f communication-protocols/graphql/docker/docker-compose.yml up -d` first |
| `communication-protocols/webhooks/producer-demo/` | Webhooks demo — subscription registry, HMAC-signed dispatch, retry/backoff, dead-lettering — no external infrastructure required |
| `communication-protocols/webhooks/consumer-demo/` | Webhooks demo — signature verification, delivery-id idempotency/dedup, on-demand failure simulation — no external infrastructure required |
| `communication-protocols/websockets/spring-demo/` | WebSocket demo — raw `WebSocketHandler` broadcast, STOMP broadcast/per-order-topic/request-reply patterns, disconnect handling, and a static browser test client over an order-tracking domain — no external infrastructure required |
| `reactive-programming/project-reactor/{spring-demo,upstream-demo}/` | Project Reactor demo — two independent Spring Boot WebFlux apps covering Mono/Flux basics, backpressure/error handling, schedulers/concurrency, and SSE/WebClient streaming; `upstream-demo` must be started before `spring-demo`'s `streaming/upstream/*` endpoints work — no external infrastructure required |
| `workflow-engines/camunda/spring-demo/` | Camunda 8 (Zeebe) BPMN workflow demo — service tasks, exclusive gateway, user task (approval), and error-boundary-driven failure routing over the order-fulfillment domain shared with `distributed-transactions/saga`; requires Docker for both the running app (`docker compose`) and `mvn test` (Testcontainers) |
| `spring-boot-starters/<starter>/<starter>-spring-boot-starter/` + `.../spring-demo/` | Custom Spring Boot starter demos — each starter is an auto-configuration jar plus a consuming demo app in the same Maven reactor (currently: request-logging) — no external infrastructure required |
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

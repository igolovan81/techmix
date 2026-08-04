# techmix

A collection of self-contained demo applications for exploring backend and frontend patterns — message brokers, NoSQL databases, CQRS/event sourcing, communication protocols, distributed transactions, workflow engines, reactive programming, template engines, and a full-stack REST + Angular app. Each demo is an independently runnable Spring Boot (Java 21) or Angular project with its own tests and, where relevant, Gatling/JMeter load tests.

For AI-assistant build/run commands and detailed architecture notes, see [CLAUDE.md](CLAUDE.md).

## Repository layout

| Path | What it demonstrates |
|---|---|
| `backend/rest-api/` | Layered Spring Boot REST API (JPA, Liquibase, Spring Security) |
| `backend/hackerrank/` | Standalone algorithm practice project |
| `frontend/angular/` | Angular 20 app with SSR, backed by `backend/rest-api` |
| `frontend/angular-demo/` | Self-contained Angular 21 tour of framework capabilities (signals, forms, routing, `@defer`, ...) |
| `message-brokers/` | Kafka, RabbitMQ, Azure Service Bus, SQS, Redis Streams, Apache Pulsar — six parallel demo apps covering the same messaging patterns per broker |
| `noSQL/` | NoSQL database demos (currently MongoDB) |
| `cqrs-event-sourcing/` | CQRS/event sourcing with Axon Framework |
| `template-engines/` | Server-side template engines (Handlebars, FreeMarker) |
| `distributed-transactions/` | Saga pattern (choreography and orchestration) |
| `domain-driven-design/` | Tactical DDD patterns — banking ledger with aggregates, value objects, domain events, bounded contexts, and an anti-corruption layer |
| `communication-protocols/` | gRPC, GraphQL (+ Angular client), Webhooks, WebSockets |
| `workflow-engines/` | Camunda 8 (Zeebe) BPMN workflow demo |
| `reactive-programming/` | Project Reactor (Mono/Flux, backpressure, schedulers, SSE/WebClient streaming) |
| `spring-boot-starters/` | Custom Spring Boot auto-configuration starters with a consuming demo app |
| `docker-compose.yml` | Shared infrastructure (SonarQube, H2, Postgres, Oracle) — see [DOCKER-COMPOSE-README.md](DOCKER-COMPOSE-README.md) |

Each broker/database/pattern demo follows the same internal conventions (controller → service → repository, a `FailureSimulator` utility, Gatling load tests excluded from `mvn test`) — see the "Message broker demos" section of [CLAUDE.md](CLAUDE.md) for the full breakdown.

## Prerequisites

- Java 21 (JDK 21 — some modules use Groovy/gmavenplus, which breaks on newer JDKs; set `JAVA_HOME` accordingly)
- Maven
- Node.js + npm (for the Angular apps)
- Docker + Docker Compose (for modules that need external infrastructure — brokers, databases, Camunda)

## Getting started

Every demo module is independent and documents its own commands. Pick a category above, `cd` into its module root, and follow that module's README (or the matching section in [CLAUDE.md](CLAUDE.md)) for build/run/test instructions. Most modules follow the same shape:

```bash
cd <category>/<module>/spring-demo   # or the module root, if there's no spring-demo subfolder

mvn clean package     # build
mvn test               # unit tests (Gatling/JMeter excluded automatically)
mvn spring-boot:run     # run the app (see the module's README for the port and any required docker compose stack)
```

Shared infrastructure (SonarQube, H2, Postgres, Oracle) is started from the repository root:

```bash
docker compose up -d
```

Modules that need their own infrastructure (a broker, a NoSQL database, Camunda, ...) ship a dedicated `docker-compose.yml` under that module's `docker/` folder — see [CLAUDE.md](CLAUDE.md) for the exact command per module.

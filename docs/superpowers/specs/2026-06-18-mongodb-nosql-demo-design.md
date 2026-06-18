# MongoDB NoSQL Demo Design

**Date:** 2026-06-18
**Status:** Approved

## Overview

A new top-level `noSQL/` category (sibling to `message-brokers/`), with a 3-node MongoDB replica set and a Spring Boot demo app as its first module. Mirrors `message-brokers/`'s parent/module Maven structure and demo-app conventions (REST-triggered patterns, Swagger UI, Spotless formatting, Gatling performance tests) so the codebase stays consistent as more NoSQL databases are potentially added later.

## Repository structure

```
noSQL/
├── pom.xml                                          (new parent POM, mirrors message-brokers/pom.xml)
├── eclipse-formatter.xml                            (copy of message-brokers/eclipse-formatter.xml — same style repo-wide)
├── README.md                                        (short index; lists the mongodb module)
└── mongodb/
    ├── docker/
    │   └── docker-compose.yml
    ├── spring-demo/
    │   ├── pom.xml                                  (artifactId: mongodb-demo)
    │   └── src/
    │       ├── main/
    │       │   ├── java/com/testingai/mongodb/
    │       │   │   ├── MongoDbDemoApplication.java
    │       │   │   ├── config/
    │       │   │   │   └── MongoConfig.java          (MongoTransactionManager bean)
    │       │   │   ├── controller/
    │       │   │   │   └── DemoController.java
    │       │   │   ├── crud/
    │       │   │   │   ├── Product.java
    │       │   │   │   └── ProductService.java
    │       │   │   ├── transaction/
    │       │   │   │   ├── Order.java
    │       │   │   │   └── OrderService.java
    │       │   │   ├── changestream/
    │       │   │   │   └── OrderChangeStreamListener.java
    │       │   │   └── aggregation/
    │       │   │       └── OrderAggregationService.java
    │       │   └── resources/
    │       │       └── application.yml
    │       └── test/
    │           ├── java/com/testingai/mongodb/
    │           │   ├── MongoDbDemoApplicationTest.java
    │           │   ├── crud/ProductServiceTest.java
    │           │   ├── transaction/OrderServiceTest.java
    │           │   ├── changestream/OrderChangeStreamListenerTest.java
    │           │   ├── aggregation/OrderAggregationServiceTest.java
    │           │   ├── controller/DemoControllerTest.java
    │           │   └── performance/DemoSimulation.java
    │           └── resources/application.yml
    └── README.md
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** currently auto-formats staged Java files only under `message-brokers/` (hardcoded `grep '^message-brokers/.*\.java$'`). It must be extended to also match `^noSQL/.*\.java$` and run `mvn spotless:apply` from `noSQL/` for those files — otherwise commits to the new module silently skip formatting.
- **`CLAUDE.md`** gets a new short section documenting the `noSQL/` category and its commands, mirroring the existing `message-brokers/` section.

## Cluster topology — 3-node replica set

Three `mongod` containers form a replica set named `rs0`, matching this repo's established pattern of running a real multi-node cluster for every demo (Kafka: 3 brokers, RabbitMQ: 3 nodes, Redis: 6 nodes) rather than a single instance:

```
┌────────────┐  ┌────────────┐  ┌────────────┐
│   mongo1   │  │   mongo2   │  │   mongo3   │
│  :27017    │  │  :27017    │  │  :27017    │   (internal port — Mongo default)
└─────┬──────┘  └─────┬──────┘  └─────┬──────┘
      │   replica set "rs0" (rs.initiate via   │
      └───── a one-shot mongo-init container, ─┘
             same pattern as Redis's cluster-init

Host ports: 27017 / 27018 / 27019 (one per node)
mongo-express UI: 8091 (next free slot after Kafka UI's 8090)
Spring Boot app: 8084 (next free slot after Pulsar's 8083)
```

- **No authentication** — matches Kafka/Redis's no-auth local-dev simplicity. The only services in this repo with credentials (RabbitMQ, Postgres, Oracle, Azure SQL Edge) require them because the software itself mandates it; MongoDB doesn't, and skipping auth keeps the demo's `mongo-init` bootstrap simple (no keyfile, no user creation step).
- **`mongo-init`** (one-shot container, same `mongo` image): waits for all three nodes to be reachable, then runs `rs.initiate({_id: "rs0", members: [...]})` via `mongosh`.
- Each `mongod` container's healthcheck: `mongosh --eval "db.adminCommand('ping')"`.
- App connection string: `mongodb://localhost:27017,localhost:27018,localhost:27019/ecommerce?replicaSet=rs0`.

## Domain model & demo patterns

A single "product catalog + orders" theme ties all four patterns together with shared, realistic data (more concrete than the broker demos' abstract topic names):

| Pattern | Collection(s) | What it demonstrates |
|---|---|---|
| **CRUD** | `products` | Create/read/update/delete a product document (`id`, `name`, `price`, `stock`) |
| **Transactions** | `products` + `orders` | Placing an order atomically decrements the product's `stock` and inserts an `orders` document in one `ClientSession`-bound transaction. If `stock` is insufficient, the transaction rolls back for real — a genuine business-rule failure, not an artificial one, so no `FailureSimulator`-style random failure injection is needed for this module |
| **Change streams** | `orders` | A background listener, started at application startup via `MongoTemplate.changeStream(...)`, subscribes to inserts on `orders` and logs each one. Naturally triggered by the transaction endpoint placing an order — this is the NoSQL analogue of the broker demos' pub/sub pattern |
| **Aggregation** | `orders` | A read endpoint runs a `$match`/`$group`/`$sort` pipeline computing total revenue and order count grouped by order status |

## REST API

All triggered via `DemoController` at `/demo/*`:

```
POST   /demo/products              create a product       — body: {name, price, stock}
GET    /demo/products/{id}         read a product
PUT    /demo/products/{id}         update a product        — body: {name, price, stock}
DELETE /demo/products/{id}         delete a product
POST   /demo/orders                place an order (transaction; also triggers the change-stream listener)
                                                            — body: {productId, quantity}
GET    /demo/aggregation           revenue + order count per status
```

Swagger UI: `http://localhost:8084/swagger-ui/index.html`.

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**Dependencies:**
- `spring-boot-starter-web`
- `spring-boot-starter-data-mongodb` (synchronous `MongoTemplate` — not the reactive/WebFlux variant; `MongoTemplate.changeStream()` already supports a blocking subscription, so the app stays consistent with the other demos' non-reactive MVC style)
- `springdoc-openapi-starter-webmvc-ui`
- `lombok`
- `spring-boot-starter-test` (test scope)
- `gatling-charts-highcharts` (test scope, performance tests)

**`application.yml`** key settings:
- `spring.data.mongodb.uri: mongodb://localhost:27017,localhost:27018,localhost:27019/ecommerce?replicaSet=rs0`
- `server.port: 8084`

**`MongoConfig`** (new — Spring Boot does not auto-configure this, unlike JPA's transaction manager):
```java
@Bean
public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
}
```

**Change-stream listener bootstrap:** `OrderChangeStreamListener` registers its subscription in an `@PostConstruct` method (or `ApplicationRunner`), mirroring how the Redis demo's pub/sub subscribers are wired up at startup.

## Testing

- Unit tests for `ProductService`, `OrderService` (transaction rollback on insufficient stock), `OrderChangeStreamListener`, and `OrderAggregationService`, using an embedded/test MongoDB instance (Testcontainers' MongoDB module, since `de.flapdoodle`-style embedded Mongo does not support replica sets/transactions — Testcontainers can start a single-node replica set for tests)
- `DemoControllerTest` using `MockMvc`
- Gatling performance simulation in `src/test/java/.../performance/DemoSimulation.java`, hitting create-product, place-order, and aggregation endpoints — same convention as the six broker demos (excluded from `mvn test` via the inherited surefire `**/performance/**` exclude in `noSQL/pom.xml`, run explicitly via `mvn gatling:test`)

## README

`noSQL/mongodb/README.md` follows the same format as `message-brokers/kafka/README.md`: Prerequisites, start cluster, verify, run app, curl examples, Swagger UI link, performance tests, architecture (with a cluster topology diagram and a patterns/data-flow diagram), collection characteristics table, replica set admin commands (`rs.status()`, etc.), stop cluster.

`noSQL/README.md` is a short index, analogous to the top of `message-brokers/README.md`, listing the MongoDB module (and ready to grow into a comparison guide if more NoSQL databases are added later — not written now, since there is nothing yet to compare against).

## Scope limits

- No authentication on the MongoDB cluster (local-dev simplification, documented above).
- No sharded cluster — a 3-node replica set is sufficient to unlock transactions and change streams, which is what this demo needs; sharding is real added complexity (mongos routers, config server replica set) with no corresponding demo value here.
- No `FailureSimulator`-style artificial random failures — the transaction pattern's insufficient-stock rollback is a real, meaningful failure case that serves the same pedagogical purpose.
- `message-brokers/README.md`'s broker comparison guide is not touched — MongoDB is not a message broker and doesn't belong in that table.

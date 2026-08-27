# Cassandra NoSQL Demo Design

**Date:** 2026-08-27
**Status:** Draft

## Overview

A new `noSQL/cassandra/` module, sibling to `noSQL/mongodb/`, following that module's established shape exactly: a Docker Compose cluster plus one Spring Boot demo app, sharing the product-catalog/orders domain for comparability. Where MongoDB's demo covers CRUD/transactions/change-streams/aggregation, Cassandra's demo covers six patterns that are actually distinctive to a wide-column store — MongoDB's four patterns don't map cleanly onto Cassandra (no joins, no ACID multi-document transactions, no native change streams):

- **crud** — single-partition CRUD on a `products` table
- **datamodeling** — query-first denormalization: the same order written to two tables (`orders_by_customer`, `orders_by_product`), one per query shape, instead of a join
- **lwt** — lightweight transactions: a Paxos-backed compare-and-set stock decrement (`IF stock >= ?`)
- **consistency** — tunable per-request consistency levels (`ONE`/`QUORUM`/`ALL`) on reads and writes against the 3-node ring
- **ttl** — expiring rows: a "recently viewed products" table written `USING TTL`
- **counter** — a dedicated counter table incremented on every order

`placeOrder` ties `lwt` + `datamodeling` + `counter` together in one call, mirroring how MongoDB's `OrderService.placeOrder` ties together transactions/change-streams/aggregation.

## Repository structure

```
noSQL/
├── README.md                                        (edit: add Cassandra row to the comparison table)
├── pom.xml                                           (edit: add <module>cassandra/spring-demo</module>)
└── cassandra/
    ├── README.md
    ├── docker/
    │   ├── docker-compose.yml
    │   ├── init/
    │   │   └── init-keyspace.cql                     (keyspace + all 6 tables, RF=3)
    │   ├── prometheus/
    │   │   └── prometheus.yml
    │   └── grafana/
    │       ├── provisioning/
    │       │   ├── datasources/prometheus.yml
    │       │   └── dashboards/provider.yml
    │       └── dashboards/cassandra.json
    └── spring-demo/
        ├── pom.xml                                   (artifactId: cassandra-demo)
        └── src/
            ├── main/
            │   ├── java/com/testingai/cassandra/
            │   │   ├── CassandraDemoApplication.java
            │   │   ├── crud/
            │   │   │   ├── Product.java               (@Table("products"))
            │   │   │   └── ProductService.java
            │   │   ├── datamodeling/
            │   │   │   ├── OrderByCustomer.java        (@Table("orders_by_customer"))
            │   │   │   ├── OrderByProduct.java          (@Table("orders_by_product"))
            │   │   │   ├── OrderService.java             (placeOrder: ties lwt + datamodeling + counter together)
            │   │   │   └── PlaceOrderRequest.java        (record: customerId, productId, quantity)
            │   │   ├── lwt/
            │   │   │   └── StockReservationService.java  (conditional UPDATE ... IF stock >= ?)
            │   │   ├── consistency/
            │   │   │   ├── ConsistencyDemoService.java   (read/write products at a caller-supplied CL)
            │   │   │   └── ConsistencyReadResult.java    (record: product, consistencyLevel, elapsedMillis)
            │   │   ├── ttl/
            │   │   │   ├── ProductView.java              (@Table("recently_viewed"))
            │   │   │   └── RecentlyViewedService.java    (record on read, list still-live rows)
            │   │   ├── counter/
            │   │   │   └── OrderCountService.java        (raw CQL increment + read against order_counts_by_product)
            │   │   └── controller/
            │   │       └── DemoController.java
            │   └── resources/
            │       └── application.yml                  (server.port: 8085; spring.cassandra.*)
            └── test/
                ├── java/com/testingai/cassandra/
                │   ├── CassandraDemoApplicationTest.java
                │   ├── crud/ProductServiceTest.java
                │   ├── datamodeling/OrderServiceTest.java
                │   ├── lwt/StockReservationServiceTest.java
                │   ├── consistency/ConsistencyDemoServiceTest.java
                │   ├── ttl/RecentlyViewedServiceTest.java
                │   ├── counter/OrderCountServiceTest.java
                │   ├── controller/DemoControllerTest.java
                │   └── performance/DemoSimulation.java
                └── resources/application.yml
```

### Cross-cutting edits to existing files

- **`noSQL/pom.xml`** — add `<module>cassandra/spring-demo</module>`.
- **`noSQL/README.md`** — add a Cassandra row to the comparison table (`Cassandra | 3-node ring, RF=3 | Wide-column, write-heavy workloads, tunable consistency, linear scalability`).
- **`CLAUDE.md`** — add a "Cassandra demo" command section (mirroring the MongoDB section), a `noSQL/cassandra/spring-demo/` row in the repository layout table.
- **`README.md`** (repo root) — add/extend the `noSQL/` row if one exists there.
- **`.githooks/pre-commit`** — verify the existing `noSQL/` staged-file grep and `mvn spotless:apply` block already cover any path under `noSQL/`, since it's driven by the `noSQL/` prefix, not per-database; only extend if the hook is scoped to `noSQL/mongodb/` specifically.

## Cluster topology (Docker Compose)

Three `cassandra:5.0` nodes forming one ring, keyspace `ecommerce` with `RF=3` (matches the MongoDB demo's 3-node replica set, so the `consistency` pattern has real replicas to be tunable across):

| Service | Native transport (host:container) | JMX (host:container) | Role |
|---|---|---|---|
| `cassandra1` | `9042:9042` | `7199:7199` | seed |
| `cassandra2` | `9043:9042` | `7200:7199` | |
| `cassandra3` | `9044:9042` | `7201:7199` | |
| `cassandra-init` | — | — | one-shot `cqlsh` container running `init-keyspace.cql` once all three nodes are healthy (mirrors `mongo-init`) |

`CASSANDRA_SEEDS=cassandra1` on all three nodes; `CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch`; healthcheck via `cqlsh -e "describe cluster"` (or `nodetool status` if that proves more reliable — confirm during implementation). No Cassandra web UI (mongo-express equivalent doesn't have a maintained analog); the README documents `cqlsh`/`nodetool` commands via `docker exec` instead, same as the message-broker demos that lack a UI (e.g. SQS/LocalStack).

### Monitoring

`criteo/cassandra_exporter` sidecar per node (`cassandra1-exporter`, `cassandra2-exporter`, `cassandra3-exporter`), each pointed at its node's JMX port, internal-network-only (no host port), Prometheus (`9096:9090`) scrapes all three, Grafana (`3003:3000`) with one provisioned dashboard (panels: node up/down per instance, read/write latency, pending compactions, storage load, active connections — the metrics `criteo/cassandra_exporter` exposes). This is the part of the stack most likely to need iteration during implementation — JMX-based Cassandra exporters are less turnkey than `mongodb-exporter`; if `criteo/cassandra_exporter` proves unreliable, the fallback is a single exporter against `cassandra1` only (still demonstrates the monitoring pattern, just without per-node panels).

## Domain model

Same product-catalog/orders domain as `noSQL/mongodb`, with a `customerId` added to `PlaceOrderRequest` to justify the two denormalized order tables.

```sql
CREATE TABLE products (
    id uuid PRIMARY KEY,
    name text,
    price decimal,
    stock int
);

CREATE TABLE orders_by_customer (
    customer_id text,
    order_id timeuuid,
    product_id uuid,
    quantity int,
    unit_price decimal,
    line_total decimal,
    PRIMARY KEY (customer_id, order_id)
) WITH CLUSTERING ORDER BY (order_id DESC);

CREATE TABLE orders_by_product (
    product_id uuid,
    order_id timeuuid,
    customer_id text,
    quantity int,
    unit_price decimal,
    line_total decimal,
    PRIMARY KEY (product_id, order_id)
) WITH CLUSTERING ORDER BY (order_id DESC);

CREATE TABLE recently_viewed (
    product_id uuid,
    viewed_at timeuuid,
    PRIMARY KEY (product_id, viewed_at)
) WITH CLUSTERING ORDER BY (viewed_at DESC);
-- rows written with USING TTL <seconds>

CREATE TABLE order_counts_by_product (
    product_id uuid PRIMARY KEY,
    order_count counter
);
```

## Pattern implementations

### `crud/` — ProductService

Standard `CassandraTemplate` (Spring Data Cassandra) CRUD against `products`, single partition per product — the baseline every other pattern builds on.

### `datamodeling/` — OrderService

`placeOrder(customerId, productId, quantity)`:
1. Calls `StockReservationService.decrementIfAvailable` (the `lwt` pattern) — fails fast with a 409-mapped exception if the conditional update reports `[applied] = false`.
2. On success, writes the same order data into both `orders_by_customer` and `orders_by_product` via `CassandraTemplate` — no join, no secondary index, just two purpose-built tables, the signature Cassandra data-modeling move.
3. Calls `OrderCountService.increment` (the `counter` pattern).

`order_id` is a `timeuuid` generated once per order and reused across both denormalized rows and the counter, so the three writes stay identifiable as one logical order even though Cassandra gives no cross-table transaction.

### `lwt/` — StockReservationService

`UPDATE products SET stock = stock - ? WHERE id = ? IF stock >= ?` via `CassandraTemplate`'s `UpdateOptions.builder().withIfCondition(...)` (Spring Data Cassandra's LWT support) — Paxos-backed compare-and-set, Cassandra's answer to "atomic conditional update" where MongoDB would use a multi-document ACID transaction. Returns whether the write was applied; `OrderService` maps `false` to a "stock unavailable" failure.

### `consistency/` — ConsistencyDemoService

Exposes a read and a write that accept a `ConsistencyLevel` parameter (`ONE`, `QUORUM`, `ALL`), passed through via `CassandraTemplate`'s per-operation options (`QueryOptions.builder().consistencyLevel(...)`). Wraps each call with a timer and returns `ConsistencyReadResult` (value, level used, elapsed millis) so the README can show the latency/consistency trade-off concretely by hitting the same endpoint with different levels against the 3-node ring.

### `ttl/` — RecentlyViewedService

Every `ProductService.findById` call also inserts a row into `recently_viewed` `USING TTL 300` (5 minutes, configurable). A separate listing endpoint reads back only currently-live rows for a product — since Cassandra silently drops expired rows from query results, "still live" requires no extra filtering, which is itself worth calling out in the README as the point of the pattern.

### `counter/` — OrderCountService

`order_counts_by_product` is a counter table — Cassandra requires counter columns to live in a table containing only the counter(s) plus the primary key, and updates only via increment/decrement CQL, not a regular `UPDATE ... SET count = ?`. Spring Data Cassandra doesn't model counter increments through `CassandraTemplate.update`, so this service uses `CassandraTemplate.getCqlOperations().execute("UPDATE order_counts_by_product SET order_count = order_count + 1 WHERE product_id = ?", productId)` directly — the one place this module drops to raw CQL, called out in the README as a deliberate exception (same spirit as `producer/`'s ephemeral-instance carve-out in the LMAX Disruptor demo).

## API surface

Single `DemoController`, consistent with every other module in the repo:

| Endpoint | Behavior |
|---|---|
| `POST /demo/products` | Create a product; returns `Product`. |
| `GET /demo/products/{id}` | Read a product (also records a `recently_viewed` row via `ttl`); returns `Product`. |
| `PUT /demo/products/{id}` | Update a product; returns `Product`. |
| `DELETE /demo/products/{id}` | Delete a product. |
| `POST /demo/orders` | `PlaceOrderRequest` body → `lwt` decrement + `datamodeling` writes + `counter` increment; returns the placed order or 409 if stock unavailable. |
| `GET /demo/orders/by-customer/{customerId}` | Reads `orders_by_customer`; returns `List<OrderByCustomer>`. |
| `GET /demo/orders/by-product/{productId}` | Reads `orders_by_product`; returns `List<OrderByProduct>`. |
| `GET /demo/products/{id}/consistency?level=ONE\|QUORUM\|ALL` | Reads the product at the given consistency level; returns `ConsistencyReadResult`. |
| `GET /demo/products/{id}/recently-viewed` | Lists still-live `recently_viewed` rows for the product. |
| `GET /demo/products/{id}/order-count` | Reads the counter; returns the current count. |

Swagger UI at `/swagger-ui/index.html`, same as `noSQL/mongodb`.

## Testing

- Unit tests per service (`crud/`, `datamodeling/`, `lwt/`, `consistency/`, `ttl/`, `counter/`) — `@ExtendWith(MockitoExtension.class)` with `CassandraTemplate` (and, for `counter/`, `CqlOperations`) mocked via `@Mock`/`@InjectMocks`, exactly matching `noSQL/mongodb`'s `ProductServiceTest` pattern (`MongoTemplate` mocked, no live cluster). `mvn test` for this module does **not** require the Docker Compose cluster running — only `mvn gatling:test` and manual `curl` walkthroughs do.
- `DemoControllerTest` — `@WebMvcTest(DemoController.class)` + `MockMvc` + `@MockitoBean` per service, matching `noSQL/mongodb`'s own `DemoControllerTest` exactly (plain JUnit 5 — this repo's `noSQL` Java tests don't use Spock, so [[spock-spring-webmvctest-incompatibility]] doesn't apply here), one happy-path case per endpoint plus the 409-on-insufficient-stock case.
- `src/test/.../performance/DemoSimulation.java` — Gatling load test hitting all ten endpoints; excluded from `mvn test` via the inherited surefire `**/performance/**` exclude (already configured at `noSQL/pom.xml` level), run explicitly via `mvn gatling:test`.

## Ports

- `cassandra/spring-demo` app → `8085` (next free slot after `mongodb/spring-demo`'s `8084`).
- Cassandra native transport → `9042`/`9043`/`9044` (host), no collision with `message-brokers/kafka`'s `9092`–`9094` or `communication-protocols/grpc`'s `9090`.
- Cassandra JMX → `7199`/`7200`/`7201` (host, exporter sidecars only — not intended for direct host use, but exposed for troubleshooting).
- Prometheus → `9096` (no collision with `noSQL/mongodb`'s `9095`).
- Grafana → `3003` (no collision with `noSQL/mongodb`'s `3002`).

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (inherited from `noSQL/pom.xml` parent)
**Java:** 21

**`cassandra-demo` dependencies:** `spring-boot-starter-web`, `spring-boot-starter-data-cassandra`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `gatling-charts-highcharts` (test) — same set as `mongodb-demo` with `data-cassandra` swapped in for `data-mongodb`.

`application.yml`:
```yaml
spring:
  cassandra:
    contact-points: cassandra1:9042,cassandra2:9042,cassandra3:9042
    local-datacenter: datacenter1
    keyspace-name: ecommerce
    schema-action: none   # tables are created by cassandra-init, not the app

server:
  port: 8085
```

Same as the MongoDB module's `/etc/hosts` requirement, the app connects to the cluster using the containers' hostnames (`cassandra1`/`cassandra2`/`cassandra3`), so the README carries the same one-time host-setup step pointing those hostnames at `127.0.0.1`.

## README

`noSQL/cassandra/README.md` follows `noSQL/mongodb/README.md`'s structure exactly: prerequisites (Java 21, Maven, Docker, `/etc/hosts` entries), start-the-cluster instructions with a ring-status check (`nodetool status` instead of `rs.status()`), run-the-app instructions, `curl` walkthroughs for all ten endpoints grouped by pattern, a cluster-topology mermaid diagram, a patterns-and-data-flow mermaid diagram, a table characterizing tables/patterns (parallel to MongoDB's "Collection characteristics" table), monitoring section (Prometheus/Grafana URLs, dashboard panels), ring admin commands (`nodetool status`, `nodetool ring`, `cqlsh` inspection), and stop-the-cluster instructions.

## Scope limits

- No multi-datacenter topology — single datacenter (`datacenter1`), matching the demo's scope (this is about Cassandra's data-modeling and consistency primitives, not full operational topology).
- No repair/anti-entropy demo (`nodetool repair`) — out of scope; the README may mention it exists without demonstrating it.
- The `counter` pattern's raw-CQL exception is confined to `OrderCountService`; every other service uses `CassandraTemplate`/Spring Data Cassandra idioms.
- No embedded Cassandra or Testcontainers substitute is introduced for `mvn test` — unit tests mock `CassandraTemplate`, keeping this module consistent with `noSQL/mongodb`'s testing convention rather than introducing a new one.
- If `criteo/cassandra_exporter` proves unreliable during implementation, monitoring falls back to a single exporter against `cassandra1` rather than being dropped entirely — confirm this fallback with the user only if it's actually needed.

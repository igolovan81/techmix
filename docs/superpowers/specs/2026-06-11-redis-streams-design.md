# Redis Streams Cluster & Spring Demo Design

**Date:** 2026-06-11
**Status:** Approved

## Overview

A 6-node Redis Cluster (Docker) and a Spring Boot demo app covering 6 messaging patterns: simple streaming, work queue (consumer groups), fanout, pending entries & retry, stream trimming, and Redis native Pub/Sub.

## Directory Structure

```
message-brokers/
└── redis/
    ├── docker/
    │   └── docker-compose.yml
    ├── spring-demo/
    │   ├── pom.xml
    │   └── src/
    │       └── main/
    │           ├── java/com/testingai/redis/
    │           │   ├── RedisDemoApplication.java
    │           │   ├── config/
    │           │   │   ├── RedisConfig.java
    │           │   │   └── StreamKeys.java
    │           │   ├── controller/
    │           │   │   └── DemoController.java
    │           │   ├── simple/
    │           │   │   ├── SimpleProducer.java
    │           │   │   └── SimpleConsumer.java
    │           │   ├── workqueue/
    │           │   │   ├── WorkQueueProducer.java
    │           │   │   └── WorkQueueConsumer.java
    │           │   ├── fanout/
    │           │   │   ├── FanoutProducer.java
    │           │   │   ├── FanoutConsumerA.java
    │           │   │   └── FanoutConsumerB.java
    │           │   ├── pending/
    │           │   │   ├── PendingProducer.java
    │           │   │   └── PendingConsumer.java
    │           │   ├── trimming/
    │           │   │   ├── TrimmingProducer.java
    │           │   │   └── TrimmingConsumer.java
    │           │   ├── pubsub/
    │           │   │   ├── PubSubPublisher.java
    │           │   │   ├── PubSubSubscriberA.java
    │           │   │   └── PubSubSubscriberB.java
    │           │   └── util/
    │           │       └── FailureSimulator.java
    │           └── resources/
    │               └── application.yml
    └── README.md
```

## Docker Cluster

- **Image:** `redis:7.4`
- **Nodes:** 6 containers — `redis1`–`redis6` (3 masters + 3 replicas, 1 replica per master)
- **Cluster init:** one-shot `cluster-init` container using `redis/redis-stack` or plain `redis:7.4` image that runs `redis-cli --cluster create` once all nodes pass health checks, then exits
- **UI:** `redis/redisinsight:latest` at host port `5540`
- **Project name:** `redis-cluster`

### Port mapping

| Container | Host port | Role |
|---|---|---|
| redis1 | 6379 | master (shard 0) |
| redis2 | 6380 | master (shard 1) |
| redis3 | 6381 | master (shard 2) |
| redis4 | 6382 | replica of redis1 |
| redis5 | 6383 | replica of redis2 |
| redis6 | 6384 | replica of redis3 |
| redisinsight | 5540 | UI |

### `--cluster-announce-ip` pattern

Each Redis node is configured with:
```
--cluster-enabled yes
--cluster-config-file nodes.conf
--cluster-node-timeout 5000
--cluster-announce-ip 127.0.0.1
--cluster-announce-port <host-port>
--cluster-announce-bus-port 1<host-port>
--appendonly yes
```

This ensures MOVED redirects sent to clients use `127.0.0.1:<host-port>` rather than internal Docker IPs, so the Spring Boot app running on the host can follow them correctly on Mac and Windows.

### Cluster init

The `cluster-init` container waits for all 6 nodes to be reachable, then runs:
```bash
redis-cli --cluster create \
  redis1:6379 redis2:6379 redis3:6379 \
  redis4:6379 redis5:6379 redis6:6379 \
  --cluster-replicas 1 --cluster-yes
```
Cluster formation uses internal container names (`redis1`–`redis6`) so the nodes can reach each other within the Docker network. The `--cluster-announce-ip/port` settings on each node ensure that after formation, client-facing redirects use `127.0.0.1:<host-port>`.

## Messaging Patterns

| # | Pattern | Stream / channel key | Consumer group(s) | Detail |
|---|---|---|---|---|
| 1 | Simple | `{streams}:simple` | none | XADD / XREAD, no group — standalone reader |
| 2 | Work Queue | `{streams}:work` | `work-group` | Two consumers share entries; XACK on success; 5% failure via FailureSimulator |
| 3 | Fanout | `{streams}:fanout` | `group-a`, `group-b` | Both groups receive every entry independently |
| 4 | Pending & Retry | `{streams}:pending` | `pending-group` | 5% simulated failure leaves entries in PEL; `@Scheduled` reclaimer (every 3 s) uses XCLAIM to recover entries idle > 5 s |
| 5 | Stream Trimming | `{streams}:trimmed` | `trimmed-group` | XADD with `MAXLEN 100`; consumer logs stream length after each read |
| 6 | Pub/Sub | channel `demo:pubsub` | n/a | Redis native PUBLISH/SUBSCRIBE; two `MessageListener` beans both receive every message |

**Hash tag `{streams}:`** is applied to all stream keys (patterns 1–5) so they map to the same hash slot in Redis Cluster. This is a real Redis Cluster constraint: keys involved in multi-key operations (e.g., consumer groups spanning streams) must share a slot.

## REST Endpoints

All triggered via `DemoController` at `POST /demo/*`:

```
POST /demo/simple?message=hello
POST /demo/work?message=task&count=5
POST /demo/fanout?message=broadcast
POST /demo/pending?message=hello&count=3
POST /demo/trimming?message=hello
POST /demo/pubsub?message=broadcast
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Spring Boot Configuration

**Spring Boot version:** 3.4.4
**Java:** 21

**Dependencies:**
- `spring-boot-starter-data-redis` (Lettuce cluster client)
- `spring-boot-starter-web`
- `spring-boot-starter-test`
- `springdoc-openapi-starter-webmvc-ui` 2.8.6
- `lombok` 1.18.38
- `gatling-charts-highcharts` 3.13.1

**`application.yml`:**
```yaml
spring:
  data:
    redis:
      cluster:
        nodes: localhost:6379,localhost:6380,localhost:6381,localhost:6382,localhost:6383,localhost:6384
        max-redirects: 3
```

### Config classes

**`StreamKeys.java`** — holds all stream key and channel constants:
```java
public class StreamKeys {
    public static final String SIMPLE   = "{streams}:simple";
    public static final String WORK     = "{streams}:work";
    public static final String FANOUT   = "{streams}:fanout";
    public static final String PENDING  = "{streams}:pending";
    public static final String TRIMMED  = "{streams}:trimmed";
    public static final String PUBSUB_CHANNEL = "demo:pubsub";
    public static final int    TRIM_MAX_LEN   = 100;
}
```

**`RedisConfig.java`** — declares:
- `RedisTemplate<String, String>` (string serializers)
- `StreamMessageListenerContainer<String, MapRecord<String, String, String>>` — auto-started container that registers all stream listeners
- Consumer group bootstrap: on `@PostConstruct`, call `XGROUP CREATE <key> <group> $ MKSTREAM` for each stream/group pair; catch and ignore `BUSYGROUP` error if already exists
- `RedisMessageListenerContainer` for native Pub/Sub with `PubSubSubscriberA` and `PubSubSubscriberB` bound to channel `demo:pubsub`

### Stream consumer pattern

Each stream consumer implements:
```java
StreamListener<String, MapRecord<String, String, String>>
```

The `StreamMessageListenerContainer` is configured to read via `StreamOffset.create(key, ReadOffset.lastConsumed())` within the consumer group. Acknowledgment (`XACK`) is called explicitly after successful processing.

### Pending & Retry

`PendingConsumer` processes entries and calls `XACK` on success. On 5% simulated failure it skips the `XACK`, leaving the entry in the PEL.

A `@Scheduled(fixedDelay = 3000)` method in `PendingConsumer` calls:
1. `XPENDING {streams}:pending pending-group - + 10` — list entries idle > 5000 ms
2. `XCLAIM {streams}:pending pending-group pending-consumer 5000 <id>` — claim and reprocess each one

### Failure simulation

`FailureSimulator.maybeThrow(context)` — 5% probability `RuntimeException`. Applied in `WorkQueueConsumer` and `PendingConsumer`.

## Testing

- **Producer tests** — `@ExtendWith(MockitoExtension.class)`, mock `RedisTemplate`, verify `opsForStream().add()` / `convertAndSend()` calls
- **Consumer tests** — instantiate consumer directly, call `onMessage()` with a constructed `MapRecord`; mock `FailureSimulator` static for failure path
- **`DemoControllerTest`** — `@WebMvcTest(DemoController.class)` with `@MockitoBean` for all 6 producers/publishers; verifies HTTP 200 and delegation
- **`StreamKeysTest`** — verifies all constants are non-null and non-blank
- Gatling simulation at `src/test/java/com/testingai/redis/performance/DemoSimulation.java` — excluded from `mvn test` via Surefire `<excludes>`; run with `mvn gatling:test`

## README

`redis/README.md` follows the same format as `rabbitmq/README.md` and `kafka/README.md`:
prerequisites, start cluster, verify, run app, curl examples, Swagger UI link, cluster management CLI commands, RedisInsight shortcuts, stop cluster.

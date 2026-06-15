# Message Brokers — Use-Case Guide

This repository contains runnable demos for four messaging technologies. Each demo runs a realistic cluster via Docker Compose and a Spring Boot app that exercises the key patterns.

| Broker | Demo | Best fit |
|---|---|---|
| [Kafka](kafka/) | 3-broker KRaft cluster | High-throughput event streaming, audit logs, stream processing |
| [RabbitMQ](rabbitmq/) | 3-node quorum cluster | Task queues, complex routing, protocol flexibility |
| [Redis Streams](redis/) | 6-node cluster | Lightweight queuing when Redis is already in the stack |
| [Amazon SQS](sqs/) | LocalStack (SQS + SNS) | Managed cloud queuing in AWS workloads |

---

## Choosing a broker

### Use Kafka when

- **Throughput is the primary concern.** Kafka is designed for millions of messages per second. Sequential disk writes and zero-copy transfers make it fundamentally faster than brokers that copy messages through memory.
- **Messages must be replayed.** Kafka is a log, not a queue. Messages are retained for days or weeks and can be re-consumed from any offset by any consumer group — useful for audits, event sourcing, and debugging.
- **You need exactly-once delivery across producers and consumers.** Kafka's transactional API (`isolation.level=read_committed`) makes atomically publishing and consuming a batch a first-class feature.
- **Order within a key matters at scale.** Partitioning routes all messages with the same key to the same partition, so a single consumer processes them in order without locking or coordination.
- **You want stream processing co-located with messaging.** Kafka Streams runs inside your application; no separate infrastructure is needed for real-time aggregations, joins, or windowed computations.
- **Log compaction is needed.** Topics configured with `cleanup.policy=compact` retain only the latest record per key — a natural fit for maintaining a current-state view (user profiles, inventory levels) without a separate database.

**Avoid Kafka when** message count is low, you need complex per-message routing logic, or operational simplicity outweighs throughput.

---

### Use RabbitMQ when

- **Routing logic is the hard part.** RabbitMQ's exchange model (direct, fanout, topic, headers) lets the broker make delivery decisions based on routing keys or message headers. Kafka and SQS require the producer or consumer to implement this logic.
- **Task queues with acknowledgment semantics are enough.** RabbitMQ's push-based delivery with `basicAck`/`basicNack` and per-queue TTL, priority, and dead-lettering are mature and well-understood.
- **You need protocol flexibility.** RabbitMQ speaks AMQP 0-9-1, MQTT, STOMP, and WebSockets out of the box. Useful when connecting devices, browsers, or third-party systems that speak a specific protocol.
- **Message TTL and per-message priority matter.** Queue-level or message-level TTL and priority queues are built in — features Kafka and SQS do not support natively.
- **The team prefers push semantics.** Kafka consumers pull; RabbitMQ pushes with configurable prefetch. For small teams or simple workloads this reduces code complexity.

**Avoid RabbitMQ when** you need long-term message retention, log replay, or very high throughput (>100k msg/s).

---

### Use Redis Streams when

- **Redis is already in your stack.** Adding a separate message broker for low-to-moderate throughput is unnecessary if Redis is already handling caching or session state.
- **You need consumer groups without a heavyweight broker.** Redis Streams support `XREADGROUP`/`XACK` and pending-entry lists (PEL) — persistent, acknowledgment-based delivery without standing up Kafka or RabbitMQ.
- **Latency is more important than durability.** Redis operates in memory; p99 latency is sub-millisecond, which Kafka and RabbitMQ cannot match. Native pub/sub (`PUBLISH`/`SUBSCRIBE`) adds zero persistence overhead for fire-and-forget notifications.
- **Stream size must be bounded.** `XADD ... MAXLEN ~100` automatically trims old entries — useful for sliding-window event feeds (real-time activity, leaderboards) where you only care about recent data.

**Avoid Redis Streams when** you need cross-datacenter replication, guaranteed delivery with complex retry policies, or message volumes that exceed RAM capacity.

---

### Use Amazon SQS when

- **You are building on AWS and don't want to manage infrastructure.** SQS is fully serverless — no cluster to size, no brokers to patch, no disks to monitor. You pay per API call and it scales automatically.
- **Decoupling microservices in the cloud.** SQS + SNS (fan-out) is the canonical AWS pattern for event-driven architectures. IAM policies enforce access control without a separate auth layer.
- **Strict ordering within a group is required.** FIFO queues guarantee exactly-once, in-order delivery per `MessageGroupId`. Standard queues trade ordering for maximum throughput and at-least-once delivery.
- **Poison-message handling should be automatic.** SQS moves messages to a dead-letter queue after a configurable `maxReceiveCount` — no custom retry logic needed in the consumer.
- **Burst absorption matters.** SQS can buffer millions of messages during traffic spikes and drain them at whatever rate consumers can handle, without backpressure on producers.

**Avoid SQS when** you need message replay (SQS deletes messages after consumption), sub-second latency, or you are not on AWS.

---

## Messaging patterns

### Simple queue

One producer, one consumer. Point-to-point delivery. The consumer removes the message from the queue after processing.

**Use when:** a single downstream service handles a task and no fanout is needed. Examples: sending a single email, triggering a single downstream API call.

| Broker | Mechanism |
|---|---|
| Kafka | Single-partition topic with one consumer group |
| RabbitMQ | Classic queue, single consumer |
| Redis Streams | `XREAD` without a consumer group |
| SQS | Standard queue, single listener |

---

### Work queue (competing consumers)

Multiple consumers share a single queue; each message is delivered to exactly one consumer. The broker distributes work across workers.

**Use when:** tasks are CPU- or I/O-bound and can be processed independently. Scaling consumers adds throughput. Examples: image resizing, report generation, payment processing.

| Broker | Mechanism |
|---|---|
| Kafka | Multiple consumers in the same group; Kafka assigns partitions |
| RabbitMQ | Quorum queue, multiple consumers with `prefetch=1` |
| Redis Streams | Consumer group (`XREADGROUP`) — each member receives different entries |
| SQS | Standard queue, multiple `@SqsListener` beans compete for messages |

**Key difference:** Kafka work queues are partition-bound — parallelism is capped at the partition count. RabbitMQ and SQS do not have this constraint; you can add consumers freely without reconfiguring the queue.

---

### Pub/Sub (fanout)

One message is delivered to all subscribers independently. Each subscriber maintains its own position.

**Use when:** an event needs to trigger multiple independent reactions. Examples: order placed → notify inventory, billing, and shipping services simultaneously.

| Broker | Mechanism |
|---|---|
| Kafka | Multiple consumer groups on the same topic; each group reads all messages independently |
| RabbitMQ | Fanout exchange bound to multiple queues |
| Redis Streams | Multiple consumer groups on the same stream |
| SQS | SNS topic fan-out to multiple SQS queues |

**Key difference:** Kafka and Redis Streams maintain per-group offsets so late subscribers can catch up. RabbitMQ and SQS deliver only messages published after the subscriber binds — missed messages are not replayed.

---

### Routing (content-based dispatch)

Messages are routed to different consumers based on attributes — a routing key, message header, or message content.

**Use when:** one event type has multiple subtypes that need different handlers. Examples: log levels routed to different sinks (`info` → metrics, `error` → alerting + metrics); payment methods routed to different processors.

| Broker | Mechanism |
|---|---|
| Kafka | Partition by key (same-key → same partition, same consumer) |
| RabbitMQ | Direct or topic exchange with routing keys; broker makes the decision |
| Redis Streams | Application-level — consumer filters by message fields |
| SQS | SNS filter policies on subscriptions |

**Key difference:** RabbitMQ is the most expressive — topic exchanges support wildcard routing keys (`logs.*.error`). Kafka partitioning is ordering-focused, not routing-focused.

---

### Exactly-once / transactions

A batch of messages is published or consumed atomically — either all succeed or none do. Consumers with `read_committed` isolation never see uncommitted partial results.

**Use when:** consistency across multiple messages is required and duplicate processing or partial writes are unacceptable. Examples: transferring funds (debit + credit must both publish), order state machine transitions.

| Broker | Mechanism |
|---|---|
| Kafka | Transactional producer (`transaction-id-prefix`) + `isolation.level=read_committed` |
| RabbitMQ | Publisher confirms + consumer acknowledgment (at-least-once; idempotency still needed) |
| Redis Streams | `MULTI`/`EXEC` for local atomicity; no distributed transaction support |
| SQS | FIFO queues offer exactly-once within a deduplication window (content-based dedup) |

**Key difference:** Kafka is the only broker here with native distributed exactly-once guarantees spanning multiple partitions and topics.

---

### Dead-letter queue (DLQ)

Messages that fail processing repeatedly are moved to a separate queue for inspection and replay without blocking the main queue.

**Use when:** consumers can encounter poison messages (malformed data, downstream service outages) that must not block healthy messages indefinitely.

| Broker | Mechanism |
|---|---|
| Kafka | `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` — failed messages go to `<topic>.DLT` |
| RabbitMQ | DLX (dead-letter exchange) + `x-delivery-limit` on quorum queues |
| Redis Streams | Application-level — move entries from PEL to a dedicated error stream after N reclaims |
| SQS | `maxReceiveCount` on source queue + separate DLQ ARN; fully automatic |

**Key difference:** SQS handles the DLQ routing automatically with zero application code. All other brokers require some configuration or application logic.

---

### FIFO / ordered delivery

Messages are delivered in the exact order they were produced, within a logical group.

**Use when:** downstream processing is order-sensitive and parallel processing would violate invariants. Examples: user action sequences, financial ledger entries, database change events.

| Broker | Mechanism |
|---|---|
| Kafka | Per-partition ordering; same key → same partition → ordered delivery |
| RabbitMQ | Single-consumer classic queue (ordering is lost with multiple consumers or quorum queues under failure) |
| Redis Streams | Stream entries are ordered by ID; consumer groups read in insertion order |
| SQS | FIFO queue with `MessageGroupId`; in-order per group, parallel across groups |

**Key difference:** Kafka and SQS FIFO are the two production-grade choices for ordered-at-scale delivery. Kafka ordering is partition-scoped; SQS FIFO ordering is group-scoped and fully managed.

---

### Log compaction (latest value per key)

The broker retains only the most recent record for each key. Older records with the same key are garbage-collected. The result is a compacted log that always reflects current state.

**Use when:** consumers need the latest state rather than the full history. Examples: user profile cache, device telemetry (last known sensor reading), inventory levels.

| Broker | Mechanism |
|---|---|
| Kafka | `cleanup.policy=compact` on a topic; log cleaner runs in the background |
| RabbitMQ | Not supported natively |
| Redis Streams | Not applicable — use Redis Hashes/Strings for key-value semantics |
| SQS | Not supported |

**Kafka is the only broker in this set with native log compaction.**

---

### Stream processing

Real-time stateful transformations, aggregations, and joins over a continuous event stream — co-located with the messaging layer.

**Use when:** you need derived data (counts, sums, join results) updated in real time without a separate batch job. Examples: word count, fraud detection, real-time analytics dashboards.

| Broker | Mechanism |
|---|---|
| Kafka | Kafka Streams API — runs inside the application, reads input topics, writes output topics |
| RabbitMQ | No native stream processing; requires an external engine (Flink, Spark) |
| Redis Streams | Application-level aggregation in the consumer; no windowing or join primitives |
| SQS | No native stream processing; requires Lambda or Kinesis Data Analytics |

**Kafka Streams is the only embedded stream processing engine in this set.**

---

## Quick decision matrix

| Need | Kafka | RabbitMQ | Redis Streams | SQS |
|---|:---:|:---:|:---:|:---:|
| Very high throughput (>100k msg/s) | ✓ | — | ✓ | ✓ |
| Message replay / audit log | ✓ | — | limited | — |
| Complex routing (wildcards, headers) | — | ✓ | — | limited |
| Managed / no-ops | — | — | — | ✓ |
| Exactly-once delivery | ✓ | — | — | FIFO only |
| Strict ordering at scale | ✓ | — | ✓ | FIFO |
| Log compaction | ✓ | — | — | — |
| Built-in stream processing | ✓ | — | — | — |
| Protocol flexibility (MQTT, STOMP) | — | ✓ | — | — |
| Sub-millisecond latency | — | — | ✓ | — |
| Dead-letter queue (automatic) | config | config | manual | ✓ |
| Already in AWS | — | — | — | ✓ |
| Already have Redis | — | — | ✓ | — |

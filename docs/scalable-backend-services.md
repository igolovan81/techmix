# Designing Scalable Backend Services

A reference for approaching "design scalable backend services" style system design questions — general principles, not tied to a specific service in this repo.

## Start by scoping "scalable"

Before designing anything, pin down what's actually growing. A system tuned for 10x traffic looks different from one tuned for 10x data or 10x engineers shipping to it. Naming which one you're solving for is the highest-signal move to make first — it's usually what's actually being probed for.

- **Traffic (requests/sec)** — the classic case. Mostly solved with horizontal scaling: more stateless instances behind a load balancer, plus caching to keep the multiplier on the database low. The failure mode to watch for is a *hot key* — one endpoint or one entity (a viral post, a celebrity account) taking disproportionate load that adding generic capacity doesn't fix.
- **Data volume** — needs partitioning/sharding and indexing strategy, not just more app servers. A dataset that outgrows a single machine's disk or a single index's working set changes the query patterns you can afford, independent of how much traffic it's under.
- **Concurrency / connections** — distinct from raw request rate: long-lived connections (WebSockets, SSE, gRPC streams) consume a file descriptor and often a thread for the connection's lifetime, so "requests/sec" undercounts the real resource pressure. This usually forces event-loop or virtual-thread-style I/O models instead of one-thread-per-connection.
- **Geographic spread** — needs multi-region deployment, CDNs, and latency-aware routing. This is as much a consistency problem as a capacity problem (see the CAP/PACELC section below) — replicating data across regions means deciding how stale a read in Singapore is allowed to be relative to a write in Virginia.
- **Team/org size** — needs service boundaries and independent deployability, a different problem from raw throughput. This is Conway's Law in reverse: you decompose a system into services partly so that teams can ship independently, not only because any single service is overloaded. Over-applying this when the actual problem is traffic just adds network hops and operational surface for no scaling benefit.

## Core principle: statelessness enables horizontal scaling

Make application servers stateless — no session data, no local file writes, no in-memory state that outlives a single request. Once that's true, scaling is "add more identical instances behind a load balancer," which is cheap and close to unlimited. Anything stateful (sessions, uploaded files, WebSocket connections, in-memory caches that need to agree with each other) gets pushed out to a dedicated store specifically so the app tier stays disposable and instances become interchangeable — the "cattle, not pets" framing: any instance can be killed and replaced without anyone noticing.

**What "stateless" actually requires:**

- **Session data** → an external store (Redis, a database-backed session table) instead of in-process memory, so any instance can serve any request for any user.
- **File uploads** → object storage (S3-style), not local disk — local disk doesn't survive the instance being replaced and isn't visible to other instances.
- **Long-lived connections** (WebSockets) → either sticky routing at the load balancer (the connection's requests always land on the instance that holds it) or a shared pub/sub backplane (Redis, a message bus) so any instance can push a message to a connection held by another instance.

**Load balancing**, once statelessness is in place, has its own set of choices: round-robin (simple, ignores instance load), least-connections (better under uneven request cost), and consistent hashing (routes the same key to the same instance — useful for cache locality, at the cost of needing sticky-session-aware rebalancing when instances come and go). Health checks removing unhealthy instances from rotation, and auto-scaling adding/removing instances based on CPU/queue-depth/custom metrics, are what turn a static pool into an elastic one.

## Data layer — usually the actual bottleneck

App servers scale horizontally almost for free; the database doesn't. This is where the real design work happens, and where most of the hard trade-offs live.

**Read replicas.** Cheap, and most systems are read-heavy, so this is usually the first lever pulled. The catch is replication lag: a write goes to the primary, and a read immediately after can hit a replica that hasn't caught up yet — the classic "I just posted a comment and it's not showing up" bug. Mitigations: route a user's own reads to the primary for a short window after they write (read-your-own-writes), or accept the staleness where it's genuinely fine (a public feed, an analytics dashboard).

**Caching.** The highest-leverage change per unit of engineering effort — and the one with the most subtle failure modes:

- *Cache-aside* (app checks cache, falls back to DB on miss, populates cache) is the default; *write-through* (writes go to cache and DB together) keeps the cache warm at write cost; *write-behind* (writes land in cache first, DB is updated asynchronously) trades durability risk for write latency.
- **Invalidation** is genuinely the hard part — "there are only two hard things in computer science." TTL-based expiry is simple but means the cache is sometimes stale; explicit invalidation on write is fresher but means every write path has to remember to invalidate every cache key it affects, which scales badly as the number of cache consumers grows.
- **Cache stampede / thundering herd**: when a hot key expires, many concurrent requests all miss simultaneously and all hammer the database to repopulate it. Mitigated with request coalescing (only one request repopulates, others wait) or staggered TTLs so keys don't all expire in lockstep.
- Layer caches by how static the content is: CDN/edge cache for static assets and cacheable API responses (minutes-to-hours TTL), an application cache like Redis for hot entities and computed aggregates (seconds-to-minutes), and sometimes a query-result cache in front of expensive joins.

**Sharding/partitioning.** The expensive, hard-to-reverse option — only reach for it once replicas and caching are exhausted, since it complicates every cross-shard query, join, and transaction. Strategies:

- *Hash-based* (shard = hash(key) % N) spreads load evenly but makes range scans and resharding painful (changing N reshuffles almost everything — consistent hashing exists specifically to soften this).
- *Range-based* (shard by ID or date range) keeps range scans cheap but risks hot shards (all of "this week's" writes landing on one shard).
- *Directory-based* (a lookup service maps key → shard) is the most flexible and the most operational overhead — it's another stateful service that itself needs to be highly available.
- Cross-shard transactions are the recurring pain point; the usual answer is to design the shard key so that the transactions that need atomicity stay within one shard (e.g., shard an e-commerce system by customer, not by order, so "place this customer's order" never spans shards).

**Connection pooling.** A database has a hard ceiling on concurrent connections, and each connection has real memory/CPU cost on the DB side. Without pooling, horizontally scaled app instances exhaust that ceiling long before CPU or memory becomes the app-tier constraint. An external pooler (PgBouncer-style) sitting between many app instances and the database multiplexes many logical app-side connections onto fewer real database connections, and is often necessary even after each app instance has its own reasonably-sized pool.

**Indexing and query shape.** Scaling the infrastructure around a query is wasted effort if the query itself does a sequential scan. A composite index matching the actual `WHERE`/`ORDER BY` pattern, denormalizing a read-heavy join into a single table, or introducing a separate read-optimized model (CQRS — a write model optimized for consistency, a read model optimized for the actual query shape, kept in sync asynchronously) often outperforms any amount of added hardware.

**SQL vs. NoSQL** is a consistency-and-access-pattern decision, not a scale decision by itself — a well-sharded SQL database scales plenty far for most systems. Reach for NoSQL when the access pattern is genuinely key-value/document-shaped and doesn't need cross-entity transactions or ad-hoc joins, or when the write volume needs a log-structured/append-only engine (wide-column stores, time-series databases) that a traditional B-tree-indexed RDBMS isn't built for.

## Decoupling load with async processing

Not every request needs to be handled synchronously. Anything that can be deferred — sending an email, processing an upload, updating a search index, notifying a downstream system — should go through a queue instead of blocking the request path. This buys two things: it smooths traffic spikes (the queue absorbs the burst; workers drain it at a sustainable rate), and it isolates a slow downstream dependency from taking down the whole request path.

**Choosing the messaging shape:**

- A **work queue** (SQS-style, or a broker's queue semantics) is the right fit when each message should be processed by exactly one consumer — a job, a task.
- **Pub/sub** (fan-out to multiple independent consumers) is the right fit when several unrelated systems each need to react to the same event — an order-placed event triggering email, inventory update, and analytics independently.
- An **event log** (Kafka-style, append-only, consumers track their own offset) adds replayability — a new consumer can be added later and read from the beginning of history — at the cost of more operational complexity than a simple queue.

**Delivery and ordering guarantees are the recurring trap.** Most real systems offer *at-least-once* delivery, not exactly-once, because exactly-once across a network is expensive and usually not actually needed if consumers are idempotent instead (see below). Ordering is per-partition/per-key at best in most systems, not global — design consumers to not depend on cross-key ordering unless the broker specifically guarantees it and the partition key is chosen to keep the events that must stay ordered together.

**Backpressure and dead-letter queues** are what keep an async pipeline from either falling over under load or silently losing messages that can't be processed: bound the queue (or the consumer's prefetch) so a slow consumer applies backpressure upstream instead of the queue growing unbounded, and route messages that fail repeatedly to a dead-letter queue instead of retrying forever or dropping them, so a poison message doesn't block every message behind it.

**Choreography vs. orchestration** matters once a workflow spans multiple services: choreography (each service reacts to events with no central coordinator) is loosely coupled but makes the end-to-end flow hard to see in one place; orchestration (a central coordinator calls each step and manages compensation on failure) is easier to reason about and monitor but creates a coupling point. This repo's saga demo (linked below) implements both side by side over the same workflow specifically to make that trade-off concrete.

## Resilience under load — scale makes failures routine, not rare

At scale, "some percentage of calls fail" stops being an edge case and becomes a constant background condition — enough servers, enough network hops, enough dependencies that something is always partially degraded. The controls that matter:

- **Rate limiting** — protect yourself from your own callers, and protect downstream dependencies from you. Common algorithms: *token bucket* (allows bursts up to the bucket size, refills at a steady rate — usually the best default), *leaky bucket* (smooths bursts into a constant output rate), *sliding window* (more accurate than fixed-window counters, avoids the edge-of-window burst problem where two allowed bursts land back-to-back across a window boundary).
- **Circuit breakers** — stop calling a dependency that's already failing, so callers fail fast instead of piling up threads waiting on timeouts. Three states: *closed* (normal, calls pass through, failures are counted), *open* (failure rate exceeded a threshold, calls fail immediately without hitting the dependency), *half-open* (after a cooldown, a limited number of probe calls decide whether to close again or re-open).
- **Retries with exponential backoff + jitter** — jitter specifically to avoid synchronized retry storms, where many clients that failed at the same moment all retry at the same moment and re-cause the failure they were retrying from. A *retry budget* (cap total retries as a percentage of overall traffic, not per-request) prevents retries themselves from becoming the load spike that takes a struggling dependency down entirely.
- **Idempotency** — once retries exist anywhere in the system, duplicate delivery is guaranteed to happen eventually; operations need to be safe to replay. The standard mechanism is an idempotency key (client-generated or derived from the operation's natural key) that the server uses to recognize and no-op a duplicate rather than double-applying it.
- **Timeouts at every network hop** — a call with no timeout is a call that can hold a thread/connection forever the moment a dependency hangs instead of erroring; every outbound call needs one, tuned tighter than the caller's own timeout so failures surface at the right layer instead of cascading upward as a generic caller timeout.
- **Bulkheads** — isolate resource pools (thread pools, connection pools) per dependency so one slow downstream can't exhaust resources needed to talk to everything else. Named after ship hull compartments for exactly this reason: one compartment flooding shouldn't sink the ship.
- **Graceful degradation** — when a non-critical dependency is down, serve a reduced experience (cached/stale data, a default value, a hidden feature) instead of failing the whole request. Deciding in advance which dependencies are "must succeed" versus "nice to have" is design work, not an afterthought.

## Observability — you can't scale what you can't see

The three pillars: **metrics** (numeric time series — request rate, latency percentiles, error rate, resource utilization), **logs** (structured, with a correlation/trace ID threaded through every hop of a request so a single request's path is reconstructable), and **distributed traces** (the actual call graph and timing across service boundaries, essential once a request touches more than one or two services).

**Percentiles over averages.** An average latency can look fine while p99 is terrible — and p99 is usually where scaling problems actually live, because it's the tail that a small fraction of unlucky requests (often the ones hitting a cold cache, a GC pause, or a retry) experience. Alert on p95/p99, not just the mean.

**SLIs, SLOs, and error budgets** turn "is this healthy" from a vibe into a number: define a Service Level Indicator (e.g., "% of requests under 300ms"), a target (Service Level Objective, e.g., "99.9% over 30 days"), and let the gap between actual and target (the error budget) be the thing that governs how aggressively you can ship risky changes versus needing to focus on reliability.

**Alert on symptoms, not causes.** Page on "error rate is elevated" or "latency SLO is at risk," not on every individual server's CPU spiking — the latter creates alert fatigue and doesn't reliably correlate with user impact anyway. Without this instrumentation layer, capacity planning is guesswork and incident response is archaeology.

**Load testing** closes the loop — it's how a capacity model gets validated against reality before a real traffic spike does it involuntarily, and it's the way to find the actual bottleneck (which is rarely where intuition says it is) before committing to a scaling strategy.

## Load balancing and the edge

Everything above assumes traffic is already reaching the right place; the edge tier is what makes that true at scale:

- A **load balancer** (L4, routing on IP/port, cheap and fast) or **application load balancer** (L7, routing on path/host/header — enables canary releases and A/B routing at the routing layer itself) distributes traffic across the stateless instance pool described above.
- An **API gateway** adds cross-cutting concerns in front of many services at once — auth, rate limiting, request/response transformation, routing to the right backend service — so individual services don't each reimplement them.
- A **CDN** pushes static and cacheable content to edge locations near the user, cutting both latency and origin load simultaneously; this is often the single cheapest scaling win available, because it removes traffic from the system entirely rather than just handling it more efficiently.

## Service decomposition: monolith vs. microservices

This is fundamentally an organizational and blast-radius decision more than a raw-throughput one — a well-built monolith can serve enormous traffic (vertical scaling plus the horizontal/caching/data techniques above go a long way before the architecture itself is the limit). Decomposing into services buys independent deployability, independent scaling of the components that actually need more capacity, and fault isolation (one service's outage doesn't necessarily take down everything). It costs network latency on every call that used to be a function call, distributed-transaction complexity where a monolith had a local database transaction for free, and real operational overhead (more things to deploy, monitor, and keep compatible across versions). The common failure mode is decomposing prematurely, before either the team or the traffic pattern actually needs it, and paying the distributed-systems tax without the corresponding benefit.

## Multi-region and geo-distribution

Once a system needs low latency for geographically distant users or needs to survive an entire region going down, it has to decide, per piece of data, how consistency is maintained across regions: a single-writer region with read replicas elsewhere (simple, but writes from a distant region are slow and the primary region is a single point of failure), or multi-writer with conflict resolution (available and low-latency everywhere, at the cost of needing an actual strategy — last-write-wins, CRDTs, or application-level merge logic — for what happens when two regions write the same record concurrently). Most real systems mix both: strongly consistent single-writer for data where correctness matters most (payments, inventory), multi-writer/eventually-consistent for data where availability matters more (user preferences, activity feeds).

## The trade-off to name explicitly: CAP and PACELC

CAP theorem: under a network partition, a system must choose between consistency (every read sees the latest write) and availability (every request gets a response). In practice partitions are rare; the more useful everyday framing is **PACELC**: *if* Partitioned, choose Availability or Consistency — *Else* (the normal case, no partition), choose Latency or Consistency. Even with a perfectly healthy network, synchronous replication for strong consistency costs latency, and that trade-off is made on every single write, not just during rare partition events.

Being explicit about *where* a system has made this trade is what separates a real design from a buzzword list — e.g., "search results are eventually consistent because staleness is invisible to the user and availability matters more; account balance uses synchronous quorum writes because a stale read here is a correctness bug, not a UX nit." Consistency patterns worth naming by name: *strong consistency* (read-after-write, always current, usually via a single writer or synchronous quorum), *eventual consistency* (replicas converge given enough time, no guarantee on how long), *read-your-own-writes* (a middle ground — the writer sees their own write immediately, other users may not yet).

## Cost and complexity — the trade-off nobody asks about but every senior answer mentions

Every lever above has an operational and dollar cost: more replicas, more cache infrastructure, more services, more regions all mean more to run, monitor, and pay for. The strongest version of a scalability answer names not just what could be built, but what's actually justified by the stated scale — over-engineering for hypothetical 100x growth when the real problem is 3x is its own kind of failure, and calling that out explicitly is usually the difference between a design that reads as thoughtful and one that reads as a checklist of buzzwords.

## Related demos in this repo

Several of these levers are demonstrated concretely elsewhere in this repo rather than just described here:

- Async decoupling, retries, rate limiting, circuit breakers, idempotency, and dead-letter queues: [`data-integration/survey-monkey-import`](../data-integration/survey-monkey-import/)
- Distributed transaction coordination — choreography vs. orchestration: [`distributed-transactions/saga`](../distributed-transactions/saga/)
- High-throughput in-process concurrency (not a networked scaling story, but the same rate-limiting/backpressure/failure-isolation shapes at the thread level): [`concurrency-patterns/lmax-disruptor`](../concurrency-patterns/lmax-disruptor/)

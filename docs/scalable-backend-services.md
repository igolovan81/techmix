# Designing Scalable Backend Services

A reference for approaching "design scalable backend services" style system design questions — general principles, not tied to a specific service in this repo.

## Start by scoping "scalable"

Before designing anything, pin down what's actually growing. A system tuned for 10x traffic looks different from one tuned for 10x data or 10x engineers shipping to it:

- **Traffic** (requests/sec) — the classic case, solved with horizontal scaling
- **Data volume** — needs partitioning/sharding, not just more app servers
- **Geographic spread** — needs multi-region, CDNs, latency-aware routing
- **Team/org size** — needs service boundaries and independent deployability, a different problem from raw throughput

Naming which one you're solving for is the highest-signal move to make first — it's usually what's actually being probed for.

## Core principle: statelessness enables horizontal scaling

Make application servers stateless — no session data, no local file writes, no in-memory state that outlives a single request. Once that's true, scaling is "add more identical instances behind a load balancer," which is cheap and nearly unlimited. Anything stateful (sessions, uploaded files, WebSocket connections) gets pushed out to a dedicated store (Redis, object storage, a sticky-session-aware layer) specifically so the app tier stays disposable.

## Data layer — usually the actual bottleneck

App servers scale horizontally almost for free; the database doesn't. This is where the real design work happens:

- **Read replicas** for read-heavy workloads — cheap, and most systems are read-heavy
- **Caching** at multiple layers (CDN for static/edge content, an application cache like Redis for hot keys, sometimes a query-result cache) — the highest-leverage change per unit of engineering effort, but introduces staleness and invalidation problems that are genuinely hard
- **Sharding/partitioning** when a single writer can't keep up — the expensive, hard-to-reverse option; only reach for it once replicas and caching are exhausted, since it complicates every cross-shard query and transaction
- **Connection pooling** — a database has a hard ceiling on concurrent connections; without pooling, horizontally scaled app instances will exhaust it long before CPU or memory becomes the constraint

## Decoupling load with async processing

Not every request needs to be handled synchronously. Anything that can be deferred — sending an email, processing an upload, updating a search index — should go through a queue instead of blocking the request path. This buys two things: it smooths traffic spikes (the queue absorbs the burst; workers drain it at a sustainable rate), and it isolates a slow downstream dependency from taking down the whole request path.

## Resilience under load — scale makes failures routine, not rare

At scale, "some percentage of calls fail" stops being an edge case and becomes a constant background condition. The controls that matter:

- **Rate limiting** — protect yourself from your own callers, and protect downstream dependencies from you
- **Circuit breakers** — stop calling a dependency that's already failing, so you fail fast instead of piling up threads waiting on timeouts
- **Retries with exponential backoff + jitter** — jitter specifically to avoid synchronized retry storms across many clients
- **Idempotency** — once retries exist, duplicate delivery is guaranteed to happen eventually; operations need to be safe to replay
- **Bulkheads** — isolate resource pools per dependency so one slow downstream can't exhaust threads/connections needed by everything else

## Observability — you can't scale what you can't see

Metrics (latency percentiles, not just averages — p99 is where scaling problems actually live), distributed tracing across service boundaries, and structured logs with correlation IDs. Without this, capacity planning is guesswork and incident response is archaeology.

## The trade-off to name explicitly

CAP-theorem-flavored: as a system scales — especially geographically or via sharding — you trade consistency for availability/partition tolerance somewhere. Being explicit about *where* that trade has been made (e.g., "search results are eventually consistent, account balance is not") is what separates a real design from a buzzword list.

## Related demos in this repo

Several of these levers are demonstrated concretely elsewhere in this repo rather than just described here:

- Async decoupling, retries, rate limiting, circuit breakers, idempotency, and dead-letter queues: [`data-integration/survey-monkey-import`](../data-integration/survey-monkey-import/)
- Distributed transaction coordination (saga pattern): [`distributed-transactions/saga`](../distributed-transactions/saga/)
- High-throughput in-process concurrency (not a networked scaling story, but the same rate-limiting/backpressure/failure-isolation shapes at the thread level): [`concurrency-patterns/lmax-disruptor`](../concurrency-patterns/lmax-disruptor/)

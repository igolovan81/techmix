# Survey Monkey Import Demo Design

**Date:** 2026-08-26
**Status:** Draft

## Overview

A new top-level `data-integration/` category (sibling to `concurrency-patterns/`, `distributed-transactions/`), containing `survey-monkey-import/`, a two-app demo of a reliable external-API ingestion pipeline: importing survey responses from SurveyMonkey. The design is a webhook + polling hybrid — webhooks for freshness, a scheduled poll for backfill and reconciliation — and demonstrates, concretely and inspectably, the six concerns that motivated the design: **pagination, idempotency, rate limiting, retries, monitoring, and dead-letter queues**.

Two independent Spring Boot apps, talking to each other locally, following this repo's established two-app convention (`communication-protocols/grpc`'s server/client-demo, `communication-protocols/webhooks`'s producer/consumer-demo, `reactive-programming/project-reactor`'s spring-demo/upstream-demo):

- **`source-demo`** — a fake SurveyMonkey: seeded in-memory survey data, a paginated responses API mimicking SurveyMonkey's real v3 shape, on-demand failure injection (429 / 5xx / malformed payloads), and a webhook dispatcher that pushes HMAC-signed `response_completed` events to the importer.
- **`importer-demo`** — the actual subject of the design: a connector with Resilience4j-backed retry/circuit-breaker/rate-limiting, an in-process job queue with worker threads, idempotent upsert storage, a persisted dead-letter queue with redrive, a webhook receiver, a reconciliation scheduler, and Micrometer/Actuator monitoring.

No external infrastructure (no Docker, no real Kafka) — the job queue is in-process (`DelayQueue`-backed, per the earlier decision), and storage is H2, matching the no-infrastructure convention used by `distributed-transactions/`, `concurrency-patterns/`, and `template-engines/`.

## Repository structure

```
data-integration/
├── pom.xml                                          (new parent POM, mirrors concurrency-patterns/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview)
└── survey-monkey-import/
    ├── source-demo/
    │   ├── pom.xml                                  (artifactId: survey-source-demo)
    │   ├── README.md
    │   └── src/
    │       ├── main/java/com/testingai/surveysource/
    │       │   ├── SurveySourceApplication.java
    │       │   ├── domain/
    │       │   │   ├── SourceSurveyResponse.java     (record: id, surveyId, dateModified, List<Answer>)
    │       │   │   ├── Answer.java                   (record: questionId, text)
    │       │   │   ├── ResponsesPage.java             (record: data, page, perPage, total, Links)
    │       │   │   ├── Links.java                     (record: next — nullable URL string)
    │       │   │   ├── FailureMode.java                (enum: NONE, RATE_LIMIT, SERVER_ERROR, MALFORMED)
    │       │   │   └── FailureConfig.java               (record: mode, rate)
    │       │   ├── seed/
    │       │   │   └── SeedDataService.java           (@PostConstruct: generates surveys + responses)
    │       │   ├── failure/
    │       │   │   └── FailureInjector.java            (AtomicReference<FailureConfig>; decides per-request whether to inject)
    │       │   ├── webhook/
    │       │   │   └── WebhookDispatcher.java           (HMAC-signs + POSTs to importer's webhook URL)
    │       │   └── controller/
    │       │       ├── ResponsesController.java         (GET /v3/surveys/{id}/responses/bulk)
    │       │       └── AdminController.java              (POST /admin/failure-mode, POST /admin/webhooks/trigger)
    │       └── main/resources/application.yml           (server.port: 8101; importer webhook URL + shared secret)
    │       └── test/... (see Testing)
    └── importer-demo/
        ├── pom.xml                                  (artifactId: survey-importer-demo; adds resilience4j-spring-boot3, spring-boot-starter-aop, micrometer-registry-prometheus)
        ├── README.md
        └── src/
            ├── main/java/com/testingai/surveyimporter/
            │   ├── SurveyImporterApplication.java
            │   ├── domain/
            │   │   ├── SyncJob.java                    (record: id, surveyId, kind, cursor, responseId, triggerType, attemptCount, nextAttemptAt)
            │   │   ├── JobKind.java                     (enum: PAGE_SYNC, SINGLE_RESPONSE_SYNC)
            │   │   └── TriggerType.java                 (enum: SCHEDULED, WEBHOOK, MANUAL)
            │   ├── entity/
            │   │   ├── SurveyResponseEntity.java         (JPA; unique survey_id+response_id)
            │   │   ├── SyncWatermarkEntity.java           (JPA; @Id surveyId, lastSyncedAt)
            │   │   └── DeadLetterJobEntity.java            (JPA)
            │   ├── client/
            │   │   ├── SurveyMonkeyClient.java             (RestClient wrapper; @Retry/@CircuitBreaker/@RateLimiter)
            │   │   ├── RetryableSyncException.java          (transient: 429/5xx/timeout)
            │   │   └── PermanentSyncException.java          (non-retryable: 4xx other than 429, malformed page)
            │   ├── queue/
            │   │   ├── JobQueue.java                       (DelayQueue<DelayedSyncJob> wrapper)
            │   │   ├── DelayedSyncJob.java                  (implements Delayed)
            │   │   └── SyncWorkerPool.java                  (fixed thread pool; @PostConstruct start / @PreDestroy shutdown)
            │   ├── connector/
            │   │   └── ConnectorService.java                (processPage / processSingleResponse; enqueues continuations)
            │   ├── storage/
            │   │   ├── SurveyResponseRepository.java         (Spring Data JPA + native upsert queries)
            │   │   ├── SyncWatermarkRepository.java
            │   │   └── UpsertService.java                    (update-if-newer, else insert-if-absent)
            │   ├── dlq/
            │   │   ├── DeadLetterJobRepository.java
            │   │   ├── DeadLetterService.java                (persist / redrive)
            │   │   └── DlqController.java                    (GET /demo/dlq, POST /demo/dlq/{id}/redrive)
            │   ├── webhook/
            │   │   ├── WebhookSignatureVerifier.java          (HMAC-SHA256)
            │   │   └── WebhookController.java                 (POST /webhooks/surveymonkey)
            │   ├── scheduler/
            │   │   └── SyncScheduler.java                     (@Scheduled — enqueues PAGE_SYNC per known survey)
            │   ├── monitoring/
            │   │   ├── SyncMetrics.java                       (Micrometer counters/timers/gauges)
            │   │   └── DemoStatusController.java               (GET /demo/status)
            │   └── controller/
            │       └── DemoController.java                     (POST /demo/surveys/{id}/sync, GET /demo/surveys/{id}/responses)
            ├── main/resources/application.yml                  (server.port: 8102; resilience4j config; webhook secret; known survey IDs)
            └── test/... (see Testing)
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep to also match `^data-integration/.*\.java$`, and add a matching `mvn spotless:apply` block run from `data-integration/`.
- **`CLAUDE.md`** — add a "Survey Monkey import demo" command section (two apps, run source-demo before importer-demo), a `data-integration/` row in the repository layout table.
- **`README.md`** (repo root) — add a `data-integration/` row to the layout table.

## Domain model

### source-demo

```java
public record SourceSurveyResponse(String id, String surveyId, Instant dateModified, List<Answer> answers) {}
public record Answer(String questionId, String text) {}
public record ResponsesPage(List<SourceSurveyResponse> data, int page, int perPage, int total, Links links) {}
public record Links(String next) {}
public enum FailureMode { NONE, RATE_LIMIT, SERVER_ERROR, MALFORMED }
public record FailureConfig(FailureMode mode, double rate) {}
```

`SeedDataService` generates 3 surveys with deterministic IDs (`survey-1`, `survey-2`, `survey-3`) × 250 responses each at startup (`per_page` defaults to 25, so a full backfill walks 10 pages per survey — enough to make continuation-job pagination visible in a live walkthrough, not just a single page). `dateModified` values are spread across the last 30 days so `start_modified_at` filtering on delta polls is meaningful. The same three IDs are what `importer-demo`'s `SyncScheduler` is configured with (see Reconciliation below) — the two apps' seed/config are kept in sync by using the same literal IDs, not by any runtime discovery mechanism.

### importer-demo

```java
public record SyncJob(UUID id, String surveyId, JobKind kind, String cursor, String responseId,
        TriggerType triggerType, int attemptCount, Instant nextAttemptAt) {}
public enum JobKind { PAGE_SYNC, SINGLE_RESPONSE_SYNC }
public enum TriggerType { SCHEDULED, WEBHOOK, MANUAL }
```

`SyncJob` is immutable; requeueing with an incremented attempt count or later `nextAttemptAt` creates a new record via `SyncJob`'s canonical constructor, never mutates in place.

```java
@Entity @Table(name = "survey_response", uniqueConstraints = @UniqueConstraint(columnNames = {"survey_id", "response_id"}))
class SurveyResponseEntity { Long id; String surveyId; String responseId; Instant dateModified; String payload; Instant importedAt; }

@Entity @Table(name = "sync_watermark")
class SyncWatermarkEntity { @Id String surveyId; Instant lastSyncedAt; }

@Entity @Table(name = "dead_letter_job")
class DeadLetterJobEntity { @Id @GeneratedValue Long id; String surveyId; String kind; String cursor; String responseId;
        String triggerType; int attemptCount; String errorClass; String errorMessage; Instant createdAt; Instant lastAttemptAt; }
```

## Component implementations, mapped to the six concerns

### Pagination — `ConnectorService` + `JobQueue`

`ConnectorService.processPage(SyncJob job)` fetches **exactly one page** via `SurveyMonkeyClient.fetchResponsesPage(surveyId, cursor, startModifiedAt)`. If `ResponsesPage.links().next()` is non-null, it builds a continuation `SyncJob` (same `surveyId`/`triggerType`, `cursor` = the next page token, `attemptCount` = 0) and pushes it back onto `JobQueue`. If `next()` is null, this was the last page of the pass, so `SyncWatermarkEntity.lastSyncedAt` is updated to `now()`. Because each page is its own queued unit of work, a crash between pages loses at most the in-flight page (which the scheduler's next reconciliation pass will re-cover) — there is no in-memory recursive loop holding state across an entire survey's pagination.

### Idempotency — `UpsertService`

```java
public record SurveyResponseUpsert(String surveyId, String responseId, Instant dateModified, String payload) {}
```

`UpsertService.upsert(SurveyResponseUpsert)` runs two native-SQL steps inside one `@Transactional` method:

1. `UPDATE survey_response SET date_modified = ?, payload = ?, imported_at = ? WHERE survey_id = ? AND response_id = ? AND date_modified < ?` — updates only if the incoming record is strictly newer than what's stored; a replayed or out-of-order-arriving write is a safe no-op.
2. If step 1 affected zero rows, attempt an insert. If a concurrent writer already inserted the row (unique constraint on `survey_id, response_id`), catch `DataIntegrityViolationException` and treat it as a no-op — the row exists and this writer's data was not strictly newer anyway.

This makes replaying a job (queue redelivery), receiving the same response from both a webhook-triggered `SINGLE_RESPONSE_SYNC` and a scheduled `PAGE_SYNC`, or reprocessing a redriven DLQ entry all converge to the same stored row, regardless of delivery order.

### Rate limiting — Resilience4j `RateLimiter`

A single named Resilience4j rate limiter instance (`surveyMonkey`) wraps every `SurveyMonkeyClient` call via `@RateLimiter(name = "surveyMonkey")`. Because the instance is a shared Spring-managed bean, every worker thread — regardless of which job is executing — draws from the same budget, matching the real constraint that SurveyMonkey rate-limits per account, not per caller. Configured deliberately low (`limit-for-period: 10`, `limit-refresh-period: 5s`) so a backfill of 3×10 pages visibly throttles within a short demo run rather than completing instantly.

### Retries — Resilience4j `@Retry` + job-level redelivery

Two independent retry layers, deliberately not conflated:

- **HTTP-call level**: `@Retry(name = "surveyMonkey")` on `SurveyMonkeyClient`, retrying only `RetryableSyncException` (429, 5xx, timeout/connection errors) with exponential backoff and jitter (`wait-duration: 500ms`, `exponential-backoff-multiplier: 2`, `randomized-wait-factor: 0.5`), capped at 3 attempts.
- **Job level**: if a job still fails after the client's retries are exhausted (or throws any other unexpected exception), `SyncWorkerPool` requeues the *job* itself with `attemptCount + 1` and a `nextAttemptAt` delay (`DelayQueue` enforces the delay natively), up to 5 total job attempts before moving to the DLQ. `PermanentSyncException` (4xx other than 429, or a malformed page — see Scope limits) skips job-level retry entirely and routes straight to the DLQ.

### Circuit breaker — Resilience4j `@CircuitBreaker`

`@CircuitBreaker(name = "surveyMonkey")` wraps `SurveyMonkeyClient`, using a sliding window of the last 10 calls and a 50% failure-rate threshold to trip open. While open, calls fail fast with `CallNotPermittedException` (mapped to `RetryableSyncException`, so the job-level redelivery path still applies) rather than consuming a rate-limiter permit on a call likely to fail. After a 5-second wait, the breaker half-opens and permits 3 probe calls before deciding to close or re-open.

### Dead-letter queue — `DeadLetterService` + `DlqController`

A job is dead-lettered when: (a) job-level attempts are exhausted, or (b) a `PermanentSyncException` is thrown. `DeadLetterService.deadLetter(job, exception)` persists a `DeadLetterJobEntity` with full context (survey, cursor/responseId, kind, trigger type, attempt count, exception class + message, timestamps). `GET /demo/dlq` lists entries; `POST /demo/dlq/{id}/redrive` reconstructs a fresh `SyncJob` (`attemptCount` reset to 0) from the entity, pushes it onto `JobQueue`, and deletes the DLQ row.

### Monitoring — Micrometer + Actuator + `DemoStatusController`

`SyncMetrics` registers, via the injected `MeterRegistry`:

- `Counter sync.jobs.processed` tagged `outcome={success,retried,dead_lettered}`
- `Counter surveymonkey.api.calls` tagged `status={2xx,429,5xx,other}`
- `Gauge sync.queue.depth` — `JobQueue` size
- `Gauge sync.dlq.size` — `DeadLetterJobRepository.count()`
- `Gauge sync.lag.seconds` per survey — `now() - watermark.lastSyncedAt`, one gauge per known survey ID, registered at startup

These are exposed at `/actuator/metrics` and `/actuator/prometheus` (via `micrometer-registry-prometheus`) — concretely demonstrating "metrics are scrapeable" without needing a running Prometheus/Grafana stack in this demo. `GET /demo/status` returns a human-readable `DemoStatusResponse` (per-survey lag, queue depth, DLQ size, circuit-breaker state read from `CircuitBreakerRegistry`) for the walkthrough, since reading raw Micrometer output isn't a good live-demo experience.

## Webhook path (freshness)

`source-demo`'s `AdminController.POST /admin/webhooks/trigger?surveyId=&responseId=` builds a `{surveyId, responseId, eventType: "response_completed"}` payload, computes an HMAC-SHA256 signature over the raw body using a shared secret (`application.yml` property, identical value configured in both apps — clearly a demo secret, not a production credential), and POSTs it once to the importer's `/webhooks/surveymonkey` with header `X-SurveyMonkey-Signature: sha256=<hex>`. No delivery retry on the source side — that's deliberately out of scope here (see Scope limits); the interesting retry/redelivery story is entirely on the importer's *inbound* processing side, which is already covered by the job queue.

`importer-demo`'s `WebhookController` verifies the signature via `WebhookSignatureVerifier`, returns `401` on mismatch, and on success enqueues a `SyncJob{kind: SINGLE_RESPONSE_SYNC, responseId, triggerType: WEBHOOK}`. `ConnectorService.processSingleResponse` calls `SurveyMonkeyClient.fetchSingleResponse(surveyId, responseId)` (a second `source-demo` endpoint, `GET /v3/surveys/{id}/responses/{responseId}`) and upserts just that one response — cheap, and the concrete mechanism by which "webhooks collapse rate-limit cost" versus re-polling a whole survey.

## Reconciliation (no separate component)

`SyncScheduler` periodically (`@Scheduled(fixedDelayString = "${scheduler.fixed-delay:60000}")`) enqueues one `PAGE_SYNC` job per known survey ID (a small fixed list in `importer-demo`'s `application.yml`, matching `source-demo`'s seeded surveys), using the stored watermark's `lastSyncedAt` as `start_modified_at` if present. Because upserts are idempotent, a response that arrived via a missed or failed webhook simply gets picked up on the next scheduled pass — there is no separate "count comparison" reconciliation job. This scheduled path is also what performs the very first full backfill (no watermark yet → no `start_modified_at` filter → walks every page).

## API surface

**source-demo** (`http://localhost:8101`):

| Endpoint | Behavior |
|---|---|
| `GET /v3/surveys/{surveyId}/responses/bulk?page=&per_page=&start_modified_at=` | Paginated responses, subject to the active `FailureConfig`. |
| `GET /v3/surveys/{surveyId}/responses/{responseId}` | Single-response fetch (used by webhook-triggered syncs). |
| `POST /admin/failure-mode` | Body: `FailureConfig`. Sets the active injected-failure behavior. |
| `GET /admin/failure-mode` | Returns the active `FailureConfig`. |
| `POST /admin/webhooks/trigger?surveyId=&responseId=` | Fires one HMAC-signed webhook at the importer. |

**importer-demo** (`http://localhost:8102`):

| Endpoint | Behavior |
|---|---|
| `POST /demo/surveys/{surveyId}/sync` | Enqueues a manual `PAGE_SYNC` job (full backfill, ignoring the watermark). |
| `POST /webhooks/surveymonkey` | Webhook receiver — HMAC-verified. |
| `GET /demo/surveys/{surveyId}/responses` | Lists imported responses (verifies idempotent storage). |
| `GET /demo/status` | Monitoring summary. |
| `GET /demo/dlq` | Lists dead-lettered jobs. |
| `POST /demo/dlq/{id}/redrive` | Redrives one DLQ entry. |

Swagger UI on both apps at `/swagger-ui/index.html`.

## Testing

**source-demo:**
- `SeedDataServiceTest` — generates the expected survey/response counts.
- `ResponsesControllerTest` (MockMvc) — pagination correctness (page/per_page/links.next), and that each `FailureMode` produces the expected status code / payload shape at a mocked-deterministic rate (rate forced to 1.0 in tests, not left probabilistic).
- `WebhookDispatcherTest` — HMAC signature is computed correctly over a known payload+secret (a fixed test vector, so the test doesn't depend on the importer being up).

**importer-demo:**
- `SurveyMonkeyClientTest` — the status/body → `RetryableSyncException`/`PermanentSyncException` classification is a pure function, unit tested directly without needing real HTTP (avoids introducing a new mocking-HTTP dependency like WireMock; the client is tested against a mocked `RestClient` bean at the classification-logic level, matching how other modules test service layers against mocked collaborators).
- `ConnectorServiceTest` (mocked `SurveyMonkeyClient`) — single page with no `next` updates the watermark; a page with `next` enqueues a continuation job with the right cursor; a page failing validation throws `PermanentSyncException`.
- `UpsertServiceTest` — the idempotency test: insert-when-absent, update-when-newer, no-op-when-older-or-equal, and a simulated concurrent-insert race (two upserts for the same key racing) both converge to one row.
- `SyncWorkerPoolTest` — a `RetryableSyncException` requeues with `attemptCount + 1` and a later `nextAttemptAt`; exceeding max attempts dead-letters; a `PermanentSyncException` dead-letters immediately without requeue.
- `WebhookControllerTest` (MockMvc) — valid signature enqueues a job and returns `200`; invalid signature returns `401` and enqueues nothing.
- `DlqControllerTest` (MockMvc) — list reflects persisted entries; redrive re-enqueues (verified via a mocked `JobQueue`) and removes the entity.
- `DemoStatusControllerTest` (MockMvc) — reflects queue depth / DLQ size / circuit-breaker state from mocked collaborators.
- `src/test/.../performance/DemoSimulation.java` — Gatling, hitting `POST /demo/surveys/{id}/sync` and `GET /demo/status` against a running `importer-demo` (with `source-demo` also running); excluded from `mvn test` via the inherited `**/performance/**` surefire exclude.

## Ports

- `source-demo` → `8101`
- `importer-demo` → `8102`
(next free slots after `concurrency-patterns/lmax-disruptor`'s `8100`)

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`survey-source-demo` dependencies:** `spring-boot-starter-web`, `springdoc-openapi-starter-webmvc-ui`, `lombok`, test deps as usual. No persistence — all seeded data is in-memory (`ConcurrentHashMap`).

**`survey-importer-demo` dependencies:** `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `com.h2database:h2`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `io.github.resilience4j:resilience4j-spring-boot3`, `spring-boot-starter-aop` (required for Resilience4j's annotation-based decorators), `springdoc-openapi-starter-webmvc-ui`, `lombok`, test deps as usual.

## README

`data-integration/survey-monkey-import/README.md` (category-level, since there's currently one module in it) documents: the two-app topology and how to run them together (`source-demo` first, then `importer-demo`), a walkthrough exercising each of the six concerns explicitly — e.g. `POST /admin/failure-mode {mode: RATE_LIMIT, rate: 1.0}` then trigger a sync and watch `/demo/status` show retries and eventually the circuit breaker open; `POST /admin/failure-mode {mode: MALFORMED, rate: 1.0}` then watch a job land in `/demo/dlq` and redrive it; trigger a webhook and observe a response imported without a full-survey poll. Includes Swagger UI links and Gatling instructions for `importer-demo`.

## Scope limits

- In-process job queue (`DelayQueue`) — jobs mid-flight are lost on a crash. This is recoverable (the watermark + scheduled reconciliation pick up any gap on the next pass, and idempotent upserts make replays safe) but is not the durability a real broker (Kafka/SQS) would provide; explicitly called out in the README as the direct tradeoff of the earlier no-Docker decision, not an oversight.
- Page-level validation granularity: if any response within a fetched page fails validation (missing required field), the **whole page** is treated as a `PermanentSyncException` and dead-lettered, rather than importing the valid responses and dead-lettering only the malformed one. Keeps the "one job = one page" model simple; a redrive re-fetches and re-validates the same page.
- `source-demo`'s webhook dispatch is single-attempt, no retry — the delivery-retry story for *outbound* webhooks is already the subject of `communication-protocols/webhooks`; this demo's retry/DLQ focus is entirely on the importer's own processing.
- No real SurveyMonkey OAuth/API-key handling — `source-demo` simulates only the data-shape and rate-limit/error behavior relevant to this design, not SurveyMonkey's actual authentication.
- No real dashboard — metrics are exposed via Actuator/Prometheus-format endpoints, not visualized; no Grafana/Prometheus server is stood up.
- Single JVM per app — `importer-demo`'s "workers" are threads in one process, not independently-scalable instances; that horizontal-scaling story is what a real broker-backed queue would enable, and is out of scope here per the earlier in-process-queue decision.

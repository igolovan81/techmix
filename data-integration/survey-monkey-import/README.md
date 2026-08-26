# Survey Monkey Import Demo

A two-app demonstration of a reliable external-API ingestion pipeline: importing survey responses from SurveyMonkey. The design is a webhook + polling hybrid — webhooks for freshness, a scheduled poll for backfill and reconciliation — and demonstrates concretely the six concerns that motivate it: **pagination, idempotency, rate limiting, retries, monitoring, and dead-letter queues**.

## Apps

| App | Port | Role |
|---|---|---|
| [`source-demo`](source-demo/) | `8101` | A fake SurveyMonkey — seeded survey data, a paginated responses API, on-demand failure injection, HMAC-signed webhook dispatch |
| [`importer-demo`](importer-demo/) | `8102` | The actual subject of the design — connector, Resilience4j retry/circuit-breaker/rate-limiter, in-process job queue, idempotent storage, dead-letter queue, webhook receiver, scheduler, monitoring |

## Architecture

The two apps talk over plain HTTP — `source-demo` is polled for pages/single responses and pushes webhooks; `importer-demo` never talks to anything else:

```
source-demo (:8101) — "fake SurveyMonkey"          importer-demo (:8102) — "the pipeline"

GET /v3/surveys/{id}/responses/bulk        ◀── polled by ── SurveyMonkeyClient
GET /v3/surveys/{id}/responses/{id}                          (Resilience4j: retry,
                                                                circuit breaker, rate limiter)

POST /admin/webhooks/trigger  ── HMAC-signed webhook ──▶  POST /webhooks/surveymonkey

POST /admin/failure-mode  (injects 429 / 5xx / malformed data into the polled responses above)
```

Inside `importer-demo`, both trigger paths feed one queue, and one worker pool drives every job through the same connector — the "one job = one page" model that makes pagination resumable and retries safe:

```
SyncScheduler (cron)              WebhookController (HMAC-verified)
        │                                   │
        ▼                                   ▼
              JobQueue  (DelayQueue — redelivery with backoff)
                        │
                        ▼  take()
               SyncWorkerPool (3 worker threads)
                        │  process(job)
                        ▼
               ConnectorService ──▶ SurveyMonkeyClient ──▶ source-demo
                        │
                        ▼  idempotent upsert (survey_id + response_id,
                        │   only applied if incoming date_modified is newer)
               UpsertService ──▶ H2 survey_response table
                        │
        ┌───────────────┴────────────────┐
        ▼ retryable                       ▼ permanent, or attempts exhausted
re-enqueue with backoff             DeadLetterService ──▶ dead_letter_job table
   (back into JobQueue)                     │
                                             ▼  POST /demo/dlq/{id}/redrive
                                    re-enqueued into JobQueue (attempts reset to 0)
```

A page with a `next` link enqueues a continuation job with that cursor instead of looping in-process — the same queue and worker pool that handle a fresh backfill also handle "the next page of this survey," which is what makes a crash mid-pagination merely resume later rather than lose progress. `SyncMetrics` and `DemoStatusController` observe the same `JobQueue`/DLQ/circuit-breaker state shown above and expose it via `GET /demo/status` and `/actuator/prometheus`.

## Prerequisites

- Java 21
- Maven

No Docker/external infrastructure required — everything runs in-process, `source-demo` and `importer-demo` talk to each other over plain HTTP on localhost.

## Running

Start `source-demo` first, then `importer-demo`:

```bash
cd source-demo && mvn spring-boot:run
# in another terminal
cd importer-demo && mvn spring-boot:run
```

Swagger UI: `http://localhost:8101/swagger-ui/index.html` and `http://localhost:8102/swagger-ui/index.html`.

## Walkthrough — all six concerns

**Pagination** — a full backfill walks 10 pages per survey (25 responses/page, 250 seeded per survey), one queued job per page:

```bash
curl -X POST http://localhost:8102/demo/surveys/survey-1/sync
sleep 2
curl http://localhost:8102/demo/surveys/survey-1/responses | jq 'length'   # 250
```

**Idempotency** — trigger the same sync again; the response count doesn't change and no duplicates are created (upserts are keyed on `survey_id`+`response_id`, only applied when the incoming record is newer):

```bash
curl -X POST http://localhost:8102/demo/surveys/survey-1/sync
sleep 2
curl http://localhost:8102/demo/surveys/survey-1/responses | jq 'length'   # still 250
```

**Rate limiting + retries + circuit breaker** — force every call to fail with 429, trigger a sync, and watch the client retry with backoff, then the circuit breaker trip open:

```bash
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"RATE_LIMIT","rate":1.0}'
curl -X POST http://localhost:8102/demo/surveys/survey-2/sync
curl http://localhost:8102/demo/status | jq '.circuit_breaker_state'   # OPEN after enough failures
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"NONE","rate":0.0}'
```

**Dead-letter queue** — force malformed data, watch a job land in the DLQ, then redrive it once the failure mode is cleared:

```bash
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"MALFORMED","rate":1.0}'
curl -X POST http://localhost:8102/demo/surveys/survey-3/sync
sleep 2
curl http://localhost:8102/demo/dlq | jq '.[0].id'
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"NONE","rate":0.0}'
curl -X POST http://localhost:8102/demo/dlq/1/redrive
```

**Webhooks (freshness)** — push one response without a full-survey poll:

```bash
curl -X POST "http://localhost:8101/admin/webhooks/trigger?surveyId=survey-1&responseId=survey-1-response-0"
```

**Monitoring**:

```bash
curl http://localhost:8102/demo/status
curl http://localhost:8102/actuator/metrics/sync.jobs.processed
curl http://localhost:8102/actuator/prometheus
```

## Testing

```bash
mvn test                # unit tests for both apps (Gatling excluded automatically)
mvn test -Dtest=ClassName
mvn gatling:test -pl survey-monkey-import/importer-demo   # load test — requires both apps running first
```

## Scope limits

- In-process job queue — jobs mid-flight are lost on a crash. Recoverable via the watermark + scheduled reconciliation and idempotent upserts, but not the durability a real broker would provide.
- Page-level validation granularity — one malformed response in a page dead-letters the whole page, not just that response.
- `source-demo`'s webhook dispatch is single-attempt, no retry — outbound webhook delivery retry is already the subject of `communication-protocols/webhooks`.
- No real SurveyMonkey OAuth — `source-demo` simulates only the data shape and failure behavior relevant to this design.
- No real dashboard — metrics are exposed via Actuator/Prometheus-format endpoints, not visualized.
- Single JVM per app — `importer-demo`'s workers are threads in one process, not independently-scalable instances.

# Spring Batch Demo Design

**Date:** 2026-08-27
**Status:** Draft

## Overview

A new top-level `batch-processing/` category (sibling to `concurrency-patterns/` and `distributed-transactions/`), containing a single Spring Boot demo app — `spring-batch/spring-demo` — that demonstrates Spring Batch 5.x (bundled with Spring Boot 3.4.4) against an order-invoicing domain: a nightly billing run that reads pending orders and writes invoices. No external infrastructure required — H2 only, matching `distributed-transactions/saga` and `concurrency-patterns/lmax-disruptor`.

Six patterns are covered across five job configurations (listeners attach to the chunk job rather than needing a duplicate job), each independently triggerable via its own REST endpoint:

- **chunk** — the core chunk-oriented ETL step: `JdbcCursorItemReader<Order>` → `ItemProcessor` computes an `Invoice` → a custom `ItemWriter` inserts the invoice and flips the order to `INVOICED`, all inside the chunk's transaction
- **listener** — a `JobExecutionListener` + `StepExecutionListener` attached to the chunk job, capturing timing/read/write counts into an in-memory recorder
- **tasklet** — a standalone job with one `Tasklet` step (counts invoices, logs a summary) — contrasted with the chunk step's per-item processing
- **faulttolerant** — a step configured `.faultTolerant().skip(...).retry(...)`, with the processor calling `FailureSimulator.maybeThrow(...)` (this repo's established 5%-rate convention) to trigger real skips/retries
- **restart** — a step that deterministically fails once per `runId`; relaunching with the *same* `runId` (identical `JobParameters`) resumes from the last completed chunk via Spring Batch's own restart support
- **partition** — a `Partitioner` splits orders into `gridSize` id-ranges, each processed by its own chunk step instance via `TaskExecutorPartitionHandler` — Spring Batch's scale-out pattern

## Repository structure

```
batch-processing/
├── pom.xml                                          (new parent POM, mirrors distributed-transactions/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview: pattern table)
└── spring-batch/
    └── spring-demo/
        ├── pom.xml                                  (artifactId: spring-batch-demo)
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/batch/
            │   │   ├── BatchDemoApplication.java
            │   │   ├── domain/
            │   │   │   ├── Order.java                    (POJO: id, batchType, customerId, amount, status, createdAt)
            │   │   │   ├── Invoice.java                   (POJO: id, orderId, customerId, amount, tax, total, createdAt)
            │   │   │   ├── OrderStatus.java                (enum: PENDING, INVOICED)
            │   │   │   ├── BatchType.java                  (enum: CHUNK, FAULT_TOLERANT, RESTART, PARTITION)
            │   │   │   ├── OrderRowMapper.java              (implements RowMapper<Order>)
            │   │   │   └── InvoiceCalculator.java            (static: Order -> Invoice, tax = amount * 0.08)
            │   │   ├── seed/
            │   │   │   └── OrderSeedService.java            (JdbcTemplate.batchUpdate — generates N random PENDING orders of a given BatchType)
            │   │   ├── chunk/
            │   │   │   ├── InvoiceProcessor.java             (ItemProcessor<Order,Invoice>, delegates to InvoiceCalculator)
            │   │   │   ├── InvoiceItemWriter.java             (ItemWriter<Invoice> — inserts invoices + updates orders.status, shared by chunk/faulttolerant/partition)
            │   │   │   └── ChunkJobConfig.java                 (orderReader/chunkStep/invoiceChunkJob beans; wires the listener beans)
            │   │   ├── listener/
            │   │   │   ├── ListenerStats.java                 (record: jobName, status, startTime, endTime, durationMillis, readCount, writeCount, skipCount)
            │   │   │   ├── ListenerStatsService.java           (AtomicReference<ListenerStats>, getLatest()/record(...))
            │   │   │   ├── InvoiceJobListener.java              (JobExecutionListener)
            │   │   │   └── InvoiceStepListener.java             (StepExecutionListener)
            │   │   ├── tasklet/
            │   │   │   ├── ArchiveSummaryTasklet.java           (Tasklet — counts invoices, logs summary)
            │   │   │   └── TaskletJobConfig.java                 (archiveSummaryStep/archiveSummaryJob beans)
            │   │   ├── faulttolerant/
            │   │   │   ├── FaultTolerantProcessor.java           (ItemProcessor<Order,Invoice> — FailureSimulator.maybeThrow(...) then InvoiceCalculator)
            │   │   │   └── FaultTolerantJobConfig.java            (step with .faultTolerant().skip(...).retry(...))
            │   │   ├── restart/
            │   │   │   ├── RestartFailureTracker.java            (ConcurrentHashMap<String, AtomicBoolean> — shouldFailNow(runId), fails only the first call per runId)
            │   │   │   ├── RestartProcessor.java                  (@StepScope ItemProcessor — throws once per runId via the tracker)
            │   │   │   └── RestartJobConfig.java                  (plain step, no fault tolerance — a thrown exception fails the step for real restart semantics)
            │   │   ├── partition/
            │   │   │   ├── OrderRangePartitioner.java             (Partitioner — splits MIN(id)..MAX(id) into gridSize ranges)
            │   │   │   └── PartitionJobConfig.java                 (@StepScope ranged reader + partitioned master step + partitionedInvoiceJob)
            │   │   ├── launch/
            │   │   │   ├── JobRunResult.java                     (record: jobExecutionId, jobName, status, readCount, writeCount, skipCount, durationMillis)
            │   │   │   └── BatchLaunchService.java                (JobLauncher.run(job, params) -> JobRunResult, aggregating StepExecution counts)
            │   │   ├── util/
            │   │   │   └── FailureSimulator.java                 (FAILURE_RATE = 0.05; maybeThrow(String context) — Kafka-module convention)
            │   │   └── controller/
            │   │       └── DemoController.java                   (all endpoints below)
            │   └── resources/
            │       ├── application.yml                          (server.port: 8103; spring.batch.job.enabled: false; spring.batch.jdbc.initialize-schema: always; H2 URL with DB_CLOSE_DELAY=-1)
            │       └── schema.sql                                (CREATE TABLE orders, invoices — auto-run by Spring Boot on startup)
            └── test/
                ├── java/com/testingai/batch/
                │   ├── BatchDemoApplicationTest.java
                │   ├── domain/InvoiceCalculatorTest.java
                │   ├── seed/OrderSeedServiceTest.java
                │   ├── chunk/ChunkJobConfigTest.java              (@SpringBootTest + @SpringBatchTest)
                │   ├── listener/ListenerStatsServiceTest.java
                │   ├── tasklet/TaskletJobConfigTest.java          (@SpringBootTest + @SpringBatchTest)
                │   ├── faulttolerant/FaultTolerantJobConfigTest.java (@SpringBootTest + @SpringBatchTest)
                │   ├── restart/RestartJobConfigTest.java          (@SpringBootTest + @SpringBatchTest)
                │   ├── partition/PartitionJobConfigTest.java      (@SpringBootTest + @SpringBatchTest)
                │   ├── util/FailureSimulatorTest.java
                │   ├── controller/DemoControllerTest.java         (@WebMvcTest, mocked BatchLaunchService/OrderSeedService)
                │   └── performance/DemoSimulation.java
                └── resources/application.yml
```

### Cross-cutting edits to existing files

- **`.githooks/pre-commit`** — extend the staged-file grep to also match `^batch-processing/.*\.java$`, and add a matching `mvn spotless:apply` block run from `batch-processing/`.
- **`CLAUDE.md`** — add a "Spring Batch demo" command section (mirroring the saga/LMAX sections), a `batch-processing/` row in the repository layout table.
- **`README.md`** (repo root) — add a `batch-processing/` row to the layout table.

## Domain model and schema

```sql
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_type VARCHAR(20) NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    tax DECIMAL(10,2) NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

`batch_type` is a discriminator (`CHUNK`/`FAULT_TOLERANT`/`RESTART`/`PARTITION`) keeping each pattern's demo data isolated within one shared `orders` table rather than four near-duplicate tables — each job's reader filters on its own `batch_type`. No `data.sql` — orders are seeded on demand via `POST /demo/orders/seed`, keeping demo data creation explicit rather than preloaded.

`InvoiceCalculator.toInvoice(Order)` is the one place tax/total math lives (`tax = amount * 0.08`, `total = amount + tax`), used by both `chunk.InvoiceProcessor` and `faulttolerant.FaultTolerantProcessor` to avoid duplicating the calculation.

## Pattern implementations

### `chunk/` — the core ETL step

`orderReader()` — `JdbcCursorItemReader<Order>` over `SELECT id, batch_type, customer_id, amount, status, created_at FROM orders WHERE batch_type = 'CHUNK' AND status = 'PENDING'`, row-mapped by `OrderRowMapper`. `InvoiceProcessor` delegates to `InvoiceCalculator`. `InvoiceItemWriter` (a plain `ItemWriter<Invoice>` using `JdbcTemplate.batchUpdate`) does two batch operations per chunk inside the chunk's transaction: insert the invoices, then update the corresponding orders' `status` to `INVOICED` — demonstrating chunk transactionality (either both happen or neither does, per chunk). `ChunkJobConfig` wires `invoiceChunkStep` (chunk size 10) with the `InvoiceJobListener`/`InvoiceStepListener` from `listener/` attached via `.listener(...)`, and `invoiceChunkJob` wrapping that one step.

### `listener/` — lifecycle observability

`InvoiceJobListener.beforeJob`/`afterJob` and `InvoiceStepListener.beforeStep`/`afterStep` record into `ListenerStatsService` (a single `AtomicReference<ListenerStats>` — last-run-wins, fine for a demo). `afterStep` pulls `stepExecution.getReadCount()`, `getWriteCount()`, `getSkipCount()` — real Spring Batch `StepExecution` accessors — into the recorded `ListenerStats`. `GET /demo/batch/listener-stats` returns the latest snapshot, populated only after `POST /demo/batch/chunk` has run at least once.

### `tasklet/` — simple non-chunked step

`ArchiveSummaryTasklet.execute(...)` runs `SELECT COUNT(*) FROM invoices`, logs `"Archive summary: {} invoices on file"`, and returns `RepeatStatus.FINISHED` — no chunk, no item-by-item processing. Its `JobRunResult` naturally reports `readCount`/`writeCount`/`skipCount` of `0`, called out in the README as expected: tasklet steps aren't chunk-oriented, so those counters don't apply to them the way they do to `chunk`/`faulttolerant`/`partition`.

### `faulttolerant/` — skip + retry

Reads orders where `batch_type = 'FAULT_TOLERANT'`. `FaultTolerantProcessor` calls `FailureSimulator.maybeThrow("fault-tolerant-invoice")` before delegating to `InvoiceCalculator` — a real 5%-rate `RuntimeException` for the step to actually skip/retry, not a scripted failure. The step is built `.faultTolerant().skip(RuntimeException.class).skipLimit(50).retry(RuntimeException.class).retryLimit(3)`, reusing `chunk.InvoiceItemWriter`. `JobRunResult.skipCount` is expected to be non-zero (statistically, at a 5% rate) across a large-enough seeded batch.

### `restart/` — job restart

`RestartFailureTracker.shouldFailNow(String runId)` returns `true` exactly once per `runId` (a `ConcurrentHashMap<String, AtomicBoolean>` — first call for a given key flips it and returns `true`; every call after that returns `false`). `RestartProcessor` is `@StepScope` with `@Value("#{jobParameters['runId']}")`; on item 3 of the first attempt for a given `runId` it throws an uncaught `RuntimeException` (no `.faultTolerant()` on this step — the point is a real step failure, not a skip). `JdbcCursorItemReader`'s default `saveState=true` persists the current row position into the `StepExecution`'s `ExecutionContext`, so relaunching the job with **the same `runId`** (and no other varying `JobParameters`) is recognized by Spring Batch as the same job instance and resumes the failed step from its last committed chunk rather than reprocessing already-committed rows. The controller endpoint deliberately does **not** add a timestamp or other unique parameter to this job's launch — that would make every call look like a new job instance and break restart semantics — unlike the other four job endpoints, which do add a unique parameter specifically so they *can* be re-triggered repeatedly without `JobInstanceAlreadyCompleteException`.

### `partition/` — partitioned step

`OrderRangePartitioner.partition(int gridSize)` queries `MIN(id)`/`MAX(id)` for `batch_type = 'PARTITION' AND status = 'PENDING'` orders and splits that range into `gridSize` contiguous id-range partitions, injecting `minId`/`maxId` into each partition's `ExecutionContext`. The worker step's reader is `@StepScope` with `@Value("#{stepExecutionContext['minId']}")`/`maxId` late-binding, querying `... WHERE batch_type = 'PARTITION' AND status = 'PENDING' AND id BETWEEN ? AND ?`. `PartitionJobConfig` wires the master step via `StepBuilder(...).partitioner("workerStep", partitioner).step(workerStep).gridSize(gridSize).taskExecutor(new SimpleAsyncTaskExecutor())`, reusing `InvoiceCalculator`/`InvoiceItemWriter`. Requires the H2 datasource to support genuine concurrent connections (see Spring Boot configuration below) since partitions run their chunk steps in parallel.

## API surface

Single `DemoController`, consistent with every other module in the repo:

| Endpoint | Behavior |
|---|---|
| `POST /demo/orders/seed?type=CHUNK\|FAULT_TOLERANT\|RESTART\|PARTITION&count=50` | Inserts `count` random `PENDING` orders of the given `batch_type`. |
| `POST /demo/batch/chunk` | Launches `invoiceChunkJob` (unique timestamp parameter each call); returns `JobRunResult`. |
| `GET /demo/batch/listener-stats` | Returns the latest `ListenerStats` captured from the chunk job's listeners. |
| `POST /demo/batch/tasklet` | Launches `archiveSummaryJob`; returns `JobRunResult`. |
| `POST /demo/batch/fault-tolerant` | Launches `faultTolerantJob`; returns `JobRunResult` (expect non-zero `skipCount` at scale). |
| `POST /demo/batch/restart-demo?runId=demo-1` | Launches/restarts `restartDemoJob` for the given `runId` — first call returns `FAILED`, a second call with the same `runId` returns `COMPLETED`. |
| `POST /demo/batch/partition?gridSize=4` | Launches `partitionedInvoiceJob` (unique timestamp parameter each call); returns `JobRunResult`. |
| `GET /demo/invoices` | Lists all invoices written so far (inspection helper). |

Swagger UI at `/swagger-ui/index.html`, same as every other module.

## Testing

- **Pure unit tests** (Mockito, no Spring context): `InvoiceCalculatorTest`, `OrderSeedServiceTest` (mocked `JdbcTemplate`), `ListenerStatsServiceTest`, `FailureSimulatorTest` (statistical rate-band check, matching the Kafka-module convention), `DemoControllerTest` (`@WebMvcTest` + `@MockitoBean` on `BatchLaunchService`/`OrderSeedService`/the five `Job` beans — same shape as every other module's `DemoControllerTest`).
- **Job-config tests** (`chunk`, `tasklet`, `faulttolerant`, `restart`, `partition`) use `@SpringBootTest` + `@SpringBatchTest`, seeding orders directly via `JdbcTemplate` in `@BeforeEach`, launching the job under test via `JobLauncherTestUtils` with explicit unique `JobParameters` (or a fixed `runId` for the restart test, launched twice within the same test to assert `FAILED` then `COMPLETED`), and asserting on the returned `JobExecution`'s status/counts plus the resulting `orders`/`invoices` rows. This is a real, necessary deviation from this repo's usual pure-Mockito unit-test convention — Spring Batch job wiring (readers/processors/writers/listeners composed into a `Step`/`Job`) is declarative Spring configuration that isn't meaningfully testable by mocking `JobRepository`/`JobLauncher`; `@SpringBatchTest` is the framework's own documented testing approach for exactly this. `distributed-transactions/saga` already has a precedent for integration-style testing in this repo (`SagaIntegrationTest`, a `@WebMvcTest` + `@Import` of real collaborator beans), though Spring Batch's need for a real `DataSource`/`JobRepository` pushes this further to full `@SpringBootTest`.
- `src/test/.../performance/DemoSimulation.java` — Gatling load test hitting all seven endpoints (seeding first, then each job-launch endpoint); excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run explicitly via `mvn gatling:test`.

## Ports

- `spring-batch/spring-demo` → `8103` (next free slot after `data-integration/survey-monkey-import/importer-demo`'s `8102`).

## Spring Boot configuration

**Spring Boot version:** 3.4.4 (Spring Batch 5.x, bundled)
**Java:** 21

**`spring-batch-demo` dependencies:** `spring-boot-starter-web`, `spring-boot-starter-batch`, `spring-boot-starter-jdbc`, `com.h2database:h2` (runtime), `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test), `org.springframework.batch:spring-batch-test` (test — **not** pulled in transitively by `spring-boot-starter-batch`, must be declared explicitly for `@SpringBatchTest`/`JobLauncherTestUtils` to be on the test classpath), `gatling-charts-highcharts` (test).

`application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:batchdb;DB_CLOSE_DELAY=-1
  batch:
    job:
      enabled: false          # don't auto-run any Job bean at startup — jobs launch only via REST endpoints
    jdbc:
      initialize-schema: always

server:
  port: 8103
```

`spring.batch.job.enabled: false` is essential — with multiple `Job` beans in the context, Spring Boot's `JobLauncherApplicationRunner` would otherwise either error on ambiguity or (if `spring.batch.job.name` were set) auto-run one job on every app startup, which is not the intended trigger-via-REST demo shape used by every other module in this repo. `DB_CLOSE_DELAY=-1` keeps the in-memory H2 database alive across the connection pool's multiple pooled connections — required for the `partition/` pattern's concurrent worker threads to see the same data, and generally correct for any pooled (HikariCP) H2 in-memory setup.

## README

`batch-processing/spring-batch/spring-demo/README.md` follows the saga/LMAX modules' format: prerequisites (Java 21, Maven — no Docker needed), run instructions (`mvn spring-boot:run`), a diagram of the five job configs and the shared domain tables, a patterns-demonstrated table, and full `curl` walkthroughs for all seven endpoints — including a worked example of the restart pattern (seed → launch with `runId=demo-1` → observe `FAILED` → launch again with the same `runId` → observe `COMPLETED`, plus an invoice-count check showing already-processed orders weren't reprocessed). Swagger UI link and Gatling instructions included.

`batch-processing/README.md` is a short category index (analogous to `distributed-transactions/README.md` and `concurrency-patterns/README.md`), listing the six patterns and their "best fit" use case.

## Scope limits

- No persistence across app restarts beyond the current process — H2 is in-memory (`jdbc:h2:mem:batchdb`), so all orders/invoices and Spring Batch's own job-repository history reset when the app restarts. This is a demo of Spring Batch's *in-process* restart capability (relaunching a failed job while the app keeps running), not durable-across-deployments batch history.
- No `@Scheduled` cron-triggered jobs — every job launches via REST, consistent with how every other module in this repo triggers its patterns through `DemoController` rather than background schedulers.
- `restart/`'s controlled failure (`RestartFailureTracker`) is an in-memory, single-JVM mechanism for demo determinism — it is not how a real production restart scenario would be engineered (a real failure would come from an actual I/O error, bad record, etc.), and the README says so explicitly.
- `partition/`'s worker count is bounded by `gridSize` (REST parameter, no enforced cap) — a caller-supplied excessive `gridSize` isn't guarded against, matching the deliberately-unvalidated-input style already accepted elsewhere in this repo's demos (e.g., LMAX's `eventCount` is the one place in this repo that *does* cap an input; this demo does not add an equivalent cap for `gridSize`/`count`, since doing so isn't part of what any pattern here is meant to demonstrate).

# Project Reactor Demo Design

**Date:** 2026-07-28
**Status:** Approved

## Overview

A new top-level `reactive-programming/` category (sibling to `message-brokers/`, `noSQL/`, `cqrs-event-sourcing/`, `template-engines/`, `distributed-transactions/`, `communication-protocols/`), leaving room for other reactive libraries (e.g. RxJava) later. It contains a `project-reactor/` module with **two independent Spring Boot WebFlux apps** — `spring-demo` and `upstream-demo` — that together demonstrate core Project Reactor concepts: `Mono`/`Flux` basics, backpressure & error handling, schedulers/concurrency, and reactive streaming (SSE + `WebClient`) to another service.

`spring-demo` is the primary demo app: every pattern is exposed behind a `DemoController`, package-per-pattern, in the same spirit as the broker demos' `simple/`, `workqueue/`, `pubsub/` packages. `upstream-demo` is a small standalone WebFlux service (products + a live SSE price-tick feed) that `spring-demo`'s `WebClient` calls, giving the "reactive streaming to clients" pattern a real network hop to demonstrate, not just an in-process pipeline.

No external infrastructure required (no Docker) — both apps run locally via `mvn spring-boot:run`, like `template-engines/` and `distributed-transactions/saga`.

## Repository structure

```
reactive-programming/
├── pom.xml                                          (parent POM, mirrors template-engines/pom.xml and
│                                                       communication-protocols/pom.xml; packaging=pom,
│                                                       artifactId=reactive-programming; modules point directly
│                                                       into project-reactor/spring-demo and
│                                                       project-reactor/upstream-demo — no intermediate
│                                                       project-reactor/pom.xml, matching how
│                                                       communication-protocols/pom.xml's modules point into
│                                                       grpc/server-demo without a grpc/pom.xml)
├── eclipse-formatter.xml                            (copy — same style repo-wide)
├── README.md                                        (category overview)
└── project-reactor/
    ├── README.md                                    (module overview: Reactor concepts covered, run-both-apps
    │                                                  instructions, endpoint table)
    ├── spring-demo/
    │   ├── pom.xml                                  (artifactId: reactor-demo; parent: reactive-programming,
    │   │                                              relativePath ../../pom.xml; adds spock-core/groovy/
    │   │                                              gmavenplus like message-brokers/kafka/spring-demo)
    │   ├── README.md
    │   └── src/
    │       ├── main/
    │       │   ├── java/com/testingai/reactor/
    │       │   │   ├── ReactorDemoApplication.java
    │       │   │   ├── domain/
    │       │   │   │   ├── Product.java                (record: id, name, priceCents — same shape as
    │       │   │   │   │                                  template-engines/grpc's Product)
    │       │   │   │   ├── ProductWithDiscount.java     (record: product, discountedPriceCents)
    │       │   │   │   ├── PriceTick.java                (record: productId, price, timestamp)
    │       │   │   │   └── SampleDataService.java        (in-memory product catalog)
    │       │   │   ├── basics/
    │       │   │   │   └── BasicsService.java            (Mono/Flux creation & composition — see Basics below)
    │       │   │   ├── resilience/
    │       │   │   │   ├── ResilienceService.java        (backpressure + error handling — see Resilience below)
    │       │   │   │   ├── BackpressureResultDto.java    (record: strategy, emitted, processed, droppedOrBuffered)
    │       │   │   │   └── FailureSimulator.java         (FAILURE_RATE = 0.05, maybeThrow(String context) —
    │       │   │   │                                        same shape as message-brokers/kafka's FailureSimulator)
    │       │   │   ├── concurrency/
    │       │   │   │   ├── ConcurrencyService.java       (schedulers — see Concurrency below)
    │       │   │   │   └── ThreadTraceDto.java           (record: stage, threadName)
    │       │   │   ├── streaming/
    │       │   │   │   ├── StreamingService.java          (local SSE + WebClient calls to upstream-demo)
    │       │   │   │   └── WebClientConfig.java            (WebClient bean, baseUrl from upstream.base-url)
    │       │   │   └── controller/
    │       │   │       └── DemoController.java             (all endpoints — see Endpoints below)
    │       │   └── resources/
    │       │       └── application.yml                    (server.port: 8094; upstream.base-url:
    │       │                                                 http://localhost:8095)
    │       └── test/
    │           ├── groovy/com/testingai/reactor/
    │           │   ├── basics/BasicsServiceTest.groovy         (Spock + StepVerifier)
    │           │   ├── resilience/ResilienceServiceTest.groovy (Spock + StepVerifier)
    │           │   ├── resilience/FailureSimulatorTest.groovy  (Spock, statistical — same style as Kafka module)
    │           │   ├── concurrency/ConcurrencyServiceTest.groovy (Spock + StepVerifier, asserts thread names differ)
    │           │   ├── streaming/StreamingServiceTest.groovy    (Spock; WebClient wired to a WireMock/MockWebServer
    │           │   │                                              stub of upstream-demo)
    │           │   └── controller/DemoControllerTest.groovy     (Spock; standalone WebTestClient.bindToController(...),
    │           │                                                  collaborators as Spock Mock()s — see
    │           │                                                  spock-spring-webmvctest-incompatibility note below)
    │           └── java/com/testingai/reactor/performance/
    │               └── DemoSimulation.java                     (Gatling; hits all spring-demo REST/SSE endpoints)
    └── upstream-demo/
        ├── pom.xml                                      (artifactId: reactor-upstream-demo; parent:
        │                                                  reactive-programming, relativePath ../../pom.xml;
        │                                                  same Spock/Groovy setup)
        ├── README.md
        └── src/
            ├── main/
            │   ├── java/com/testingai/reactor/upstream/
            │   │   ├── ReactorUpstreamDemoApplication.java
            │   │   ├── domain/
            │   │   │   ├── Product.java                     (same shape as spring-demo's, kept as a separate
            │   │   │   │                                       copy — no shared module, matches the rest of
            │   │   │   │                                       this repo's per-module domain duplication, e.g.
            │   │   │   │                                       template-engines' two engines each having their
            │   │   │   │                                       own Product-equivalent)
            │   │   │   ├── PriceTick.java
            │   │   │   └── SampleDataService.java
            │   │   └── controller/
            │   │       └── UpstreamController.java            (see Upstream endpoints below)
            │   └── resources/
            │       └── application.yml                        (server.port: 8095)
            └── test/
                └── groovy/com/testingai/reactor/upstream/
                    └── controller/UpstreamControllerTest.groovy  (Spock; standalone WebTestClient.bindToController(...))
```

### Cross-cutting fixes needed in existing files

- **`.githooks/pre-commit`** — extend the staged-file grep from `^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols)/.*\.java$` to also match `^reactive-programming/.*\.java$`, and add a matching `mvn spotless:apply` block run from `reactive-programming/project-reactor/`.
- **`CLAUDE.md`** — add a "Project Reactor demo" command section mirroring the gRPC section, built from `cd reactive-programming` (the reactor root) with `mvn -pl project-reactor/spring-demo ...` / `mvn -pl project-reactor/upstream-demo ...` for per-module commands, and a note that `upstream-demo` must be started before `spring-demo` for the `streaming/upstream/*` endpoints; a `reactive-programming/` row in the repository layout table; and a note in the coding-standards section that `spring-demo`/`upstream-demo` use Spock/Groovy for unit tests like `message-brokers/kafka`.

## Domain

Both apps share the same small `Product` shape (`id`, `name`, `priceCents`) used elsewhere in the repo (`template-engines`, `grpc`), kept as independent copies per app (no shared module) — consistent with how the rest of this repo duplicates small domain types per module rather than introducing a shared library for a two-field record.

`PriceTick` (`productId`, `price`, `timestamp`) is new — the payload for the SSE feed, generated by randomly walking each product's price.

## Basics (`spring-demo/basics`)

`BasicsService`:

- **`Flux<Product> allProducts()`** — `Flux.fromIterable(catalog)`.
- **`Mono<Product> productById(String id)`** — `Flux.fromIterable(catalog).filter(...).next()`, empty `Mono` if not found.
- **`Flux<Product> generatedProducts(int count)`** — `Flux.generate` with a mutable counter sink, demonstrating programmatic/synchronous emission (as opposed to `fromIterable`'s eager source).
- **`Flux<ProductWithDiscount> discountedCatalog()`** — `Flux.zip(productsFlux, discountsFlux, ...)` combining the product catalog with a parallel discount-rate `Flux`, plus a second variant using `Flux.merge`/`Flux.concat` to combine two catalog sources and show the ordering difference (`concat` preserves source order and waits for the first to complete; `merge` interleaves as items arrive).

## Resilience (`spring-demo/resilience`)

`ResilienceService`:

- **`Mono<BackpressureResultDto> demonstrateBackpressure(String strategy)`** — a fast in-memory producer (`Flux.range` pushed through a `Sinks.Many`/`Flux.create` faster than the consumer can drain) piped through either `.onBackpressureBuffer(capacity)` or `.onBackpressureDrop(dropped -> ...)` depending on `strategy`, with a deliberately slow consumer (`.delayElements(...)` or a bounded-elastic sleep) downstream. Collects counts (emitted, processed, dropped-or-buffered) into the result DTO rather than streaming raw output over HTTP — keeps the endpoint synchronously testable and avoids relying on Reactor Netty's own TCP-level backpressure, which would otherwise mask the operator behavior being demonstrated.
- **`Flux<String> retryDemo()`** — wraps `FailureSimulator.maybeThrow("retryDemo")` in `Mono.fromRunnable(...).thenReturn(...)`, chained with `.retryWhen(Retry.backoff(3, Duration.ofMillis(100)))` so transient simulated failures are retried, and `.onErrorResume(...)` as the final fallback if retries are exhausted.
- **`Mono<String> timeoutDemo()`** — a simulated slow call (`Mono.delay(...)`) composed with `.timeout(Duration.ofMillis(...))` and `.onErrorResume(TimeoutException.class, ...)` to return a fallback value instead of propagating the timeout error.

`FailureSimulator` — `FAILURE_RATE = 0.05`, `maybeThrow(String context)` throwing `RuntimeException`, matching the Kafka module reference implementation exactly (per `.claude/rules/code-review.md`).

## Concurrency (`spring-demo/concurrency`)

`ConcurrencyService`:

- **`Mono<List<ThreadTraceDto>> subscribeOnVsPublishOn()`** — one pipeline using `.subscribeOn(Schedulers.boundedElastic())` and another using `.publishOn(Schedulers.parallel())` mid-chain, each stage recording `Thread.currentThread().getName()` into a `ThreadTraceDto`, returned together so the response makes the difference between "which thread the whole subscription runs on" (`subscribeOn`) and "which thread downstream operators run on from this point" (`publishOn`) directly visible.
- **`Mono<List<ThreadTraceDto>> parallelDemo()`** — `Flux.fromIterable(catalog).parallel(4).runOn(Schedulers.parallel())...sequential()`, recording the worker thread name per item to show concurrent processing across rails.
- **`Mono<String> blockingOffload()`** — a simulated blocking call (`Thread.sleep`-based, standing in for a blocking JDBC/legacy call) wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`, returning the thread name it ran on to prove it was offloaded off the event-loop thread.

## Streaming (`spring-demo/streaming` + `upstream-demo`)

`upstream-demo`'s `UpstreamController`:

| Endpoint | Behavior |
|---|---|
| `GET /upstream/products` | `Flux<Product>` streamed as `application/x-ndjson`. |
| `GET /upstream/ticks` | `Flux<ServerSentEvent<PriceTick>>` via `Flux.interval(Duration.ofMillis(500))` mapped to a randomly-walked price per product, run indefinitely until the client disconnects. |

`spring-demo`'s `StreamingService` (backed by a `WebClient` bean pointed at `upstream.base-url`):

- **`Flux<Product> fetchUpstreamProducts()`** — `webClient.get().uri("/upstream/products").retrieve().bodyToFlux(Product.class)`, a plain reactive HTTP call.
- **`Flux<PriceTick> relayUpstreamTicks()`** — consumes `upstream-demo`'s SSE feed via `.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<PriceTick>>() {})` and re-maps it to `PriceTick`, demonstrating a `WebClient` reactive SSE **consumer**.
- **`Flux<ServerSentEvent<PriceTick>> localTicks()`** — its own independent SSE **producer** (`Flux.interval` over the local catalog), so the module demonstrates both directions (producing SSE for a client, consuming SSE from an upstream service) without conflating them.

## Endpoints (`spring-demo/controller/DemoController`, base path `/demo`)

| Endpoint | Pattern group | Behavior |
|---|---|---|
| `GET /demo/basics/products` | Basics | `Flux<Product>` as NDJSON |
| `GET /demo/basics/products/{id}` | Basics | `Mono<Product>`, 404 if empty |
| `GET /demo/basics/generated?count=N` | Basics | `Flux<Product>` from `Flux.generate` |
| `GET /demo/basics/discounted` | Basics | `Flux<ProductWithDiscount>` from `zip`/`merge`/`concat` |
| `GET /demo/resilience/backpressure?strategy=buffer\|drop` | Resilience | `Mono<BackpressureResultDto>` |
| `GET /demo/resilience/retry` | Resilience | `Flux<String>` (retry + fallback outcome) |
| `GET /demo/resilience/timeout` | Resilience | `Mono<String>` (timeout + fallback outcome) |
| `GET /demo/concurrency/subscribe-vs-publish-on` | Concurrency | `Mono<List<ThreadTraceDto>>` |
| `GET /demo/concurrency/parallel` | Concurrency | `Mono<List<ThreadTraceDto>>` |
| `GET /demo/concurrency/blocking-offload` | Concurrency | `Mono<String>` |
| `GET /demo/streaming/ticks` (SSE) | Streaming | `Flux<ServerSentEvent<PriceTick>>`, local producer |
| `GET /demo/streaming/upstream/products` | Streaming | `Flux<Product>` via `WebClient` → `upstream-demo` |
| `GET /demo/streaming/upstream/ticks` (SSE) | Streaming | `Flux<PriceTick>` relayed from `upstream-demo`'s SSE feed |

## Ports

Next free slots after the existing `808x`/`809x` block (`8081`–`8090` broker/db/framework demos, `8091` grpc client-demo, `9090`/`9091` grpc server-demo, `8092`/`8093` mcp-repo-explorer):

- `spring-demo` → HTTP `8094`.
- `upstream-demo` → HTTP `8095`.

## Testing

- **Framework:** Spock (`spock-core` + `org.apache.groovy:groovy` test deps, `gmavenplus-plugin` with `addTestSources`/`compileTests` goals) in both `spring-demo` and `upstream-demo`, matching `message-brokers/kafka/spring-demo`'s POM setup exactly. Build requires `JAVA_HOME` pointed at JDK 21 (default JDK 25 breaks `gmavenplus` — see prior note from converting the Kafka module).
- **Pattern-class specs** (`BasicsServiceTest`, `ResilienceServiceTest`, `ConcurrencyServiceTest`, `StreamingServiceTest`) — plain Spock `Specification`s calling the service classes directly and asserting the emitted sequence with **`StepVerifier`** (`StepVerifier.create(flux).expectNext(...).verifyComplete()` / `.expectError(...)`), no Spring context. `StreamingServiceTest` wires the `WebClient` to a `MockWebServer` (OkHttp) stub instead of a real `upstream-demo` instance.
- **Controller specs** (`DemoControllerTest`, `UpstreamControllerTest`) — standalone `WebTestClient.bindToController(new DemoController(mockBasics, mockResilience, ...)).build()`, collaborators as Spock `Mock()`s. **Not** `@WebFluxTest` + `spock-spring`: `spock-spring:2.3-groovy-4.0` can't detect Spring Boot 3.x test-slice annotations under Spring Framework 6 (the same issue hit converting `message-brokers/kafka`'s `@WebMvcTest` controller test — `@Autowired`/`@MockitoBean` fields stay silently `null`, no Spring startup log lines). Standalone `WebTestClient` sidesteps it entirely and is faster (no Spring context) — only `spock-core` is needed, not `spock-spring`.
- **`resilience/FailureSimulatorTest.groovy`** — statistical assertion over many invocations (same style as the Kafka module's `FailureSimulatorTest`).
- **`spring-demo/src/test/java/.../performance/DemoSimulation.java`** — Gatling (Java, per repo convention — no other module writes Gatling sims in Groovy), hitting every endpoint in the table above **except** the two SSE endpoints (`/demo/streaming/ticks`, `/demo/streaming/upstream/ticks`) — those are `Flux.interval`-backed streams that never complete, and no module in this repo currently uses Gatling's dedicated SSE DSL (`io.gatling.javaapi.http`'s `sse(...)`), so exercising them would either hang a plain `http()` request or require introducing an unverified, precedent-free pattern; the module README covers them with a `curl -N` walkthrough instead. Excluded from `mvn test` via the inherited surefire `**/performance/**` exclude, run via `mvn gatling:test` with `upstream-demo` started first.
- No Gatling/perf test in `upstream-demo` — matches the repo-wide convention that `DemoSimulation` lives only with the REST-facing "front" app (see the gRPC module's `server-demo` having none either).

## Spring Boot configuration

**Spring Boot version:** 3.4.x
**Java:** 21

**`spring-demo` dependencies:** `spring-boot-starter-webflux`, `springdoc-openapi-starter-webflux-ui`, `lombok`, `spock-core`/`groovy` (test), `spring-boot-starter-test` (test, for `reactor-test`'s `StepVerifier` which it pulls in transitively), `okhttp3:mockwebserver` (test), `gatling-charts-highcharts` (test).

**`upstream-demo` dependencies:** `spring-boot-starter-webflux`, `lombok`, `spock-core`/`groovy` (test), `spring-boot-starter-test` (test).

## README

- `reactive-programming/README.md` — short category index (analogous to `distributed-transactions/README.md`), room to grow if more reactive-library demos are added later (e.g. RxJava).
- `reactive-programming/project-reactor/README.md` — module-level overview: what Project Reactor is, the four concept groups covered, the endpoint table above, and the client/upstream relationship (must run `upstream-demo` first for the `streaming/upstream/*` endpoints to work — the local-only endpoints work with `spring-demo` alone).
- `spring-demo/README.md` and `upstream-demo/README.md` — each follows the `saga`/`grpc` module README format: prerequisites (Java 21, Maven, no Docker), run instructions, and `curl` walkthroughs including one forced-retry example (repeated calls to `/demo/resilience/retry` until the 5% `FailureSimulator` trips, showing the retry/fallback path) and one SSE example (`curl -N`).

## Scope limits

- No persistence — `SampleDataService` in both apps is in-memory only, matching `template-engines`/`grpc`.
- No TLS/auth on the `WebClient` → `upstream-demo` call — plain HTTP, matching the "local demo, not production hardening" scope of every other module in this repo.
- No R2DBC/reactive database — this demo is scoped to core Reactor operators, schedulers, and HTTP-level reactive streaming (WebFlux + WebClient + SSE), not reactive persistence; that would be a reasonable follow-up module (`noSQL/mongodb` already has a reactive driver available) but is out of scope here.
- `demonstrateBackpressure` collects counts rather than streaming raw output over HTTP, since real backpressure signaling is only meaningfully observable at the in-process operator level for a demo of this size — streaming it over HTTP would mostly demonstrate Reactor Netty's TCP-level flow control instead of the `onBackpressureBuffer`/`onBackpressureDrop` operators themselves.
- Only one upstream service — no attempt to chain multiple reactive services or demonstrate distributed backpressure/circuit-breaking across a service mesh; that's a different, larger demo.

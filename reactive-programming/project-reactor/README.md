# Project Reactor Demo

Two independent Spring Boot WebFlux apps demonstrating core [Project Reactor](https://projectreactor.io/) concepts against a small product-catalog domain:

- **[`spring-demo`](spring-demo/)** — the primary demo app. Exposes every pattern below behind `DemoController`.
- **[`upstream-demo`](upstream-demo/)** — a small standalone service (`spring-demo`'s `WebClient` calls it) providing a product feed and a live SSE price-tick stream, so the "reactive streaming to another service" pattern has a real network hop to demonstrate.

## Concepts covered

| Package | Concepts |
|---|---|
| `basics/` | `Mono`/`Flux` creation (`just`, `fromIterable`, `generate`), composition (`map`/`filter`/`flatMap`, `zip`/`merge`/`concat`) |
| `resilience/` | Backpressure (`onBackpressureBuffer`/`onBackpressureDrop`), retry (`retryWhen`), fallback (`onErrorResume`), `timeout` |
| `concurrency/` | `subscribeOn` vs `publishOn`, `ParallelFlux`, offloading blocking calls to `Schedulers.boundedElastic()` |
| `streaming/` | SSE producer (own feed) and SSE/`WebClient` consumer (relaying `upstream-demo`'s feed) |

## Endpoints

| Endpoint | Pattern group |
|---|---|
| `GET /demo/basics/products` | Basics |
| `GET /demo/basics/products/{id}` | Basics |
| `GET /demo/basics/generated?count=N` | Basics |
| `GET /demo/basics/discounted` | Basics |
| `GET /demo/resilience/backpressure?strategy=buffer\|drop` | Resilience |
| `GET /demo/resilience/retry` | Resilience |
| `GET /demo/resilience/timeout` | Resilience |
| `GET /demo/concurrency/subscribe-vs-publish-on` | Concurrency |
| `GET /demo/concurrency/parallel` | Concurrency |
| `GET /demo/concurrency/blocking-offload` | Concurrency |
| `GET /demo/streaming/ticks` (SSE) | Streaming |
| `GET /demo/streaming/upstream/products` | Streaming |
| `GET /demo/streaming/upstream/ticks` (SSE) | Streaming |

## Running

No Docker required.

```bash
cd reactive-programming

# terminal 1 — upstream-demo must be running first for the streaming/upstream/* endpoints
mvn -pl project-reactor/upstream-demo spring-boot:run

# terminal 2
mvn -pl project-reactor/spring-demo spring-boot:run
```

`spring-demo` listens on `:8094`, `upstream-demo` on `:8095`. Swagger UI for `spring-demo` is at `http://localhost:8094/swagger-ui/index.html`.

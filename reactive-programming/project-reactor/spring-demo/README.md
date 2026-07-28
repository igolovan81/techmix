# Project Reactor Demo — spring-demo

The primary Project Reactor demo app. See [`../README.md`](../README.md) for the concept/endpoint overview and [`../upstream-demo/README.md`](../upstream-demo/README.md) for the companion service.

## Prerequisites

- Java 21 (`JAVA_HOME` must point at a JDK 21 install — Groovy/Spock test compilation fails under newer JDKs)
- Maven
- No Docker

## Running

```bash
cd reactive-programming
mvn -pl project-reactor/spring-demo spring-boot:run
```

Listens on `:8094`. The `streaming/upstream/*` endpoints additionally require `upstream-demo` running on `:8095` — see [`../upstream-demo/README.md`](../upstream-demo/README.md).

## Trying it out

```bash
# Basics
curl http://localhost:8094/demo/basics/products
curl http://localhost:8094/demo/basics/products/P-100
curl "http://localhost:8094/demo/basics/generated?count=3"
curl http://localhost:8094/demo/basics/discounted

# Resilience — repeat until the 5% FailureSimulator trips to see the fallback path
curl "http://localhost:8094/demo/resilience/backpressure?strategy=drop"
curl "http://localhost:8094/demo/resilience/backpressure?strategy=buffer"
for i in $(seq 1 20); do curl http://localhost:8094/demo/resilience/retry; echo; done
curl http://localhost:8094/demo/resilience/timeout

# Concurrency
curl http://localhost:8094/demo/concurrency/subscribe-vs-publish-on
curl http://localhost:8094/demo/concurrency/parallel
curl http://localhost:8094/demo/concurrency/blocking-offload

# Streaming — local SSE feed (Ctrl-C to stop)
curl -N http://localhost:8094/demo/streaming/ticks

# Streaming via upstream-demo (requires upstream-demo running on :8095)
curl http://localhost:8094/demo/streaming/upstream/products
curl -N http://localhost:8094/demo/streaming/upstream/ticks
```

## Build & test

```bash
cd reactive-programming
mvn -pl project-reactor/spring-demo test                    # unit tests (Gatling excluded automatically)
mvn -pl project-reactor/spring-demo test -Dtest=ClassName    # single test class

# Gatling needs both apps running first
mvn -pl project-reactor/upstream-demo spring-boot:run &
mvn -pl project-reactor/spring-demo spring-boot:run &
mvn gatling:test -pl project-reactor/spring-demo
```

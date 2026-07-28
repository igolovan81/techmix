# Project Reactor Demo — upstream-demo

A small standalone WebFlux service that `spring-demo`'s `WebClient` calls to demonstrate reactive HTTP streaming to another service. See [`../README.md`](../README.md) for the full module overview.

## Prerequisites

- Java 21 (`JAVA_HOME` must point at a JDK 21 install — Groovy/Spock test compilation fails under newer JDKs)
- Maven
- No Docker

## Running

```bash
cd reactive-programming
mvn -pl project-reactor/upstream-demo spring-boot:run
```

Listens on `:8095`.

## Trying it out

```bash
curl http://localhost:8095/upstream/products
curl -N http://localhost:8095/upstream/ticks   # SSE — Ctrl-C to stop
```

## Build & test

```bash
cd reactive-programming
mvn -pl project-reactor/upstream-demo test
mvn -pl project-reactor/upstream-demo test -Dtest=ClassName
```

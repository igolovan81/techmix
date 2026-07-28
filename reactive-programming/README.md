# Reactive Programming — Demos

This directory contains runnable demos for reactive-programming libraries on the JVM, structured the same way as `../template-engines/`: one or more Spring Boot demo apps per library, no external infrastructure required.

| Library | Demo | Best fit |
|---|---|---|
| [Project Reactor](project-reactor/) | `spring-boot-starter-webflux` | Mono/Flux fundamentals, backpressure, schedulers, and reactive HTTP streaming (SSE, WebClient) |

More reactive libraries may be added here over time (e.g. RxJava), at which point this README will grow into a comparison guide like `../message-brokers/README.md`.

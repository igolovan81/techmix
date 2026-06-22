# CQRS / Event Sourcing — Demos

This directory contains runnable demos for CQRS/event-sourcing frameworks, structured the same way as `../message-brokers/` and `../noSQL/`: one infrastructure component and one Spring Boot demo app per framework.

| Framework | Infrastructure | Best fit |
|---|---|---|
| [Axon Framework](axon/) | Axon Server (single node) | Commands, event-sourced aggregates, decoupled query models, event replay, snapshotting |

More CQRS/event-sourcing frameworks may be added here over time, at which point this README will grow into a comparison guide like `../message-brokers/README.md`.

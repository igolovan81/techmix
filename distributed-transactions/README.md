# Distributed Transactions — Demos

This directory contains runnable demos for distributed-transaction patterns, structured the same way as `../template-engines/`: one Spring Boot demo app per pattern, no external infrastructure required.

| Pattern | Demo | Best fit |
|---|---|---|
| [Saga](saga/) | Choreography + orchestration, e-commerce checkout | Coordinating a multi-step business transaction across services without distributed 2PC |

## Choreography vs. orchestration

| | Choreography | Orchestration |
|---|---|---|
| Coordination | None — each participant reacts to the event before/after it | Central `SagaOrchestrator` calls every participant directly |
| Coupling | Low — participants only know adjacent events | Higher — the orchestrator knows the whole flow |
| Visibility into progress | Indirect — reconstructed from an event/log timeline | Direct — one `SagaResult` returned synchronously |
| Best fit | Independently deployable services, simple per-step logic | Complex flows where the sequence and compensation logic benefit from being explicit in one place |

More distributed-transaction patterns may be added here over time.

# Workflow Engines — Demos

This directory contains runnable demos for workflow/BPMN orchestration engines, structured the same way as `../cqrs-event-sourcing/`: one infrastructure component and one Spring Boot demo app per engine.

| Engine | Infrastructure | Best fit |
|---|---|---|
| [Camunda 8](camunda/) | Camunda 8 self-managed (Zeebe + Operate + Tasklist) + Elasticsearch | Long-running, human-task-capable business processes modeled visually as BPMN diagrams; declarative branching and error routing instead of hand-written orchestration code |

More workflow engines may be added here over time (e.g. Temporal, Flowable), at which point this README will grow into a comparison guide like `../message-brokers/README.md`.

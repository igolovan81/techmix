# Concurrency Patterns — Demos

This directory contains runnable demos for concurrency primitives and patterns, structured the same way as `../distributed-transactions/`: one Spring Boot demo app per pattern/library, no external infrastructure required.

| Pattern | Demo | Best fit |
|---|---|---|
| [LMAX Disruptor](lmax-disruptor/) | Single handler, parallel handlers, diamond dependency graph, producer/wait-strategy comparisons, exception handling — over a trading order-matching domain | High-throughput, low-latency in-process event processing without lock contention or GC pressure from message allocation |

More concurrency patterns may be added here over time.

# Batch Processing — Demos

This directory contains runnable demos for batch-processing frameworks, structured the same way as `../concurrency-patterns/`: one Spring Boot demo app per framework, no external infrastructure required.

| Framework | Demo | Best fit |
|---|---|---|
| [Spring Batch](spring-batch/) | Chunk ETL, listeners, tasklet, skip/retry, restart, partitioning | Scheduled/triggered bulk data processing (ETL, billing runs, imports) with restartability and fault tolerance as first-class concerns |

More batch-processing frameworks may be added here over time.

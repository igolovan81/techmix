# Communication Protocols — Demos

This directory contains runnable demos for communication protocols used between services, structured the same way as `../distributed-transactions/`: one protocol per subdirectory, no external infrastructure required.

| Protocol | Demo | Best fit |
|---|---|---|
| [gRPC](grpc/) | Two independent Spring Boot apps (server + client) covering all four RPC patterns | High-performance, strongly-typed service-to-service calls; streaming workloads |

More protocol demos may be added here over time (e.g. GraphQL, WebSocket).

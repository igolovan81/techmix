# Communication Protocols — Demos

This directory contains runnable demos for communication protocols used between services, structured the same way as `../distributed-transactions/`: one protocol per subdirectory, no external infrastructure required.

| Protocol | Demo | Best fit |
|---|---|---|
| [gRPC](grpc/) | Two independent Spring Boot apps (server + client) covering all four RPC patterns | High-performance, strongly-typed service-to-service calls; streaming workloads |
| [GraphQL](graphql/) | Single Spring Boot app covering query/nested-fetch, DataLoader batching, mutation, and subscription patterns | Client-driven field selection over one endpoint; aggregating/relational data from a single request |
| [Webhooks](webhooks/) | Two independent Spring Boot apps — producer (subscriptions, HMAC-signed dispatch, retry/backoff, dead-lettering) + consumer (verification, idempotency/dedup) | Async server-to-server push where the receiver can't poll; event notifications between independently-owned systems |

More protocol demos may be added here over time (e.g. WebSocket).

# Data Pipeline Agent — Future Investigation

## Concept

An agent that ingests raw unstructured data (CSV uploads, API responses, free-text reports), enriches each record by calling external APIs, validates the result against an inferred schema, and writes clean structured output to a database.

## Key Capabilities to Explore

- **Schema inference loop** — agent inspects a data sample, proposes a target schema, user confirms, then transformation begins
- **Enrichment tools** — per-row lookup tools: geocoding, currency conversion, entity resolution, taxonomy classification
- **Validation loop** — after transformation, Claude checks its own output against the agreed schema and retries failed rows before committing
- **Streaming for large files** — use the Anthropic streaming API (`MessageStreamParams`) to process large datasets record-by-record without hitting token limits
- **Spring Batch integration** — for files with millions of rows, delegate batching and retry to Spring Batch; Claude handles enrichment per chunk

## Suggested Stack

- Spring Boot + Anthropic Java SDK with streaming support
- Spring Batch for large-file ingestion and retry
- Existing Postgres from `docker-compose.yml` as the staging and output store
- Claude JSON mode (structured output) for schema-validated results

## Starting Points

- Anthropic streaming Java SDK: `MessageStreamParams` builder
- Spring Batch: https://spring.io/projects/spring-batch
- Claude structured output (tool use or response format) for typed JSON rows

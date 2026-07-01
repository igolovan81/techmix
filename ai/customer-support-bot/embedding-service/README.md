# Embedding Service

Manages a pgvector knowledge base for the Customer Support Bot. Accepts product documentation, embeds it via the OpenAI `text-embedding-3-small` model, and serves semantic similarity search results to the Chat Service.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/kb/ingest` | Embed and store a document chunk |
| `GET`  | `/api/kb/search?q=...&limit=3` | Retrieve top-K chunks by cosine similarity |

## Prerequisites

| What | Where |
|------|-------|
| `OPENAI_API_KEY` | https://platform.openai.com/api-keys |
| Postgres with pgvector | `docker compose up -d` from repo root |

## Running

```bash
export OPENAI_API_KEY=sk-...
cd ai/customer-support-bot/embedding-service
mvn spring-boot:run
```

App starts on **port 8087**.

## Seed knowledge base

```bash
curl -s -X POST http://localhost:8087/api/kb/ingest \
  -H "Content-Type: application/json" \
  -d '{"title": "Refund Policy", "content": "Customers may request a refund within 30 days of purchase by contacting support."}'

curl -s -X POST http://localhost:8087/api/kb/ingest \
  -H "Content-Type: application/json" \
  -d '{"title": "Shipping Times", "content": "Standard shipping takes 3-5 business days. Express shipping takes 1-2 business days."}'
```

## Search

```bash
curl "http://localhost:8087/api/kb/search?q=how+do+I+get+a+refund&limit=2"
```

## Build & test

```bash
mvn clean package   # build
mvn test            # unit tests (requires Docker for Testcontainers pgvector tests)
```

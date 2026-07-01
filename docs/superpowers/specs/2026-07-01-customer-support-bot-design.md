# Customer Support Bot — Design Spec

**Date:** 2026-07-01  
**Status:** Approved

---

## Overview

An agentic customer support chatbot implemented as two independent Spring Boot services. The bot handles multi-turn conversations, retrieves product knowledge using semantic search, applies a rule-based escalation policy, and persists typed conversation outcomes to Postgres.

---

## Architecture

Two services under `ai/customer-support-bot/`:

```
ai/customer-support-bot/
├── embedding-service/      (port 8087)
└── chat-service/           (port 8086)
```

They share no code. Communication is HTTP. The shared Postgres instance (from the root `docker-compose.yml`) is the only infrastructure both services touch, using separate tables.

```
User → Chat Service (:8086)
             ├─ GET /api/kb/search?q=...  → Embedding Service (:8087)
             │                                    └─ pgvector cosine search
             ├─ AnthropicClient (messages.create)
             ├─ EscalationPolicy.evaluate(session)
             └─ OutcomeRepository.save(outcome)  → Postgres (conversations table)

Admin → Embedding Service (:8087)
             ├─ POST /api/kb/ingest  → OpenAI embeddings → pgvector upsert
             └─ GET  /api/kb/search  → pgvector ANN query → chunks[]
```

**Tech stack:**
- Java 21, Spring Boot 3.4.4, Lombok
- Anthropic Java SDK 2.40.1 (chat-service only)
- OpenAI `text-embedding-3-small` via plain `RestClient` (embedding-service only)
- pgvector extension on shared Postgres
- Liquibase for schema management (each service owns its own changelog)

---

## Embedding Service

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/kb/ingest` | Embed a document chunk and upsert into pgvector |
| `GET`  | `/api/kb/search` | Embed a query and return top-K cosine-nearest chunks |

**Ingest request:** `{ "title": "...", "content": "..." }`  
**Search params:** `?q=<query>&limit=3` (limit defaults to 3)  
**Search response:** `[{ "title": "...", "content": "...", "score": 0.92 }]`

### Module layout

```
embedding-service/src/main/java/com/testingai/embedding/
├── EmbeddingApplication.java
├── config/
│   ├── AppConfig.java              RestClient bean for OpenAI
│   ├── OpenAiProperties.java       openai.* config (apiKey, baseUrl, model)
│   └── DataSourceConfig.java       pgvector JDBC setup
├── controller/
│   └── KnowledgeBaseController.java
├── service/
│   ├── EmbeddingService.java       calls OpenAI embeddings REST API
│   └── VectorStoreService.java     JDBC upsert + cosine ANN search
└── model/
    ├── IngestRequest.java          { title, content }
    ├── SearchResult.java           { title, content, score }
    └── ChunkRecord.java            internal: { id, title, content, embedding }
```

### Database schema (Liquibase, embedding-service)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE kb_chunk (
    id        BIGSERIAL PRIMARY KEY,
    title     TEXT NOT NULL,
    content   TEXT NOT NULL,
    embedding VECTOR(1536)
);

CREATE INDEX ON kb_chunk USING ivfflat (embedding vector_cosine_ops);
```

`VECTOR(1536)` matches the `text-embedding-3-small` output dimension.

### Configuration (`application.yml`)

```yaml
server:
  port: 8087

openai:
  api-key: ${OPENAI_API_KEY}
  base-url: https://api.openai.com
  model: text-embedding-3-small

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
```

### Dependencies (`pom.xml`)

`spring-boot-starter-web`, `spring-boot-starter-jdbc`, `liquibase-core`, `postgresql`, `lombok`

---

## Chat Service

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/chat/start` | Create a new session; returns `{ sessionId }` |
| `POST` | `/api/chat/{sessionId}/message` | Send a user turn; returns `{ reply, outcome, escalated }` |
| `POST` | `/api/chat/{sessionId}/close` | Mark session as `ABANDONED` if still `OPEN` |

### Session state (in-memory)

```java
record SessionState(
    String sessionId,
    List<MessageParam> history,   // full Anthropic message history
    int turnCount,
    ConversationOutcome outcome,  // OPEN | RESOLVED | ESCALATED | ABANDONED
    Instant startedAt
) {}
```

Sessions are stored in a `ConcurrentHashMap<String, SessionState>`. State is lost on restart — appropriate for a demo.

### Per-turn flow (`ChatService.sendMessage`)

1. Look up session; return 404 if unknown, 409 if outcome ≠ OPEN.
2. Call `KnowledgeBaseClient.search(userText, 3)` → top-3 KB chunks. If Embedding Service is unavailable, log and continue without context.
3. Build system prompt: support persona + retrieved chunks formatted as a numbered context block.
4. Append user message to `session.history`. Call `anthropic.messages().create()` with full history.
5. Extract reply text; append assistant message to history. Increment `turnCount`.
6. Run `EscalationPolicy.evaluate(session, userText)`:
   - Escalate if `turnCount >= chat.max-turns` (default 10).
   - Escalate if `userText` (lowercased) contains any keyword from `chat.escalation-keywords` (default: `angry, lawsuit, unacceptable, refund, fraud`).
7. If outcome is still `OPEN` and Claude's reply contains the resolution phrase (configurable, default: `"is there anything else"`), mark `RESOLVED`.
8. If outcome changed from `OPEN` → call `OutcomeRepository.save()` to persist the record.
9. Return `{ reply, outcome, escalated }`.

### Module layout

```
chat-service/src/main/java/com/testingai/chat/
├── ChatApplication.java
├── config/
│   ├── AppConfig.java              AnthropicClient bean; RestClient for Embedding Service
│   ├── AnthropicProperties.java    anthropic.* config
│   ├── ChatProperties.java         chat.* config
│   └── DataSourceConfig.java
├── controller/
│   └── ChatController.java         start / message / close
├── service/
│   ├── ChatService.java            per-turn orchestration
│   ├── KnowledgeBaseClient.java    HTTP client → Embedding Service
│   └── EscalationPolicy.java       rule-based evaluation (pure function)
├── repository/
│   └── OutcomeRepository.java      JDBC insert to conversations table
└── model/
    ├── SessionState.java
    ├── MessageRequest.java         { text }
    ├── MessageResponse.java        { reply, outcome, escalated }
    ├── ConversationOutcome.java     enum: OPEN / RESOLVED / ESCALATED / ABANDONED
    └── SearchResult.java           { title, content, score }
```

### Database schema (Liquibase, chat-service)

```sql
CREATE TABLE conversations (
    id          BIGSERIAL PRIMARY KEY,
    session_id  TEXT        NOT NULL,
    outcome     TEXT        NOT NULL,
    turn_count  INT         NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ NOT NULL
);
```

### Configuration (`application.yml`)

```yaml
server:
  port: 8086

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

chat:
  max-turns: 10
  escalation-keywords: angry,lawsuit,unacceptable,refund,fraud
  resolution-phrase: "is there anything else"
  embedding-service-url: http://localhost:8087

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
```

### Dependencies (`pom.xml`)

`spring-boot-starter-web`, `spring-boot-starter-jdbc`, `liquibase-core`, `postgresql`, `anthropic-java:2.40.1`, `lombok`

---

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Embedding Service unavailable | `KnowledgeBaseClient` catches `RestClientException`, returns empty list; turn proceeds without RAG context |
| OpenAI embeddings API error | `EmbeddingService` throws `RuntimeException`; controller returns 500 |
| Unknown `sessionId` | `ChatController` returns 404 |
| Message sent to closed session | `ChatController` returns 409 Conflict |
| Anthropic API error | Propagates as 500; session stays `OPEN`, nothing written to DB |

---

## Testing

Each service has an independent test suite (`mvn test` in its own directory, no API keys needed).

### Embedding Service

| Test class | What it covers |
|------------|---------------|
| `VectorStoreServiceTest` | WireMock for OpenAI embeddings API; embedded Postgres (`embedded-postgres`) for pgvector SQL; tests ingest and cosine search |
| `KnowledgeBaseControllerTest` | `@WebMvcTest` with mocked `EmbeddingService` and `VectorStoreService` |

### Chat Service

| Test class | What it covers |
|------------|---------------|
| `EscalationPolicyTest` | Pure unit test; no mocks |
| `ChatServiceTest` | WireMock for Embedding Service; mocked `AnthropicClient`; verifies system prompt construction, turn counting, outcome transitions |
| `OutcomeRepositoryTest` | `@JdbcTest` with H2 in-memory; verifies `conversations` row written correctly |
| `ChatControllerTest` | `@WebMvcTest` with mocked `ChatService`; verifies 404/409 edge cases |

### Integration test (both services, requires real API keys)

```bash
mvn test -Dtest=CustomerSupportIntegrationTest -Dgroups=integration
```

Starts a full conversation, triggers escalation, verifies the outcome row in Postgres.

---

## Prerequisites

| What | Where |
|------|-------|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `OPENAI_API_KEY` | https://platform.openai.com/api-keys |
| Postgres with pgvector | `docker compose up -d` from repo root |

---

## Running

```bash
# Terminal 1 — Embedding Service
export OPENAI_API_KEY=sk-...
cd ai/customer-support-bot/embedding-service
mvn spring-boot:run

# Terminal 2 — Chat Service
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/customer-support-bot/chat-service
mvn spring-boot:run
```

### Quick smoke test

```bash
# 1. Ingest a KB document
curl -s -X POST http://localhost:8087/api/kb/ingest \
  -H "Content-Type: application/json" \
  -d '{"title": "Refund Policy", "content": "You may request a refund within 30 days of purchase."}'

# 2. Start a conversation
SESSION=$(curl -s -X POST http://localhost:8086/api/chat/start | jq -r .sessionId)

# 3. Send a message
curl -s -X POST http://localhost:8086/api/chat/$SESSION/message \
  -H "Content-Type: application/json" \
  -d '{"text": "How do I get a refund?"}'
```

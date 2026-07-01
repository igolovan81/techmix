# Chat Service

Agentic customer support chatbot. Maintains multi-turn conversations, retrieves relevant context from the Embedding Service via pgvector, calls Claude (Anthropic Java SDK), applies rule-based escalation, and persists conversation outcomes to Postgres.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/chat/start` | Start a new session; returns `{ sessionId }` |
| `POST` | `/api/chat/{sessionId}/message` | Send a turn; returns `{ reply, outcome, escalated }` |
| `POST` | `/api/chat/{sessionId}/close` | Close session as ABANDONED (204) |

**Outcomes:** `OPEN` → `RESOLVED` (Claude ends with resolution phrase) / `ESCALATED` (keyword or turn limit hit) / `ABANDONED` (explicit close).

## Prerequisites

| What | Where |
|------|-------|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| Embedding Service running | See `embedding-service/README.md` |
| Postgres | `docker compose up -d` from repo root |

## Running

Start the Embedding Service first, then:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/customer-support-bot/chat-service
mvn spring-boot:run
```

App starts on **port 8086**.

## Full conversation example

```bash
# Start a session
SESSION=$(curl -s -X POST http://localhost:8086/api/chat/start | jq -r .sessionId)

# Send a message
curl -s -X POST http://localhost:8086/api/chat/$SESSION/message \
  -H "Content-Type: application/json" \
  -d '{"text": "How do I request a refund?"}' | jq .

# Close the session when done
curl -s -X POST http://localhost:8086/api/chat/$SESSION/close
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `chat.max-turns` | `10` | Turns before escalation |
| `chat.escalation-keywords` | `angry,lawsuit,unacceptable,refund,fraud` | Escalation trigger words |
| `chat.resolution-phrase` | `is there anything else` | Phrase Claude says when issue is resolved |
| `chat.embedding-service-url` | `http://localhost:8087` | URL of the Embedding Service |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model |

## Build & test

```bash
mvn clean package   # build
mvn test            # unit tests (no API keys needed)
```

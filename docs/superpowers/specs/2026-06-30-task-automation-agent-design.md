# Task Automation Agent — Design Spec

**Date:** 2026-06-30
**Status:** Approved

## Overview

A Spring Boot demo app (Java 21) that exposes a single REST endpoint. A caller posts a natural-language goal; an agentic loop powered by Claude uses two tools — web search and page fetch — to research and summarize the answer autonomously.

Lives at `ai/task-automation-agent/spring-demo/`, mirroring the existing monorepo convention (`message-brokers/<broker>/spring-demo/`).

Companion stubs for future variants:
- `ai/customer-support-bot/README.md`
- `ai/code-review-agent/README.md`
- `ai/data-pipeline-agent/README.md`

---

## Module Structure

```
ai/
├── task-automation-agent/
│   └── spring-demo/
│       ├── pom.xml
│       └── src/
│           ├── main/java/com/testingai/agent/
│           │   ├── AgentApplication.java
│           │   ├── controller/AgentController.java
│           │   ├── service/AgentService.java
│           │   ├── tool/WebSearchTool.java
│           │   ├── tool/FetchPageTool.java
│           │   ├── tool/ToolExecutor.java
│           │   └── model/                        (request/response records)
│           └── main/resources/application.yml
├── customer-support-bot/README.md
├── code-review-agent/README.md
└── data-pipeline-agent/README.md
```

**Port:** 8084 (all lower ports taken by existing demos).

**External dependencies:**
- `ANTHROPIC_API_KEY` — Claude API
- `TAVILY_API_KEY` — Tavily Search API (built for AI agents, free tier available, returns pre-extracted text)

---

## Architecture

```
POST /api/agent/run  { "goal": "..." }
        │
        ▼
  AgentController
        │
        ▼
  AgentService  ◄─────────────────────────────────────────────┐
        │                                                      │
        │  1. Build messages list + tool definitions           │
        │  2. Call Claude (claude-sonnet-4-6)                  │
        │         │                                            │
        │    tool_use blocks                                   │
        │         │                                            │
        ▼         ▼                                            │
  ToolExecutor                                                 │
        │                                                      │
        ├── web_search  ──►  Tavily API  ──► [{title,url,content}]
        │                                                      │
        └── fetch_page  ──►  HttpClient + Jsoup  ──► plain text│
                                                               │
        Append tool_result blocks to messages ─────────────────┘
        (loop until end_turn or iteration cap)
        │
        ▼
  AgentResponse { answer, steps[], iterations, truncated }
```

---

## Agentic Loop

1. Initialise `messages` with `[{ role: "user", content: goal }]`.
2. Send to Claude with both tool definitions.
3. If response contains `tool_use` blocks: execute each via `ToolExecutor`, append `tool_result` blocks to `messages`, go to step 2.
4. If response is `end_turn` with text: return final answer.
5. **Iteration cap:** 10 (configurable via `agent.max-iterations` in `application.yml`). If reached, return accumulated steps with `"truncated": true`.

---

## Tools

### `web_search`

| Field | Detail |
|---|---|
| Input | `{ "query": string, "num_results": int }` — default `num_results = 5` |
| Implementation | `WebSearchTool` calls Tavily `/search` |
| Output | JSON array `[{ "title", "url", "content" }]` returned as string |

### `fetch_page`

| Field | Detail |
|---|---|
| Input | `{ "url": string }` |
| Implementation | `FetchPageTool` fetches via `HttpClient`, strips HTML with Jsoup |
| Output | Plain text, trimmed to 4 000 characters |

---

## API

### `POST /api/agent/run`

**Request:**
```json
{ "goal": "Summarize the latest breakthroughs in quantum computing" }
```

**Response:**
```json
{
  "answer": "...",
  "steps": [
    { "tool": "web_search", "input": { "query": "...", "num_results": 5 }, "output": "..." },
    { "tool": "fetch_page", "input": { "url": "https://..." },              "output": "..." }
  ],
  "iterations": 2,
  "truncated": false
}
```

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| Tool failure (timeout, non-200, parse error) | Return `{ "error": "..." }` as `tool_result` so Claude can adapt |
| Iteration cap reached | Return partial result with `"truncated": true` |
| Missing API key at startup | `@PostConstruct` check throws, Spring refuses to start with a clear message |
| Invalid request body | Spring validation returns `400 Bad Request` |

---

## Testing

| Test | Scope |
|---|---|
| `AgentServiceTest` | Unit — mocked Anthropic client; covers single-iteration and multi-iteration paths |
| `WebSearchToolTest` | Unit — WireMock stub for Tavily API |
| `FetchPageToolTest` | Unit — WireMock stub for target URL |
| `AgentIntegrationTest` | Integration — real endpoint, real APIs; skipped in CI via `@Tag("integration")` |

Maven surefire excludes `@Tag("integration")` tests, consistent with the rest of the repo (Gatling is similarly excluded from `mvn test`).

---

## Configuration (`application.yml`)

```yaml
agent:
  max-iterations: 10
  fetch-page-max-chars: 4000

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

tavily:
  api-key: ${TAVILY_API_KEY}
  base-url: https://api.tavily.com
```

---

## Out of Scope

- Authentication / rate limiting on the REST endpoint (demo only)
- Persistent conversation history across requests
- Streaming responses
- UI — curl or any HTTP client is sufficient for the demo

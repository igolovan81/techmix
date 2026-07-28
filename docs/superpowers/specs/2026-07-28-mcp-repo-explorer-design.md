# MCP Repo Explorer — Design Spec

**Date:** 2026-07-28
**Status:** Approved

## Overview

A pair of Spring Boot demo apps (Java 21) that show the **Model Context Protocol** end to end, as a network boundary between two independent processes rather than the hand-rolled tool dispatch used by `ai/task-automation-agent` and `ai/code-review-agent`:

- **`mcp-server`** — a real MCP server exposing repo-introspection tools (list modules, read a `README.md`, search across all `README.md` files) over the Streamable HTTP transport, using the official `io.modelcontextprotocol.sdk:mcp` Java SDK.
- **`mcp-client-agent`** — a Claude-powered agent (Anthropic Java SDK, same pattern as the rest of `ai/`) that connects to `mcp-server` as an MCP client: it discovers tools at runtime via `tools/list` rather than hardcoding them, and dispatches every tool call over the protocol via `tools/call`.

The tool domain is this repository's own structure: `list_modules`, `read_readme`, `search_readmes`. This keeps the demo self-contained (no third-party API keys beyond Anthropic) and doubles as "the repo can describe itself."

Lives at `ai/mcp-repo-explorer/`, mirroring the `ai/customer-support-bot/` convention of splitting a demo into two cooperating services rather than one combined app — the split is the point here, since MCP server and client are normally separate processes.

---

## Module Structure

```
ai/
└── mcp-repo-explorer/
    ├── README.md
    ├── mcp-server/
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/testingai/mcpexplorer/server/
    │       │   ├── McpServerApplication.java
    │       │   ├── config/
    │       │   │   ├── McpServerConfig.java        McpSyncServer bean, Streamable HTTP transport
    │       │   │   └── RepoExplorerProperties.java repo.root (resolved, not user-supplied)
    │       │   └── tool/
    │       │       ├── ListModulesTool.java
    │       │       ├── ReadReadmeTool.java
    │       │       ├── SearchReadmesTool.java
    │       │       └── RepoRootResolver.java        walks up from user.dir to find CLAUDE.md
    │       └── main/resources/application.yml
    └── mcp-client-agent/
        ├── pom.xml
        └── src/
            ├── main/java/com/testingai/mcpexplorer/client/
            │   ├── AgentApplication.java
            │   ├── config/
            │   │   ├── AppConfig.java               AnthropicClient bean, McpSyncClient bean
            │   │   ├── AnthropicProperties.java
            │   │   └── McpClientProperties.java      mcp.server-url
            │   ├── controller/AgentController.java   POST /api/mcp-agent/run
            │   ├── service/McpAgentService.java       agentic loop over MCP tools
            │   └── model/
            │       ├── AgentRequest.java              { goal }
            │       ├── AgentResponse.java             { answer, steps, iterations, truncated }
            │       └── StepRecord.java                { tool, input, output }
            └── main/resources/application.yml
```

**Ports:** `mcp-server` → `8091`, `mcp-client-agent` → `8092` (next free ports; `8090` is taken by `spring-boot-starters/request-logging`).

**External dependencies:**
- `ANTHROPIC_API_KEY` — Claude API (`mcp-client-agent` only)
- `mcp-server` has no external dependencies — it reads this repo's own filesystem

---

## `mcp-server`

### Repo root resolution

`RepoRootResolver` walks up from `user.dir` looking for `CLAUDE.md`, capped at 10 levels. If not found, startup fails fast with a clear message. This avoids requiring the operator to compute a relative path by hand — `mvn spring-boot:run` from `mcp-server/` just works because the checkout root is a fixed number of levels up.

All tools resolve paths relative to this root and confine them under it (same path-traversal guard style as `code-review-agent`'s temp-dir isolation and `sdlc-agent`'s sandbox root) — a request for `../../etc/passwd` is rejected inside the tool, not by exception.

### Tools

#### `list_modules`

| Field | Detail |
|---|---|
| Input | `{}` (no arguments) |
| Implementation | Depth-2 walk from `repo.root`; a directory qualifies as a module if it (or a direct child) contains `README.md` or `pom.xml`. Skips `.git`, `target`, `node_modules`, `.claude`. |
| Output | JSON array `[{ "category": "message-brokers", "module": "kafka/spring-demo" }, ...]` |

#### `read_readme`

| Field | Detail |
|---|---|
| Input | `{ "path": string }` — relative to `repo.root`, e.g. `"ai/code-review-agent"` |
| Implementation | Resolves and confines the path under `repo.root`; reads `README.md` from that directory (or the path itself if it already points at a `README.md`) |
| Output | Content truncated to 8 000 characters; a path-traversal or missing-file attempt returns an MCP tool error result instead of throwing |

#### `search_readmes`

| Field | Detail |
|---|---|
| Input | `{ "keyword": string }` |
| Implementation | Case-insensitive line grep across every `README.md` under `repo.root` |
| Output | JSON array `[{ "path", "line" }]`, capped at 20 matches |

### Transport

`McpServerConfig` registers an `McpSyncServer` on the Streamable HTTP transport (`mcp-spring-webmvc` transport provider, matching this repo's servlet-stack convention via `spring-boot-starter-web` — no WebFlux). The three tools above are registered with their JSON schemas at startup.

---

## `mcp-client-agent`

### Architecture

```
POST /api/mcp-agent/run  { "goal": "which modules use Kafka?" }
        │
        ▼
  AgentController
        │
        ▼
  McpAgentService  ◄───────────────────────────────────────────────┐
        │                                                          │
        │  1. mcpClient.listTools() → [list_modules, read_readme,  │
        │     search_readmes] (discovered, not hardcoded)          │
        │  2. Convert MCP tool schemas → Anthropic tool defs        │
        │  3. Send goal + tool defs to Claude (claude-sonnet-4-6)  │
        │         │                                                 │
        │    tool_use blocks                                       │
        │         │                                                 │
        ▼         ▼                                                │
  mcpClient.callTool(name, args)  ──►  mcp-server (HTTP)  ──► result│
        │                                                          │
        Append tool_result blocks to messages ─────────────────────┘
        (loop until end_turn or iteration cap)
        │
        ▼
  AgentResponse { answer, steps[], iterations, truncated }
```

### Agentic loop

1. Initialise `messages` with `[{ role: "user", content: goal }]`.
2. On first call only: `mcpClient.listTools()`, convert each MCP tool's JSON schema into an Anthropic `Tool` definition.
3. Send `messages` + tool definitions to Claude.
4. If response contains `tool_use` blocks: for each, call `mcpClient.callTool(name, arguments)`, append the result as a `tool_result` block, go to step 3.
5. If response is `end_turn` with text: return the final answer.
6. **Iteration cap:** 10 (configurable via `agent.max-iterations`). If reached, return accumulated steps with `"truncated": true`.

`McpSyncClient` is created once at startup (`AppConfig`) and connects to `mcp-server` over the URL in `mcp.server-url`; connection failure at startup fails fast, the same posture as `chat-service` requiring `embedding-service` to be up first.

### API

**`POST /api/mcp-agent/run`**

Request:
```json
{ "goal": "which modules use Kafka?" }
```

Response:
```json
{
  "answer": "The message-brokers/kafka module and cqrs-event-sourcing/axon (via its Kafka-backed event bus) use Kafka.",
  "steps": [
    { "tool": "list_modules", "input": {}, "output": "[{\"category\":\"message-brokers\",\"module\":\"kafka/spring-demo\"}, ...]" },
    { "tool": "search_readmes", "input": { "keyword": "kafka" }, "output": "[{\"path\":\"message-brokers/kafka/spring-demo/README.md\",\"line\":\"...\"}, ...]" }
  ],
  "iterations": 2,
  "truncated": false
}
```

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| Tool path-traversal / missing file | `mcp-server` returns an MCP tool error result, not an exception; fed back to Claude as `tool_result` so it can adapt |
| `mcp-server` unreachable at `mcp-client-agent` startup | Startup fails fast with a clear message (required dependency, same as `chat-service` → `embedding-service`) |
| `mcp-server`'s `repo.root` cannot be resolved (`CLAUDE.md` not found within 10 levels) | Startup fails fast |
| Iteration cap reached | Return partial result with `"truncated": true` |
| Missing `ANTHROPIC_API_KEY` at startup | `@PostConstruct` check throws, Spring refuses to start |
| Invalid request body | Spring validation returns `400 Bad Request` |

---

## Testing

| Test | Scope |
|---|---|
| `ListModulesToolTest`, `ReadReadmeToolTest`, `SearchReadmesToolTest` | Unit — run against a temp directory fixture standing in for `repo.root`; no API keys needed |
| `RepoRootResolverTest` | Unit — verifies the walk-up-to-`CLAUDE.md` logic and the not-found failure path |
| `McpAgentServiceTest` | Unit — mocked `AnthropicClient` and mocked `McpSyncClient`; covers single-iteration and multi-iteration paths, and the iteration cap |
| `McpAgentIntegrationTest` | Integration — real Anthropic API, real running `mcp-server` and `mcp-client-agent`; tagged `@Tag("integration")`, requires `ANTHROPIC_API_KEY` |

Maven surefire excludes `@Tag("integration")` tests by default, consistent with every other module in the repo.

---

## Configuration

`mcp-server/src/main/resources/application.yml`:
```yaml
server:
  port: 8091
```
(`repo.root` is resolved at startup, not configured.)

`mcp-client-agent/src/main/resources/application.yml`:
```yaml
server:
  port: 8092

agent:
  max-iterations: 10

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

mcp:
  server-url: http://localhost:8091
```

---

## Out of Scope

- Authentication / rate limiting on either REST endpoint (demo only)
- Persistent conversation history across requests
- Streaming responses
- stdio transport (Streamable HTTP only, to stay consistent with every other module being a curl-able `mvn spring-boot:run` service)
- Write tools (repo introspection is read-only; no `write_file`-style tool as in `sdlc-agent`)
- UI — curl or any HTTP client is sufficient for the demo

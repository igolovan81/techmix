# MCP Client Agent (Repo Explorer)

A Claude-powered agent that connects to `ai/mcp-repo-explorer/mcp-server` as a real MCP client. Unlike this
repo's other `ai/` agents (`task-automation-agent`, `code-review-agent`), this one never hardcodes what tools
exist — it calls `tools/list` over the protocol on every request and converts whatever it gets back into
Anthropic tool definitions before handing them to Claude.

## Endpoint

| Method | Path | Description |
|--------|------|--------------|
| `POST` | `/api/mcp-agent/run` | Runs the agentic loop for a natural-language goal; returns `{ answer, steps, iterations, truncated }` |

## Prerequisites

| What | Where |
|------|-------|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `mcp-server` running on `:8092` | See `ai/mcp-repo-explorer/mcp-server/README.md` |

## Running

Start `mcp-server` first, then:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/mcp-repo-explorer/mcp-client-agent
mvn spring-boot:run
```

App starts on **port 8093**.

## Try it

```bash
curl -s -X POST http://localhost:8093/api/mcp-agent/run \
  -H "Content-Type: application/json" \
  -d '{"goal": "Which modules under message-brokers mention Kafka?"}' | jq .
```

Example response:

```json
{
  "answer": "message-brokers/kafka is the Kafka demo module.",
  "steps": [
    { "tool": "list_modules", "input": "{}", "output": "[{\"category\":\"message-brokers\",\"module\":\"kafka\"}, ...]" },
    { "tool": "search_readmes", "input": "{keyword=kafka}", "output": "[{\"path\":\"message-brokers/kafka/README.md\",\"line\":\"...\"}]" }
  ],
  "iterations": 2,
  "truncated": false
}
```

## Configuration

| Property | Default | Description |
|----------|---------|--------------|
| `agent.max-iterations` | `10` | Turns before truncation |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model |
| `mcp.server-url` | `http://localhost:8092` | Base URL of `mcp-server` |

## Build & test

```bash
cd ai/mcp-repo-explorer/mcp-client-agent

mvn clean package   # build
mvn test            # unit tests (no API keys needed)
```

To run the integration test (requires `ANTHROPIC_API_KEY` and both apps running):

```bash
mvn test -Dtest=McpAgentIntegrationTest -Dgroups=integration
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.40.1
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) 0.17.2 — `HttpClientStreamableHttpTransport` + `McpSyncClient`

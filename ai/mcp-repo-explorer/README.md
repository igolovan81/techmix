# MCP Repo Explorer

A pair of Spring Boot demos showing the **Model Context Protocol** end to end, as a network boundary between
two independent processes — as opposed to the hand-rolled tool dispatch used by `ai/task-automation-agent`
and `ai/code-review-agent`.

- **[`mcp-server`](mcp-server/README.md)** — a real MCP server (Streamable HTTP, official Java SDK) exposing
  three tools that introspect this repository's own structure: `list_modules`, `read_readme`, `search_readmes`.
- **[`mcp-client-agent`](mcp-client-agent/README.md)** — a Claude-powered agent that connects to `mcp-server`
  as an MCP client, discovering tools at runtime via `tools/list` rather than hardcoding them.

## Quickstart

```bash
# terminal 1
cd ai/mcp-repo-explorer/mcp-server
mvn spring-boot:run          # :8092

# terminal 2
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/mcp-repo-explorer/mcp-client-agent
mvn spring-boot:run           # :8093

# terminal 3
curl -s -X POST http://localhost:8093/api/mcp-agent/run \
  -H "Content-Type: application/json" \
  -d '{"goal": "Which modules under message-brokers mention Kafka?"}' | jq .
```

No external API keys or infrastructure are needed beyond `ANTHROPIC_API_KEY` for `mcp-client-agent` —
`mcp-server` reads this repo's own filesystem, rooted at the directory containing `CLAUDE.md`.

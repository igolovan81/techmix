# MCP Server (Repo Explorer)

A real [Model Context Protocol](https://modelcontextprotocol.io) server exposing three read-only tools over
the Streamable HTTP transport, using the official `io.modelcontextprotocol.sdk` Java SDK. The tools introspect
this repository's own structure — no external API keys or infrastructure required.

## Tools

| Tool | Input | Description |
|------|-------|-------------|
| `list_modules` | `{}` | Lists `{category, module}` pairs for every level-2 directory that (or a direct child of which) contains a `README.md` or `pom.xml` |
| `read_readme` | `{ "path": string }` | Reads a `README.md` given a path relative to the repo root, truncated to 8 000 characters |
| `search_readmes` | `{ "keyword": string }` | Case-insensitive search across every `README.md` in the repo, up to 20 matches |

## Repo root resolution

The server walks up from its working directory looking for `CLAUDE.md`, capped at 10 levels, and fails to
start if it isn't found. This means `mvn spring-boot:run` just works from this directory — no path
configuration needed.

## Running

```bash
cd ai/mcp-repo-explorer/mcp-server
mvn spring-boot:run
```

App starts on **port 8092**, MCP endpoint at `http://localhost:8092/mcp`.

## Try it

Any MCP client that speaks Streamable HTTP can connect to `http://localhost:8092/mcp`. See
`ai/mcp-repo-explorer/mcp-client-agent/README.md` for a Claude-powered client built specifically for this server.

## Build & test

```bash
cd ai/mcp-repo-explorer/mcp-server

mvn clean package   # build
mvn test            # unit tests + a full end-to-end wiring test against a real MCP client (no API keys needed)
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) 0.17.2 (`mcp` + `mcp-spring-webmvc`) — Streamable HTTP transport via `WebMvcStreamableServerTransportProvider`

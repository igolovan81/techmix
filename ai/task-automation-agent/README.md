# Task Automation Agent

## Concept

An agent that accepts a natural-language goal via REST, autonomously searches the web and reads pages using structured tools, and returns a researched answer together with a full trace of every step it took.

## Key Capabilities

- **Agentic loop** — Claude decides which tools to call, executes them, and feeds results back to itself repeatedly; the loop exits when Claude stops requesting tools (its own judgement) or the configurable iteration cap is reached, whichever comes first
- **Web search tool** — queries [Tavily Search API](https://docs.tavily.com) (built for AI agents) and returns structured `[{title, url, content}]` results Claude can reason over
- **Page fetch tool** — fetches any URL with `HttpClient`, strips HTML with Jsoup, and trims to a configurable character limit so the content fits cleanly in context
- **Transparent step trace** — every tool call and its output is recorded in `steps[]` on the response, making the agent's reasoning fully observable
- **Graceful error containment** — tool failures (network errors, bad URLs, missing parameters) return JSON error strings back to Claude so it can adapt, rather than crashing the run

---

A Spring Boot demo that shows what an **agentic AI product** looks like in practice. You give it a natural-language goal; it autonomously decides which tools to call, executes them, feeds the results back to Claude, and repeats until it has a final answer.

## How it works

```
POST /api/agent/run  {"goal": "..."}
        │
        ▼
  AgentService — agentic loop
        │
        ├─ 1. Send goal + tool definitions to Claude
        │
        ├─ 2. Claude returns tool_use blocks
        │         │
        │    ToolExecutor dispatches:
        │    ├─ web_search  →  Tavily Search API  →  [{title, url, content}]
        │    └─ fetch_page  →  HttpClient + Jsoup  →  plain text
        │
        ├─ 3. Results fed back to Claude as tool_result blocks
        │
        └─ 4. Repeat until Claude returns a final text answer
               (or 10-iteration cap is reached)
        │
        ▼
  AgentResponse { answer, steps[], iterations, truncated }
```

## Prerequisites

| What | Where to get it |
|---|---|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `TAVILY_API_KEY` | https://app.tavily.com (free tier available) |

## Running

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export TAVILY_API_KEY=tvly-...

cd spring-demo
mvn spring-boot:run
```

App starts on **port 8084**.

## Try it

```bash
curl -s -X POST http://localhost:8084/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"goal": "What are the latest breakthroughs in quantum computing?"}' | jq .
```

Example response:

```json
{
  "answer": "Recent breakthroughs include...",
  "steps": [
    {
      "tool": "web_search",
      "input": "{query=quantum computing breakthroughs 2025, num_results=5}",
      "output": "[{\"title\":\"...\",\"url\":\"...\",\"content\":\"...\"}]"
    },
    {
      "tool": "fetch_page",
      "input": "{url=https://...}",
      "output": "Full page text..."
    }
  ],
  "iterations": 3,
  "truncated": false
}
```

## Build & test

```bash
cd spring-demo

mvn clean package          # build
mvn test                   # unit tests (12 tests, no API keys needed)
```

To run the integration test (requires real API keys and the app running):

```bash
mvn test -Dtest=AgentIntegrationTest -Dgroups=integration
```

## Configuration

All defaults are in `spring-demo/src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `agent.max-iterations` | `10` | Maximum loop iterations before truncation |
| `agent.fetch-page-max-chars` | `4000` | Characters kept from a fetched page |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model to use |
| `tavily.base-url` | `https://api.tavily.com` | Override for local testing |

## Module layout

```
spring-demo/src/main/java/com/testingai/agent/
├── AgentApplication.java          entry point
├── config/
│   ├── AppConfig.java             Spring beans (AnthropicClient, RestClient, HttpClient)
│   ├── AgentProperties.java       agent.* config
│   ├── AnthropicProperties.java   anthropic.* config
│   └── TavilyProperties.java      tavily.* config
├── controller/
│   └── AgentController.java       POST /api/agent/run
├── service/
│   └── AgentService.java          agentic loop
├── tool/
│   ├── ToolExecutor.java          dispatches tool calls by name
│   ├── WebSearchTool.java         Tavily web search
│   └── FetchPageTool.java         HTTP fetch + Jsoup HTML strip
└── model/
    ├── AgentRequest.java          { goal }
    ├── AgentResponse.java         { answer, steps, iterations, truncated }
    └── StepRecord.java            { tool, input, output }
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.40.1
- [Tavily Search API](https://docs.tavily.com) — web search designed for AI agents
- [Jsoup](https://jsoup.org) — HTML parsing and text extraction

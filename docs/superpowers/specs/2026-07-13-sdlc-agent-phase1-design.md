# SDLC Agent — Phase 1 (Intake + Investigate) Design

**Date:** 2026-07-13
**Status:** Approved

## Overview

Implements Phase 1 of `ai/sdlc-agent/README.md`'s phased plan: a read-only agent that fetches a support ticket (Jira or Zendesk), agentically queries Splunk for correlated log evidence, and returns a structured `RootCauseHypothesis`. No file writes, no git operations, no deploy/test/release — those remain Phase 2/3, explicitly out of scope here. Follows this repo's established `ai/` agent conventions (`task-automation-agent`'s agentic-loop shape, `code-review-agent`'s tool-wrapped-external-tool pattern), with real external integrations (Jira REST API v3, Zendesk Support API, Splunk REST API) rather than mocks, consistent with how every other `ai/` module in this repo works.

## Repository structure

```
ai/sdlc-agent/
├── README.md                                    (existing concept doc — unchanged)
├── docker/
│   ├── docker-compose.yml                       (Splunk single-node, HEC enabled)
│   └── seed-logs.sh                              (posts ~50 sample events to HEC)
└── spring-demo/
    ├── pom.xml                                   (artifactId: sdlc-agent-demo)
    └── src/
        ├── main/
        │   ├── java/com/testingai/sdlc/
        │   │   ├── SdlcAgentApplication.java
        │   │   ├── config/
        │   │   │   ├── AppConfig.java             (AnthropicClient, RestClient beans)
        │   │   │   ├── AgentProperties.java        agent.* config
        │   │   │   ├── AnthropicProperties.java    anthropic.* config
        │   │   │   ├── JiraProperties.java         jira.* config
        │   │   │   ├── ZendeskProperties.java      zendesk.* config
        │   │   │   └── SplunkProperties.java       splunk.* config
        │   │   ├── controller/
        │   │   │   └── InvestigateController.java  POST /api/sdlc/investigate
        │   │   ├── service/
        │   │   │   └── InvestigateService.java      agentic loop
        │   │   ├── ticket/
        │   │   │   ├── Ticket.java                  record
        │   │   │   ├── TicketSource.java             interface
        │   │   │   ├── JiraTicketSource.java
        │   │   │   └── ZendeskTicketSource.java
        │   │   ├── log/
        │   │   │   ├── LogEntry.java                 record
        │   │   │   ├── LogSource.java                 interface
        │   │   │   └── SplunkLogSource.java
        │   │   ├── tool/
        │   │   │   ├── ToolExecutor.java              dispatches tool calls by name
        │   │   │   └── QueryLogsTool.java              wraps LogSource for Claude
        │   │   └── model/
        │   │       ├── InvestigateRequest.java         { ticketId }
        │   │       ├── InvestigateResponse.java        { rootCause, iterations, steps, truncated }
        │   │       ├── RootCauseHypothesis.java        { summary, evidence[], confidence, suspectedFiles[] }
        │   │       └── StepRecord.java                 { tool, input, output }
        │   └── resources/
        │       └── application.yml
        └── test/
            ├── java/com/testingai/sdlc/
            │   ├── SdlcAgentApplicationTest.java
            │   ├── ticket/JiraTicketSourceTest.java      (mocked HTTP)
            │   ├── ticket/ZendeskTicketSourceTest.java    (mocked HTTP)
            │   ├── log/SplunkLogSourceTest.java           (mocked HTTP)
            │   ├── tool/QueryLogsToolTest.java
            │   ├── service/InvestigateServiceTest.java     (mocked AnthropicClient + sources)
            │   ├── controller/InvestigateControllerTest.java
            │   └── integration/SdlcAgentIntegrationTest.java  (@Tag("integration"), real APIs)
            └── resources/application.yml
    └── README.md
```

## Domain scenario

A fictional `checkout-service` bug seeds both the sample ticket and the Splunk log data, giving the RCA flow something concrete to correlate:

- **Ticket** (`DEMO-101` in Jira, or ticket `1001` in Zendesk): *"Checkout fails with 500 error for some orders"* — description mentions intermittent failures, no obvious pattern from the reporter's side.
- **Splunk logs** (`checkout-service`, `ERROR` level, seeded via `seed-logs.sh`): a `NullPointerException` stack trace —
  ```
  java.lang.NullPointerException: Cannot invoke "String.length()" because "discountCode" is null
      at com.example.checkout.DiscountService.apply(DiscountService.java:42)
      at com.example.checkout.CheckoutController.checkout(CheckoutController.java:58)
  ```
  interspersed with unrelated `INFO`-level noise (successful checkouts, health checks) so Claude has to actually search rather than getting handed the answer.

Expected agent output for this scenario: `RootCauseHypothesis{summary: "NullPointerException in DiscountService.apply when discountCode is null", suspectedFiles: ["DiscountService.java", "CheckoutController.java"], confidence: "high", evidence: [...matching log lines...]}`.

`RootCauseHypothesis` field types: `summary: String`, `evidence: List<String>` (raw matching log lines), `confidence: String` (one of `"high"`/`"medium"`/`"low"`, Claude's own self-assessment rather than a computed score), `suspectedFiles: List<String>`.

## Ticket sources

```java
public interface TicketSource {
    Ticket fetch(String ticketId);
}

public record Ticket(String id, String title, String description, String severity, String service, Instant reportedAt) {
}
```

- **`JiraTicketSource`** — calls `GET {jira.base-url}/rest/api/3/issue/{ticketId}`, Basic Auth with `jira.email`/`jira.api-token`. Maps Jira's `fields.summary` → `title`, `fields.description` (Atlassian Document Format, flattened to plain text) → `description`, `fields.priority.name` → `severity`, a configurable `jira.service-field` (defaults to a custom field key, falls back to `"unknown"` if absent) → `service`, `fields.created` → `reportedAt`.
- **`ZendeskTicketSource`** — calls `GET https://{zendesk.subdomain}.zendesk.com/api/v2/tickets/{ticketId}.json`, Basic Auth with `{zendesk.email}/token:{zendesk.api-token}`. Maps `ticket.subject` → `title`, `ticket.description` → `description`, `ticket.priority` → `severity`, `ticket.tags` (first tag matching a configurable service-tag prefix, else `"unknown"`) → `service`, `ticket.created_at` → `reportedAt`.
- **Selection:** `sdlc.ticket-source=jira|zendesk` property selects which bean is active, via `@ConditionalOnProperty`. `InvestigateService` depends on the single `TicketSource` interface, never on a concrete implementation.
- **Error handling:** a 404 from either API surfaces as `ResponseStatusException(NOT_FOUND, "Ticket not found: " + ticketId)` from `InvestigateController`; any other HTTP error from `ResponseStatusException(BAD_GATEWAY, ...)`.

## Log source

```java
public interface LogSource {
    List<LogEntry> query(String service, Instant from, Instant to, String keyword, String correlationId);
}

public record LogEntry(Instant timestamp, String service, String level, String message, String correlationId) {
}
```

- **`SplunkLogSource`** — runs a Splunk search job via the REST API: `POST {splunk.base-url}/services/search/jobs` with a search string built from the non-null parameters (`search index=main service="{service}" {keyword-clause} {correlationId-clause} earliest={from} latest={to}`), polls `GET .../jobs/{sid}` until `dispatchState=DONE`, then fetches results via `GET .../jobs/{sid}/results?output_mode=json`. Auth via Bearer token (`splunk.api-token`, a pre-generated Splunk auth token — avoids implementing the full login/session flow for a demo). TLS verification disabled for the local dev container only (`splunk.trust-self-signed=true`, off by default), since the official Splunk image serves a self-signed cert on 8089.
- **Timeout:** search-job polling capped at `splunk.search-timeout-seconds` (default 10s); if exceeded, returns an empty list rather than blocking the agent loop indefinitely — Claude sees zero results and can adjust its query.

## Agentic loop

`InvestigateService`, mirroring `AgentService` in `task-automation-agent`:

```java
public RootCauseHypothesis investigate(String ticketId) {
    Ticket ticket = ticketSource.fetch(ticketId);              // deterministic, not a tool call
    List<StepRecord> steps = new ArrayList<>();
    List<MessageParam> conversation = new ArrayList<>();
    conversation.add(buildInitialUserMessage(ticket));           // ticket details + instructions

    for (int i = 0; i < agentProperties.getMaxIterations(); i++) {
        Message response = anthropicClient.messages().create(buildRequest(conversation));
        if (response.stopReason() != StopReason.TOOL_USE) {
            return parseRootCause(response, steps, i + 1, false);  // final answer
        }
        for (ToolUseBlock toolUse : extractToolUses(response)) {
            String result = toolExecutor.execute(toolUse.name(), toolUse.input());
            steps.add(new StepRecord(toolUse.name(), toolUse.input().toString(), result));
            conversation.add(toolResultMessage(toolUse.id(), result));
        }
    }
    return truncatedHypothesis(steps);   // iteration cap reached
}
```

- **Tool:** `query_logs` — the only tool available in Phase 1. Its JSON schema exposes `service` (required), `from`/`to` (ISO-8601, optional — defaults to a window around the ticket's `reportedAt` if omitted), `keyword` (optional), `correlationId` (optional). `QueryLogsTool` delegates to `LogSource.query(...)` and serializes the `List<LogEntry>` result to JSON for the tool-result message.
- **System prompt** instructs Claude: investigate the ticket using `query_logs`, may call it multiple times to narrow down (e.g., broad keyword search first, then a follow-up scoped to a `correlationId` spotted in a stack trace), and must finish with a JSON object matching the `RootCauseHypothesis` schema (documented inline in the prompt) as its final text response — no tool call.
- **Response parsing:** the final text block is parsed as JSON into `RootCauseHypothesis`; a parse failure produces a `confidence: "low"` hypothesis with the raw text in `summary` and a note that structured parsing failed, rather than a 500 — mirrors `AgentService`'s graceful-degradation stance.

## REST API

```
POST /api/sdlc/investigate
{
  "ticketId": "DEMO-101"
}

200 OK
{
  "rootCause": {
    "summary": "NullPointerException in DiscountService.apply when discountCode is null",
    "evidence": ["2026-07-10T14:22:01Z ERROR checkout-service: java.lang.NullPointerException: ..."],
    "confidence": "high",
    "suspectedFiles": ["DiscountService.java", "CheckoutController.java"]
  },
  "iterations": 3,
  "steps": [
    {"tool": "query_logs", "input": "{service=checkout-service, keyword=NullPointerException}", "output": "[...]"}
  ],
  "truncated": false
}
```

Swagger UI at `/swagger-ui/index.html` (springdoc, matching every other module).

## Spring Boot configuration

**Spring Boot version:** 3.4.4
**Java:** 21

**Dependencies:** `spring-boot-starter-web`, [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) (same version as `task-automation-agent`), `springdoc-openapi-starter-webmvc-ui`, `lombok`, `spring-boot-starter-test` (test scope), `wiremock` or `mockwebserver` (test scope, for mocking Jira/Zendesk/Splunk HTTP calls in unit tests — mirrors how `code-review-agent`'s `GitHubClient` is tested).

**`application.yml`:**
```yaml
server:
  port: 8089

sdlc:
  ticket-source: jira   # or: zendesk

agent:
  max-iterations: 10

anthropic:
  model: claude-sonnet-4-6

jira:
  base-url: ${JIRA_BASE_URL:}
  email: ${JIRA_EMAIL:}
  api-token: ${JIRA_API_TOKEN:}
  service-field: customfield_10050

zendesk:
  subdomain: ${ZENDESK_SUBDOMAIN:}
  email: ${ZENDESK_EMAIL:}
  api-token: ${ZENDESK_API_TOKEN:}

splunk:
  base-url: https://localhost:8093
  api-token: ${SPLUNK_API_TOKEN:}
  trust-self-signed: true
  search-timeout-seconds: 10
```

Required secrets (`ANTHROPIC_API_KEY`, `JIRA_API_TOKEN`/`ZENDESK_API_TOKEN` depending on `sdlc.ticket-source`, `SPLUNK_API_TOKEN`) follow the same "documented prerequisite, env var, no defaults committed" pattern as `task-automation-agent`'s `ANTHROPIC_API_KEY`/`TAVILY_API_KEY`.

## Splunk infrastructure

```yaml
# ai/sdlc-agent/docker/docker-compose.yml
name: sdlc-agent-splunk

services:
  splunk:
    image: splunk/splunk:latest
    environment:
      - SPLUNK_START_ARGS=--accept-license
      - SPLUNK_PASSWORD=changeme123
      - SPLUNK_HEC_TOKEN=00000000-0000-0000-0000-000000000000
    ports:
      - "8000:8000"     # Web UI
      - "8092:8088"     # HEC (event collector) — remapped, 8088 is already used elsewhere in this repo
      - "8093:8089"     # REST/management API — remapped, 8089 is this module's own app port
    volumes:
      - splunk-var:/opt/splunk/var

volumes:
  splunk-var:
```

`docker/seed-logs.sh` posts the ~50 sample `checkout-service` log events (the `NullPointerException` scenario plus unrelated `INFO` noise) to `https://localhost:8092/services/collector/event` using the HEC token, run manually after `docker compose up -d` and a ~30s wait for Splunk to finish first-boot initialization.

A Splunk auth token for `SplunkLogSource`'s Bearer auth is generated once via the Web UI (`Settings → Tokens`) or `curl -u admin:changeme123 https://localhost:8093/services/authorization/tokens ...` — documented as a manual one-time setup step in the module README, since Splunk token generation isn't easily scriptable at container-start time within the community image.

## Testing

- **`JiraTicketSourceTest` / `ZendeskTicketSourceTest` / `SplunkLogSourceTest`** — mock the HTTP layer (WireMock or `MockWebServer`), assert correct request construction and response mapping, no real credentials or running services needed.
- **`QueryLogsToolTest`** — mocks `LogSource`, asserts JSON schema/serialization.
- **`InvestigateServiceTest`** — mocks `TicketSource`, `LogSource` (via the tool), and `AnthropicClient`, drives the loop through a scripted tool-use → final-answer sequence, asserts `RootCauseHypothesis` parsing (including the malformed-JSON fallback path).
- **`InvestigateControllerTest`** — `MockMvc`, `InvestigateService` mocked.
- **`SdlcAgentIntegrationTest`** — `@Tag("integration")`, excluded from `mvn test` by default (surefire `excludedGroups=integration`, same convention as `AgentIntegrationTest`), runs the full path against a real Jira/Zendesk ticket, real Splunk, and real Claude; run explicitly via `mvn test -Dtest=SdlcAgentIntegrationTest -Dgroups=integration`.

No Gatling load test — this module follows `task-automation-agent`'s convention (no `performance/` package there either), since load-testing an LLM-backed investigation endpoint isn't a meaningful signal the way it is for the deterministic broker/template-engine demos.

## README

`ai/sdlc-agent/spring-demo/README.md` follows `task-automation-agent/README.md`'s shape: prerequisites (API keys table), running instructions (env vars + `mvn spring-boot:run`), the Splunk docker-compose + seed-logs.sh + manual token-generation steps, a `curl` example against the `DEMO-101`/ticket-`1001` scenario with example response, build/test instructions (unit vs. `-Dgroups=integration`), module layout, tech stack.

`ai/sdlc-agent/README.md` (the existing concept doc) gets a one-line addition at the top of the Phase 1 row in its phased-build-plan table, linking to `spring-demo/` now that it exists.

## Scope limits

- No Fix/Deploy/Verify/Release stages — Phase 2/3 per the concept doc, require their own design pass and explicit human-approval gates before any code is written.
- No file-system or git tools in this phase — `query_logs` is the only tool Claude has access to.
- Ticket source selection is single-active-bean via config (`sdlc.ticket-source`), not simultaneous multi-source querying — matches the interface's intent (pluggable, not multiplexed) and keeps `InvestigateService` simple.
- Splunk token generation is a documented manual step, not automated — acceptable for a local demo; automating Splunk's token API reliably at container-first-boot is disproportionate effort for this module's teaching goal.
- No persistence — investigation results are returned in the HTTP response only, not stored; a future phase could persist `RootCauseHypothesis` history if useful.
- `service` field extraction from Jira/Zendesk (`jira.service-field` custom field, Zendesk tag prefix) is best-effort with an `"unknown"` fallback — real ticket systems vary widely in how they tag affected services, and this demo doesn't try to solve that generally.

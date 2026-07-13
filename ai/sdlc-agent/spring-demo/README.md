# SDLC Agent — Phase 1 (Intake + Investigate)

A Spring Boot app implementing Phase 1 of the [SDLC agent concept](../README.md): fetches a support ticket from Jira or Zendesk, agentically queries a real Splunk instance for correlated log evidence via a `query_logs` tool, and returns a structured root-cause hypothesis. Read-only — no file writes, git operations, deploy, or release.

## Prerequisites

| What | Where to get it |
|---|---|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `JIRA_API_TOKEN` (if `sdlc.ticket-source=jira`) | https://id.atlassian.com/manage-profile/security/api-tokens |
| `ZENDESK_API_TOKEN` (if `sdlc.ticket-source=zendesk`) | Zendesk Admin Center → Apps and integrations → APIs |
| `SPLUNK_API_TOKEN` | Generated locally — see below |

## Start Splunk and seed sample logs

```bash
docker compose -f ../docker/docker-compose.yml up -d
# wait ~30-60s for first-boot initialization, then verify:
curl -sk -u admin:changeme123 "https://localhost:8093/services/server/info?output_mode=json"
```

Seed the `checkout-service` sample log data (~50 events, including the `NullPointerException` scenario):

```bash
../docker/seed-logs.sh
```

Generate a Splunk auth token for `SplunkLogSource`'s Bearer auth (one-time, via the REST API — `name` must be an existing Splunk username, e.g. `admin`):

```bash
curl -sk -u admin:changeme123 -X POST "https://localhost:8093/services/authorization/tokens" \
  -d name=admin -d audience=sdlc-agent -d output_mode=json \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['entry'][0]['content']['token'])"
```

Use the printed value as `SPLUNK_API_TOKEN` below.

## Running

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export JIRA_BASE_URL=https://yourorg.atlassian.net
export JIRA_EMAIL=you@example.com
export JIRA_API_TOKEN=...
export SPLUNK_API_TOKEN=...

mvn spring-boot:run
```

App starts on **port 8089**.

## Try it

```bash
curl -s -X POST http://localhost:8089/api/sdlc/investigate \
  -H "Content-Type: application/json" \
  -d '{"ticketId": "DEMO-101"}' | jq .
```

Example response:

```json
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

Swagger UI: not included in this module (matches `ai/task-automation-agent`).

## Build & test

```bash
mvn clean package          # build
mvn test                   # unit tests, no API keys needed (Jira/Zendesk/Splunk mocked with WireMock)
```

To run the integration test (requires real API keys, a running seeded Splunk, and a real ticket):

```bash
mvn test -Dtest=SdlcAgentIntegrationTest -Dgroups=integration
```

## Configuration

All defaults are in `src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `sdlc.ticket-source` | `jira` | `jira` or `zendesk` — selects which `TicketSource` bean is active |
| `agent.max-iterations` | `10` | Maximum loop iterations before truncation |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model to use |
| `jira.service-field` | `customfield_10050` | Jira custom field mapped to `Ticket.service` |
| `zendesk.service-tag-prefix` | `""` | Only tags with this prefix are considered the service tag (empty = first tag) |
| `splunk.base-url` | `https://localhost:8093` | Splunk REST API base URL |
| `splunk.search-timeout-seconds` | `10` | How long to poll a search job before giving up (returns empty results) |
| `splunk.trust-self-signed` | `true` | Bypasses TLS verification for Splunk's self-signed dev certificate — local-dev only |

## Module layout

```
spring-demo/src/main/java/com/testingai/sdlc/
├── SdlcAgentApplication.java
├── config/          AppConfig, SdlcProperties, AgentProperties, AnthropicProperties, JiraProperties, ZendeskProperties, SplunkProperties
├── controller/      InvestigateController — POST /api/sdlc/investigate
├── service/         InvestigateService — agentic loop
├── ticket/          TicketSource, Ticket, JiraTicketSource, ZendeskTicketSource, AdfTextExtractor
├── log/             LogSource, LogEntry, SplunkLogSource
├── tool/            ToolExecutor, QueryLogsTool
└── model/           InvestigateRequest, InvestigateResponse, RootCauseHypothesis, StepRecord
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.40.1
- Jira REST API v3, Zendesk Support API, Splunk REST API — real external integrations, no mocks in production code
- [WireMock](https://wiremock.org) — HTTP mocking in unit tests

## Scope

Phase 1 only: intake + investigate, read-only. Fix (propose + commit a patch), Deploy, Verify, and Release remain future phases — see [`../README.md`](../README.md).

# Code Review Agent

## Concept

An agent that accepts a unified diff via REST (or GitHub PR webhook), runs Checkstyle and PMD as Claude tools against the changed Java files, filters findings to only lines touched by the PR, and returns typed findings — each with an AI-generated fix suggestion.

## Key Capabilities

- **Diff-aware analysis** — only findings on lines actually changed by the PR are surfaced; pre-existing issues on untouched lines are silently dropped
- **Tool-wrapped static analysis** — Checkstyle and PMD are Claude tool calls; Claude controls invocation and synthesises their raw output into typed `[{severity, file, line, message, suggestion}]` JSON
- **AI-generated suggestions** — neither Checkstyle nor PMD produces fix suggestions; Claude adds a concrete, actionable suggestion for every finding
- **GitHub webhook** — triggers on PR open/update, fetches the diff from GitHub, posts the review as inline comments; `REQUEST_CHANGES` if any `ERROR` severity finding is present, otherwise `COMMENT`
- **HMAC-SHA256 verification** — webhook requests are verified with constant-time signature comparison before any processing

---

A Spring Boot demo showing how to combine deterministic static analysis tools with an LLM synthesis step inside an agentic loop.

## How it works

```
POST /api/review/analyse  {"diff": "..."}
        │
        ▼
  ReviewService — agentic loop
        │
        ├─ 1. Send diff + tool definitions to Claude
        │
        ├─ 2. Claude calls run_checkstyle and run_pmd with the diff
        │         │
        │    ToolExecutor:
        │    ├─ DiffParser.parse(diff) → fileContents, changedLines
        │    ├─ Write Java files to temp dir
        │    ├─ CheckstyleTool / PmdTool → raw findings (absolute paths)
        │    ├─ Filter: keep only findings where line ∈ changedLines[file]
        │    └─ Normalise file paths → relative; return JSON
        │
        ├─ 3. Tool results fed back to Claude
        │
        └─ 4. Claude synthesises: deduplicates, adds suggestions, returns JSON array + summary
        │
        ▼
  ReviewResponse { findings[], summary }
```

GitHub webhook path:
```
POST /api/review/webhook  (X-Hub-Signature-256: sha256=...)
        │
  WebhookController verifies HMAC-SHA256
        │
  GitHubClient.fetchPrDiff(owner, repo, prNumber) → diff
        │
  Same ReviewService pipeline
        │
  GitHubClient.postReview(...) → POST /pulls/{n}/reviews
```

## Prerequisites

| What | Where to get it |
|---|---|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `GITHUB_TOKEN` | GitHub → Settings → Developer settings → Personal access tokens |
| `GITHUB_WEBHOOK_SECRET` | Set when registering the webhook in GitHub repo settings |

The webhook secret is only needed if you register a GitHub webhook. The `/api/review/analyse` endpoint works without it.

## Running

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export GITHUB_TOKEN=ghp_...
export GITHUB_WEBHOOK_SECRET=my-secret   # optional

cd spring-demo
mvn spring-boot:run
```

App starts on **port 8085**.

## Try it

```bash
curl -s -X POST http://localhost:8085/api/review/analyse \
  -H "Content-Type: application/json" \
  -d '{
    "diff": "diff --git a/Foo.java b/Foo.java\n--- a/Foo.java\n+++ b/Foo.java\n@@ -1,3 +1,6 @@\n public class Foo {\n+    public void bar() {\n+        String unused = \"hello\";\n+    }\n }\n"
  }' | jq .
```

Example response:

```json
{
  "findings": [
    {
      "severity": "WARNING",
      "file": "Foo.java",
      "line": 3,
      "message": "Unused local variable 'unused'",
      "suggestion": "Remove the variable declaration or use the value — assign it to a field or pass it to a method."
    }
  ],
  "summary": "1 warning found on changed lines."
}
```

## GitHub webhook setup

1. In your repo: Settings → Webhooks → Add webhook
2. Payload URL: `https://your-host/api/review/webhook`
3. Content type: `application/json`
4. Secret: same value as `GITHUB_WEBHOOK_SECRET`
5. Events: select "Pull requests"

## Build & test

```bash
cd spring-demo

mvn clean package          # build
mvn test                   # unit tests (no API keys needed)
```

To run the integration test (requires `ANTHROPIC_API_KEY` and the app running):

```bash
mvn test -Dtest=ReviewIntegrationTest -Dgroups=integration
```

## Configuration

All defaults are in `spring-demo/src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `reviewer.max-iterations` | `5` | Maximum agentic loop iterations |
| `reviewer.temp-dir` | `${java.io.tmpdir}` | Base directory for per-request temp dirs |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model to use |

## Module layout

```
spring-demo/src/main/java/com/testingai/reviewer/
├── ReviewApplication.java
├── config/
│   ├── AppConfig.java               AnthropicClient bean, gitHubRestClient bean
│   ├── AnthropicProperties.java     anthropic.* config
│   ├── ReviewerProperties.java      reviewer.* config
│   └── GitHubProperties.java        github.* config
├── controller/
│   ├── ReviewController.java        POST /api/review/analyse
│   └── WebhookController.java       POST /api/review/webhook (HMAC-SHA256 verified)
├── service/
│   ├── ReviewService.java           agentic loop + response parsing
│   ├── DiffParser.java             unified diff → fileContents + changedLines
│   └── GitHubClient.java           fetch PR diff, post review comments
├── tool/
│   ├── CheckstyleTool.java          Checkstyle 10.x Java API wrapper
│   ├── PmdTool.java                 PMD 7.x Java API wrapper
│   └── ToolExecutor.java            dispatch, temp dir lifecycle, diff-aware filtering
└── model/
    ├── ParsedDiff.java              { fileContents, changedLines }
    ├── RawFinding.java             { file, tool, rule, message, line }
    ├── Finding.java                { severity, file, line, message, suggestion }
    ├── ReviewRequest.java          { diff }
    ├── ReviewResponse.java         { findings, summary }
    └── WebhookPayload.java         GitHub PR event (nested records)
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.40.1
- [Checkstyle](https://checkstyle.org) 10.21.0 — programmatic Java API
- [PMD](https://pmd.github.io) 7.12.0 — `PmdAnalysis` programmatic API
- GitHub REST API — diff fetch + inline review comments

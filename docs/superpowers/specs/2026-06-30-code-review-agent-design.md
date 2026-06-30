# Code Review Agent Design

## Overview

A Spring Boot demo that shows what an AI-powered code review agent looks like in practice. It accepts a unified diff via REST (or GitHub PR webhook), runs Checkstyle and PMD against the changed Java files using their programmatic Java APIs, filters findings to only lines touched by the PR, feeds the raw results to Claude as agent tools, and returns typed findings with AI-generated fix suggestions.

## Decisions

- **Analyser invocation:** Checkstyle and PMD as Java library dependencies (not ProcessBuilder subprocesses). Cleaner error handling and no PATH dependency.
- **Scope:** Java only; Checkstyle + PMD bundled rulesets.
- **GitHub auth:** Personal access token (`GITHUB_TOKEN` env var).
- **Agentic pattern:** Same raw Anthropic Java SDK 2.40.1 approach as `task-automation-agent` — no framework abstraction over tool calling.
- **Port:** 8085.

---

## Section 1 — API Surface and Module Structure

**Entry points:**

| Endpoint | Purpose |
|---|---|
| `POST /api/review/analyse` | Manual: accepts `{ "diff": "..." }`, returns findings + summary |
| `POST /api/review/webhook` | GitHub: verifies HMAC-SHA256 signature, fetches PR diff, posts inline review comments |

**Response schema** (`ReviewResponse`):
```json
{
  "findings": [
    {
      "severity": "WARNING",
      "file": "src/main/java/com/example/Foo.java",
      "line": 12,
      "message": "Method length exceeds 30 lines",
      "suggestion": "Extract the inner block into a private helper method."
    }
  ],
  "summary": "1 error, 2 warnings found on changed lines."
}
```

**Module layout:**
```
ai/code-review-agent/spring-demo/
└── src/main/java/com/testingai/reviewer/
    ├── ReviewApplication.java
    ├── config/
    │   ├── AppConfig.java               AnthropicClient bean
    │   ├── AnthropicProperties.java     anthropic.* (api-key, model)
    │   ├── ReviewerProperties.java      reviewer.* (temp-dir, max-iterations)
    │   └── GitHubProperties.java        github.* (token, webhook-secret)
    ├── controller/
    │   ├── ReviewController.java        POST /api/review/analyse
    │   └── WebhookController.java       POST /api/review/webhook
    ├── service/
    │   ├── ReviewService.java           orchestration: parse → analyse → filter → synthesise
    │   ├── DiffParser.java              unified diff → file contents + changed-line sets
    │   └── GitHubClient.java            fetch PR diff, post review comments
    ├── tool/
    │   ├── CheckstyleTool.java          Checkstyle Java API wrapper
    │   ├── PmdTool.java                 PMD Java API wrapper
    │   └── ToolExecutor.java            dispatch by tool name, temp dir lifecycle
    └── model/
        ├── ReviewRequest.java           record: { String diff }
        ├── Finding.java                 record: { String severity, file, message, suggestion; int line }
        ├── ReviewResponse.java          record: { List<Finding> findings, String summary }
        ├── RawFinding.java              record: { String file, tool, rule, message; int line }
        └── WebhookPayload.java          GitHub PR event fields (action, pr number, owner, repo)
```

**Bundled resources:**
```
src/main/resources/
├── checkstyle/checkstyle.xml    Google style subset: naming, line-length, Javadoc, whitespace
└── pmd/pmd-ruleset.xml          PMD Best Practices + Error Prone categories
```

---

## Section 2 — Analysis Pipeline

### Diff parsing (`DiffParser`)

Input: unified diff string.

Output:
- `Map<String, String> fileContents` — full new content of each `.java` file in the diff
- `Map<String, Set<Integer>> changedLines` — line numbers that were added or modified per file

Algorithm: scan `+++` headers to track current filename; collect lines prefixed `+` (not `+++`) as changed lines and accumulate them into file content. Lines prefixed `-` are skipped (deleted lines don't exist in the new version and cannot have findings).

Only `.java` files are processed; other file types are skipped silently.

### Temp file lifecycle (`ToolExecutor`)

Before invoking either analyser, `ToolExecutor`:
1. Creates a temp directory under `reviewer.temp-dir` (default: `System.getProperty("java.io.tmpdir")`) named `review-<UUID>`
2. Writes each file from `fileContents` preserving its package path (e.g. `src/main/java/com/example/Foo.java`)
3. Passes the temp directory root to each tool
4. Deletes the entire temp directory in a `finally` block after both tools complete

### CheckstyleTool

Uses `com.puppycrawl.tools.checkstyle.Checker` programmatically:
1. Loads XML config from classpath `checkstyle/checkstyle.xml` via `ConfigurationLoader`
2. Registers a collecting `AuditListener` that accumulates `AuditEvent` objects
3. Calls `checker.process(files)` on the temp files
4. Returns `List<RawFinding>` — each `AuditEvent` maps to `{file, line, rule=localizedMessage.getSourceName(), message}`

### PmdTool

Uses `net.sourceforge.pmd.PmdAnalysis`:
1. Builds `PMDConfiguration` pointing at the temp directory with language `java`
2. Loads ruleset from classpath `pmd/pmd-ruleset.xml`
3. Calls `pmdAnalysis.performAnalysisAndCollectReport()` → iterates `report.getViolations()`
4. Returns `List<RawFinding>` — each `RuleViolation` maps to `{file, line, rule=getRule().getName(), message}`

### Diff-aware filtering (`ToolExecutor`)

Each tool call handler in `ToolExecutor` receives the full diff string from Claude's tool input. It calls `DiffParser` internally to get `changedLines`, runs the analyser, then filters before returning:

```java
changedLines.getOrDefault(finding.file(), Set.of()).contains(finding.line())
```

Filtering inside the tool handler means Claude receives only changed-line findings — never pre-existing issues. `DiffParser` is stateless so calling it twice per request is safe.

---

## Section 3 — Agentic Loop

Claude's role is synthesis: the static analysers always run before any Claude interaction. Claude turns raw, overlapping tool findings into typed `Finding` objects with `suggestion` fields that the analysers cannot produce.

**Two Claude tools are defined:**

| Tool | Input schema | What `ToolExecutor` does |
|---|---|---|
| `run_checkstyle` | `{ "diff": "string" }` | runs `CheckstyleTool`, returns `List<RawFinding>` as JSON |
| `run_pmd` | `{ "diff": "string" }` | runs `PmdTool`, returns `List<RawFinding>` as JSON |

**Loop flow:**

```
ReviewService.analyse(diff)
        │
        ├─ DiffParser → fileContents, changedLines
        │
        ├─ Build initial user message:
        │    "You are a Java code reviewer. The diff below shows only the changed lines.
        │     Call run_checkstyle and run_pmd with the diff to get static analysis findings.
        │     Then synthesise ALL findings (deduplicated) into a JSON array:
        │     [{severity, file, line, message, suggestion}]
        │     where suggestion is a concrete fix. Follow with a one-sentence summary.
        │     <diff>...</diff>"
        │
        ├─ Claude calls run_checkstyle(diff) and run_pmd(diff)
        │       → ToolExecutor runs CheckstyleTool + PmdTool, applies diff filter
        │       → returns raw findings JSON per tool
        │
        └─ Claude returns final text: JSON array + summary
               → ReviewService parses into ReviewResponse
```

The loop typically completes in **2 iterations** (one tool-call turn, one synthesis turn). The iteration cap (`reviewer.max-iterations`, default `5`) is the same safety valve pattern as `task-automation-agent`. If Claude returns no tool calls on the first turn, `ReviewService` treats its text as a direct synthesis response.

`ToolChoiceAuto` is used — Claude decides when it has enough information to synthesise.

---

## Section 4 — GitHub Webhook

### HMAC verification (`WebhookController`)

`POST /api/review/webhook`:
1. Reads raw request body as `byte[]` — Spring must not pre-parse it (use `HttpServletRequest.getInputStream()`)
2. Computes `HMAC-SHA256(github.webhook-secret, rawBody)` using `javax.crypto.Mac`
3. Compares to `X-Hub-Signature-256` header (format: `sha256=<hex>`) with `MessageDigest.isEqual` (constant-time, prevents timing attacks)
4. Returns `403` on mismatch; `204` on events other than `action=opened` or `action=synchronize`
5. Parses JSON body to `WebhookPayload` using Jackson; delegates to `ReviewService`

### Fetching the PR diff (`GitHubClient.fetchPrDiff`)

```
GET /repos/{owner}/{repo}/pulls/{number}
Accept: application/vnd.github.diff
Authorization: Bearer ${GITHUB_TOKEN}
```

Returns the raw unified diff string — same format accepted by `/api/review/analyse`.

### Posting the review (`GitHubClient.postReview`)

```
POST /repos/{owner}/{repo}/pulls/{number}/reviews
Authorization: Bearer ${GITHUB_TOKEN}
Content-Type: application/json

{
  "event": "REQUEST_CHANGES",   // if any ERROR severity finding; else "COMMENT"
  "body": "<summary>",
  "comments": [
    {
      "path": "src/main/java/com/example/Foo.java",
      "line": 12,
      "body": "**WARNING — CheckstyleRule**: Method too long.\n\n**Suggestion**: Extract inner block into a private helper."
    }
  ]
}
```

Uses `RestClient` (same Spring 6 pattern as `task-automation-agent`'s Tavily client).

### Configuration

```yaml
server:
  port: 8085

reviewer:
  max-iterations: 5
  temp-dir: ${java.io.tmpdir}

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

github:
  token: ${GITHUB_TOKEN}
  webhook-secret: ${GITHUB_WEBHOOK_SECRET}
```

---

## Tech Stack

- Java 21, Spring Boot 3.4.4
- Anthropic Java SDK 2.40.1 (raw tool-use API, same as `task-automation-agent`)
- Checkstyle 10.x — programmatic Java API
- PMD 7.x — `PmdAnalysis` programmatic API
- WireMock standalone 3.5.4 — `GitHubClient` HTTP mocking in tests
- Lombok 1.18.38 (`<lombok.version>` property override — required for Java 25 IDE compatibility)

---

## Testing Strategy

| Test class | Scope | Approach |
|---|---|---|
| `DiffParserTest` | unit | parse known unified diff strings, assert `fileContents` and `changedLines` maps |
| `CheckstyleToolTest` | unit | write a known-bad Java file to a temp dir, assert expected `RawFinding` |
| `PmdToolTest` | unit | same for PMD |
| `ReviewServiceTest` | unit | mock `CheckstyleTool` + `PmdTool` + `AnthropicClient` (ByteBuddy mock maker), assert diff filtering and response parsing |
| `ReviewControllerTest` | unit | `@WebMvcTest`, assert HTTP contract |
| `WebhookControllerTest` | unit | `@WebMvcTest`, test HMAC accept/reject with precomputed fixtures |
| `GitHubClientTest` | unit | WireMock — assert correct `Accept` header on diff fetch, assert review POST body shape |
| `ReviewIntegrationTest` | integration | `@Tag("integration")`, excluded from `mvn test` |

No Gatling load test — the pipeline is dominated by Claude API latency, not throughput.

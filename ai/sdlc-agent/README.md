# SDLC Agent — Future Investigation

## Concept

An agent that takes a support/bug ticket through the full lifecycle: pulls the ticket from Jira or Zendesk, correlates it against production logs to form a root-cause hypothesis, proposes and commits a code fix on a hotfix branch, deploys that fix to a test environment, runs automated tests against it, and — once green — cuts a tagged release. Each stage after the fix is human-gated by default; full autonomy through to production release is an explicit non-goal of this doc.

## Pipeline stages

```
Ticket (Jira/Zendesk)
   │
   ▼
1. INTAKE       — TicketSource.fetch(id) → normalized Ticket{title, description, severity, service}
   │
   ▼
2. INVESTIGATE  — LogSource.query(service, timeWindow, correlationId) → LogEntry[]
                  Claude correlates ticket symptoms with log evidence → RootCauseHypothesis
   │
   ▼
3. FIX          — Claude, with read_file/list_files/write_file/git_commit_branch tools,
                  proposes and commits a fix to a hotfix/<TICKET-ID> branch in a disposable sandbox repo
   │
   ▼
4. DEPLOY       — mvn spring-boot:build-image → docker compose -f test-env/docker-compose.yml up -d
   │
   ▼
5. VERIFY       — mvn gatling:test / smoke-test HTTP checks against the deployed test env
   │
   ▼
6. RELEASE      — on green: git tag vX.Y.Z && git push --tags (human-gated by default)
```

## Pluggable abstractions

Ticket and log sources are modeled as interfaces rather than tied to one vendor's API shape, so a real implementation could swap sources without touching the agent loop:

```java
interface TicketSource {
    Ticket fetch(String ticketId);
}
class JiraTicketSource implements TicketSource { /* Jira REST API v3 */ }
class ZendeskTicketSource implements TicketSource { /* Zendesk Support API */ }

record Ticket(String id, String title, String description, String severity, String service, Instant reportedAt) {}

interface LogSource {
    List<LogEntry> query(String service, Instant from, Instant to, String keyword, String correlationId);
}
class SplunkLogSource implements LogSource { /* implemented in Phase 1 — Splunk REST search-job API */ }

record LogEntry(Instant timestamp, String service, String level, String message, String correlationId) {}
```

Both would be selected as Spring beans via config, e.g. `sdlc.ticket-source=jira|zendesk` / `sdlc.log-source=elasticsearch`.

`RootCauseHypothesis{summary, evidence[], confidence, suspectedFiles[]}` is the structured output Claude produces from stage 2, and becomes the seed for stage 3's coding prompt.

## The Fix stage's tool surface

Extends this repo's existing tool-wrapped-static-analysis pattern (see `ai/code-review-agent`) with write capability:

```java
tools: read_file, list_files, write_file, git_commit_branch
```

- `read_file(path)` / `list_files(dir)` — scoped to a disposable sandbox repo root, never outside it (path traversal guarded, same spirit as `code-review-agent`'s temp-dir isolation).
- `write_file(path, content)` — overwrites/creates the file with the given content. Replaces this doc's original `write_patch(path, diff)` sketch: full-file replacement is far more reliable for an LLM to produce correctly than a unified diff with accurate line offsets. A real unified diff is still produced afterward via JGit for the API response.
- `git_commit_branch(branchName, message)` — creates `hotfix/<TICKET-ID>` off the current HEAD and commits; never pushes or merges on its own.

The sandbox itself is a fresh, disposable git repo created per request (seeded with Phase 1's `checkout-service` bug scenario), not a configurable arbitrary `target-repo.path` — see [`spring-demo/`](spring-demo/) for why.

## Phased build plan

| Phase | Stages | Status |
|---|---|---|
| **Phase 1 — implemented** | Intake + Investigate | Read-only, no side effects. See [`spring-demo/`](spring-demo/) for the working implementation (Jira/Zendesk ticket intake, Splunk as the log source). |
| **Phase 2 — implemented** | Fix (propose + commit to a branch, never push/merge) | See [`spring-demo/`](spring-demo/) — `write_file` replaces the original `write_patch(diff)` sketch; operates on a disposable sandbox repo seeded with Phase 1's bug scenario, not a configurable arbitrary `target-repo.path`. A human still reviews the branch before it goes anywhere real. |
| **Phase 3 — future/exploratory** | Deploy + Verify + Release | Unbuilt. These stages touch running infrastructure and a real release process, so they need their own design pass — and explicit human approval gates — before any code is written. |

## Suggested Stack

- Java 21, Spring Boot 3.4.4, Anthropic Java SDK (`AnthropicClient`, tool-use agentic loop) — same as `ai/task-automation-agent` and `ai/code-review-agent`.
- Git operations via [JGit](https://www.eclipse.org/jgit/) (pure-Java, no shelling out) for `git_commit_branch`.
- Deploy/verify stages reuse this repo's existing `spring-boot:build-image` (Cloud Native Buildpacks, already implicit in every module's `spring-boot-maven-plugin`) and Gatling, rather than introducing new tooling.

## Starting Points

- [Jira REST API v3](https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/) — ticket intake
- [Zendesk Support API](https://developer.zendesk.com/api-reference/ticketing/introduction/) — ticket intake
- [JGit](https://www.eclipse.org/jgit/) — programmatic git operations for the Fix stage
- `ai/code-review-agent` — closest existing reference implementation for the tool-wrapped-static-analysis pattern the Fix stage extends
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) — agentic loop and tool-use, already used by `ai/task-automation-agent` and `ai/code-review-agent`

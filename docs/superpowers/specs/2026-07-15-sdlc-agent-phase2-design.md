# SDLC Agent — Phase 2 (Fix) Design

**Date:** 2026-07-15
**Status:** Approved

## Overview

Implements Phase 2 of `ai/sdlc-agent/README.md`'s phased plan: an agent that takes Phase 1's `RootCauseHypothesis` and proposes an actual code fix — reading, writing, and committing files via `read_file`/`list_files`/`write_file`/`git_commit_branch` tools — scoped entirely to a disposable sandbox git repository created fresh per request. Never pushes, merges, or touches any real codebase. `POST /api/sdlc/fix` re-runs Phase 1's investigation internally, so one call demonstrates the full Investigate → Fix story. The sandbox repo is seeded with the exact `checkout-service` `NullPointerException` bug that Phase 1's Splunk logs already point to, closing the loop between what the agent found and what it fixes.

## Repository structure

```
ai/sdlc-agent/spring-demo/
├── pom.xml                                        (+ org.eclipse.jgit:org.eclipse.jgit dependency)
└── src/
    ├── main/
    │   ├── java/com/testingai/sdlc/
    │   │   ├── config/
    │   │   │   └── SandboxProperties.java          sandbox.* config (cleanup flag)
    │   │   ├── controller/
    │   │   │   └── FixController.java              POST /api/sdlc/fix
    │   │   ├── service/
    │   │   │   ├── InvestigationLoop.java           extracted shared loop (ticket -> RootCauseHypothesis)
    │   │   │   ├── InvestigateService.java           unchanged public API, delegates to InvestigationLoop
    │   │   │   └── FixService.java                   Fix agentic loop
    │   │   ├── sandbox/
    │   │   │   ├── SandboxRepo.java                  create/diff/cleanup via JGit
    │   │   │   └── SandboxPathGuard.java             path-traversal guard shared by all 4 tools
    │   │   ├── tool/
    │   │   │   ├── ReadFileTool.java
    │   │   │   ├── ListFilesTool.java
    │   │   │   ├── WriteFileTool.java
    │   │   │   ├── GitCommitBranchTool.java
    │   │   │   └── FixToolExecutor.java              dispatches the 4 fix-stage tools
    │   │   └── model/
    │   │       ├── FixRequest.java                   { ticketId }
    │   │       └── FixResponse.java                  { rootCause, patch, branchName, commitSha, iterations, steps, truncated }
    │   └── resources/
    │       └── sandbox-repo-template/
    │           └── src/main/java/com/example/checkout/
    │               ├── DiscountService.java           seeded bug: NPE on null discountCode
    │               └── CheckoutController.java
    └── test/
        ├── java/com/testingai/sdlc/
        │   ├── sandbox/SandboxRepoTest.java            real JGit + real temp dirs, no mocking
        │   ├── sandbox/SandboxPathGuardTest.java
        │   ├── tool/ReadFileToolTest.java
        │   ├── tool/ListFilesToolTest.java
        │   ├── tool/WriteFileToolTest.java
        │   ├── tool/GitCommitBranchToolTest.java
        │   ├── tool/FixToolExecutorTest.java
        │   ├── service/FixServiceTest.java             mocked AnthropicClient (as InvestigateServiceTest), real sandbox
        │   ├── controller/FixControllerTest.java
        │   └── integration/SdlcAgentIntegrationTest.java   (existing file, gains a fix() test)
        └── resources/application.yml
```

## Sandbox repo template

Bundled as a classpath resource, copied into a fresh temp directory per request:

```java
// DiscountService.java (seeded bug)
public class DiscountService {
    public BigDecimal apply(BigDecimal price, String discountCode) {
        if (discountCode.length() > 0) {   // NPE when discountCode is null
            return price.multiply(BigDecimal.valueOf(0.9));
        }
        return price;
    }
}
```

```java
// CheckoutController.java
public class CheckoutController {
    private final DiscountService discountService;

    public BigDecimal checkout(BigDecimal price, String discountCode) {
        return discountService.apply(price, discountCode);
    }
}
```

This is the exact stack trace already seeded into Splunk by Phase 1's `seed-logs.sh` (`DiscountService.java:42`, `CheckoutController.java:58`) — a correct fix adds a null check in `DiscountService.apply`.

## `SandboxRepo`

```java
public class SandboxRepo {
    Path root;
    String initialCommitSha;

    static SandboxRepo create();          // copy template -> temp dir, git init, stage, commit "Initial commit"
    String diffAgainstInitialCommit();     // JGit diff between initialCommitSha and working tree, unified format
    void cleanup();                        // recursive delete of the temp dir
}
```

- Temp dir: `Files.createTempDirectory("sdlc-sandbox-")`.
- `git init` + initial commit via JGit's `Git.init()` / `Git.add()` / `Git.commit()` — no shelling out to the `git` binary.
- `cleanup()` always runs in a `finally` block around the fix loop; gated by `sandbox.cleanup=true` (default) so it can be disabled for demo inspection.

## `SandboxPathGuard`

Shared by all four tools:

```java
public final class SandboxPathGuard {
    static Path resolve(Path sandboxRoot, String relativePath); // throws if it escapes sandboxRoot
}
```

Resolves `relativePath` against `sandboxRoot`, normalizes (`Path.normalize()`), and rejects (throws `IllegalArgumentException`, caught and turned into a tool-error JSON string, same pattern as Phase 1's tools) if the normalized path is not a descendant of `sandboxRoot` — covers `../` traversal and absolute-path escapes.

## Tool surface

| Tool | Signature | Behavior |
|---|---|---|
| `read_file` | `(path)` | Reads file content relative to sandbox root; error JSON if missing or path escapes root |
| `list_files` | `(dir)` | Lists file names under a directory relative to sandbox root (non-recursive; sufficient for the small template) |
| `write_file` | `(path, content)` | Overwrites/creates the file with `content`; parent directories created if needed, still guarded |
| `git_commit_branch` | `(branchName, message)` | Creates `branchName` off the initial commit via JGit, stages all changes, commits; returns the commit SHA |

Replaces the concept doc's original `write_patch(path, diff)` — full-file replacement is far more reliable for an LLM to produce correctly than a unified diff with accurate line offsets; a real unified diff is still produced afterward via `SandboxRepo.diffAgainstInitialCommit()` for the API response.

`FixToolExecutor` mirrors `ToolExecutor`'s dispatch-by-name pattern, switching on tool name and delegating to the four tool classes, each of which takes the `SandboxRepo` (or just its root `Path`) as a constructor/method argument scoped per-request (not a singleton Spring bean, since each request gets its own sandbox).

## Agentic loop

`InvestigateService`'s existing loop is split so `FixService` can reuse the "ticket → `RootCauseHypothesis`" portion without duplicating it or making an internal HTTP call:

```java
@Component
public class InvestigationLoop {
    RootCauseHypothesis investigate(Ticket ticket); // the loop body, extracted from InvestigateService.investigate
}
```

`InvestigateService.investigate(ticketId)` becomes: fetch ticket, delegate to `InvestigationLoop`, wrap in `InvestigateResponse` — identical external behavior and API contract, `InvestigateServiceTest` unaffected.

`FixService.fix(ticketId)`:

```java
public FixResponse fix(String ticketId) {
    Ticket ticket = ticketSource.fetch(ticketId);
    RootCauseHypothesis rootCause = investigationLoop.investigate(ticket);

    SandboxRepo sandbox = SandboxRepo.create();
    try {
        List<StepRecord> steps = new ArrayList<>();
        List<MessageParam> messages = new ArrayList<>();
        messages.add(buildInitialPrompt(rootCause));   // seeded with summary/evidence/suspectedFiles

        int iterations = 0;
        String branchName = null;
        String commitSha = null;
        while (iterations < agentProperties.maxIterations()) {
            // same loop shape as InvestigateService: call Claude with the 4 tools,
            // dispatch tool_use blocks via FixToolExecutor, append tool_result messages
            // ...
            // when git_commit_branch is called, capture branchName/commitSha from its result
            // loop ends when Claude returns final text (a short summary) or the cap is hit
        }
        String patch = sandbox.diffAgainstInitialCommit();
        return new FixResponse(rootCause, patch, branchName, commitSha, steps, iterations, truncated);
    } finally {
        sandbox.cleanup();
    }
}
```

System prompt instructs Claude: given the root-cause hypothesis and suspected files, use `read_file`/`list_files` to inspect the sandbox, `write_file` to apply a fix, and finish by calling `git_commit_branch("hotfix/<TICKET-ID>", "<commit message>")` exactly once, then respond with a short plain-text summary of what was changed (no JSON parsing needed for the final answer here, unlike Phase 1 — the structured output is the diff/branch/commit, not a Claude-authored JSON blob).

## REST API

```
POST /api/sdlc/fix
{
  "ticketId": "DEMO-101"
}

200 OK
{
  "rootCause": { "summary": "...", "evidence": [...], "confidence": "high", "suspectedFiles": [...] },
  "patch": "diff --git a/src/main/java/com/example/checkout/DiscountService.java ...",
  "branchName": "hotfix/DEMO-101",
  "commitSha": "a1b2c3d...",
  "iterations": 4,
  "steps": [
    {"tool": "list_files", "input": "{dir=src/main/java/com/example/checkout}", "output": "[...]"},
    {"tool": "read_file", "input": "{path=...DiscountService.java}", "output": "..."},
    {"tool": "write_file", "input": "{path=..., content=...}", "output": "{\"status\":\"written\"}"},
    {"tool": "git_commit_branch", "input": "{branchName=hotfix/DEMO-101, message=...}", "output": "{\"branch\":\"hotfix/DEMO-101\",\"commitSha\":\"a1b2c3d\"}"}
  ],
  "truncated": false
}
```

## Spring Boot configuration

**New dependency:** `org.eclipse.jgit:org.eclipse.jgit` (latest stable compatible with Java 21) — pure-Java git, no shelling out.

**`application.yml` addition:**
```yaml
sandbox:
  cleanup: true
```

No new secrets — Phase 2 reuses Phase 1's `ANTHROPIC_API_KEY`/`JIRA_API_TOKEN`/`SPLUNK_API_TOKEN` and adds no external integrations of its own.

## Testing

- **`SandboxRepoTest`, `SandboxPathGuardTest`, tool tests** — real JGit operations against real temp directories; no mocking needed (unlike Phase 1's Jira/Zendesk/Splunk clients, there's no external HTTP surface here).
- **`FixServiceTest`** — mocks `AnthropicClient` exactly like `InvestigateServiceTest` (`Answers.RETURNS_DEEP_STUBS`, scripted tool-use → final-text sequence), but `FixToolExecutor`/`SandboxRepo` are real, operating on a real temp sandbox — asserts the resulting branch/commit/diff are real and correct via JGit assertions after the loop runs.
- **`FixControllerTest`** — `@WebMvcTest`, `FixService` mocked, matches `InvestigateControllerTest`'s shape.
- **Integration test** — `SdlcAgentIntegrationTest` (existing file) gains a `fix_withRealApis_createsHotfixBranch()` test, still `@Tag("integration")`, excluded by default.

## Scope limits

- No push, merge, or PR/MR creation — `git_commit_branch` only ever commits locally within the disposable sandbox.
- No file deletion or rename tools — `write_file` (create/overwrite) is the only mutation, keeping the guard logic and tool surface minimal.
- No configurable arbitrary `target-repo.path` — the bundled sandbox-template approach is final for this phase, not a stepping stone to pointing at real repos; that would need its own design pass (real repos need much stronger guardrails than a disposable temp dir).
- Sandbox scope is the two seeded files (`DiscountService.java`, `CheckoutController.java`) — not a realistic multi-module codebase; `list_files` is deliberately non-recursive since the template is small enough not to need it.
- `git_commit_branch` calling more than once per run is not specially handled beyond "Claude is instructed to call it once" — a second call would just amend further commits onto the same branch; not guarded against, since the agentic loop's iteration cap already bounds worst-case behavior.

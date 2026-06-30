# Code Review Agent — Future Investigation

## Concept

An agent that accepts a code snippet or GitHub PR diff via REST, analyses it using structured tools (static analysers, linters), and returns categorised, diff-aware findings as typed JSON.

## Key Capabilities to Explore

- **Tool-wrapped static analysis** — shell out to real tools (Checkstyle, PMD, ESLint, SpotBugs) as agent tools; Claude synthesises structured output from their reports
- **Diff-aware feedback** — only surface issues on lines touched by the PR, mirroring the project's existing `.claude/rules/code-review.md` conventions
- **Typed findings** — return `[{severity, file, line, message, suggestion}]` rather than free-form prose so callers can post inline GitHub review comments
- **GitHub webhook** — trigger automatically on PR open/update via a Spring MVC webhook endpoint, post results back as inline comments via the GitHub REST API

## Suggested Stack

- Spring Boot + Anthropic Java SDK (same raw pattern as `task-automation-agent`)
- GitHub App or personal access token for posting review comments
- Docker sidecar or `ProcessBuilder` for running linters in isolation

## Starting Points

- GitHub REST API: POST `/repos/{owner}/{repo}/pulls/{pull_number}/reviews`
- Anthropic tool use: define one tool per analyser, aggregate results before synthesis
- Model: `claude-sonnet-4-6` for speed/cost balance on large diffs

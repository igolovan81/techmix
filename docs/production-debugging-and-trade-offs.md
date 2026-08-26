# Production Debugging and Trade-offs

A reference for approaching "how do you debug production issues" style system design/behavioral questions — general principles, not tied to a specific service in this repo.

## The core challenge

Production bugs are fundamentally different from bugs caught in dev/test: you usually can't reproduce them on demand (different scale, different data, different concurrency, an intermittent race), you can't freely pause execution to inspect state without affecting real users, and every action taken to investigate has a blast radius. Good production debugging is as much about *how carefully you look* as it is about tooling.

## Techniques, roughly in order of how invasive they are

- **Structured logs + correlation IDs** — the baseline. A trace/correlation ID generated at the edge and propagated through every downstream call and log line lets you reconstruct one request's full path after the fact, without needing to be watching when it happened. This only works if it was built in before the incident — logs that were never tagged can't be retroactively correlated.
- **Metrics dashboards** — narrow down *when* and *where* before diving into *why*. A latency/error-rate spike correlated against a deploy timeline, a config change, or a traffic pattern usually gets you to the right service or the right commit faster than reading logs cold.
- **Distributed tracing** (OpenTelemetry/Jaeger/Zipkin-style) — once the request path is known, a trace shows the actual call graph and per-hop timing, often the only way to find a problem that's structural (an N+1 call pattern, a serial chain that should be parallel) rather than a single bad line of code.
- **Log aggregation and search** (ELK/Splunk/Datadog-style) — necessary the moment there's more than a handful of instances; SSH-ing into boxes to `grep` a log file doesn't scale past a toy system.
- **Runtime introspection** (thread dumps, heap dumps, flame graphs from a profiler like async-profiler or JFR) — for performance problems that logs and traces don't explain (a deadlock, a memory leak, unexpected GC pressure). This is the most invasive tier: attaching a profiler or forcing a heap dump on a live JVM can pause it at a safepoint and briefly degrade the very service being debugged.
- **Feature flags** — disable a suspect code path or bisect which recent change caused a regression *without a redeploy*, both faster and safer than shipping a fix-forward or rolling back a whole release.
- **Shadow traffic / replay** — mirror real production requests against a candidate build in a sandbox to catch issues before they ever touch real users. Powerful for catching regressions that only manifest under real data shapes, but only as good as how representative the replayed traffic actually is.

## The trade-offs

**Mitigate first, understand later.** During an active incident, the instinct to fully root-cause before acting is usually wrong. Rolling back or toggling a feature flag to stop user impact takes priority over full understanding — the deep root-cause work happens afterward, in a postmortem, without users still being affected while it's investigated.

**Observability cost vs. blast radius of not having it.** Verbose logging and full-fidelity tracing cost real money (ingestion, storage) and can themselves degrade performance (I/O overhead on the hot path). Sampling traces/logs is the standard mitigation, but sampling means rare bugs — the ones that most need investigation — are the ones most likely to be sampled out. There's no clean answer here, only a deliberate choice of where on that curve a system sits.

**Debug live vs. reproduce locally.** Reproducing locally is safer and allows a real debugger, but many production bugs (races, scale-dependent behavior, real data shape) simply never reproduce outside production. Debugging live is faster to diagnose but every tool attached has some risk to real traffic — this is why the invasiveness ordering above matters: reach for the least invasive tool that can actually answer the question.

**PII/security vs. debuggability.** The data most needed to debug a customer-specific issue is often exactly the data that needs to be redacted from logs for compliance reasons. Redaction makes logs safer and less useful at the same time — the usual resolution is a separate, access-controlled, audited path to look up unredacted data for a specific investigation rather than logging it broadly by default.

**Built-in from day one vs. bolted on under pressure.** Correlation IDs, structured logging, and tracing are cheap to add when a service is first built and expensive/awkward to retrofit once it's already in production and something is on fire. The teams with the best production-debugging experience treat observability as a design requirement, not an incident-response afterthought.

**Toil of maintaining the tooling vs. shipping features.** Dashboards, alert rules, and tracing instrumentation all rot if nobody owns them — stale alerts get ignored (alert fatigue), stale dashboards get distrusted. Investing in observability has an ongoing maintenance cost, not just a one-time build cost, and that has to be weighed against feature velocity honestly rather than assumed to be free.

## After the fire is out

A blameless postmortem — what happened, why the existing safeguards didn't catch it, and concrete follow-up actions with owners — is what turns one incident into a permanently better system instead of just a story. The trade-off there is thoroughness vs. speed of closing it out: a postmortem that takes three weeks to publish loses most of its value, but one written in an hour under pressure usually misses the actual systemic cause in favor of the most visible symptom.

## Related demos in this repo

- Structured logging across a full request lifecycle (job start/success/retry/dead-letter, page fetches, webhook accept/reject, scheduled passes) plus Micrometer/Prometheus metrics and a human-readable status endpoint: [`data-integration/survey-monkey-import`](../data-integration/survey-monkey-import/)
- Circuit breaker state-transition visibility (`CLOSED → OPEN → HALF_OPEN`) as a concrete example of surfacing failure-mode state that's otherwise invisible between polling intervals: [`data-integration/survey-monkey-import/importer-demo`](../data-integration/survey-monkey-import/importer-demo/)

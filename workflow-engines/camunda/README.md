# Camunda 8 Demo

Demonstrates Camunda 8 (built on the Zeebe workflow engine) — a BPMN 2.0 process orchestration platform where the process is a visual diagram, not hand-written control-flow code — via one Spring Boot app (`spring-demo`) covering service tasks, an exclusive gateway, a user task, and error-boundary-driven failure routing.

This reuses the exact order-fulfillment domain from [`distributed-transactions/saga`](../../distributed-transactions/saga/) (reserve inventory → process payment → arrange shipping) so the two demos are directly comparable: saga hand-writes orchestration and compensation in Java; this demo expresses the same process as a `.bpmn` diagram, with Camunda's engine driving execution.

## The four patterns

| Pattern | BPMN element | What it demonstrates |
|---|---|---|
| Service tasks | `Reserve Inventory`, `Process Payment`, `Arrange Shipping`, `Release Inventory` | External worker processes (`@JobWorker`) polling/executing units of work — Camunda's equivalent of saga's hand-written participants, but declared in the process model rather than Java control flow |
| Exclusive gateway | `High-Value Order?` | Conditional branching expressed declaratively in the process diagram (a FEEL expression on the sequence flow) instead of an `if` statement buried in orchestrator code |
| User task | `Approve Order` | Human-in-the-loop step — something saga's code-only orchestrator has no first-class way to express; the process instance genuinely pauses until a human (or, in this demo, a REST call) completes it |
| Error boundary event | attached to `Reserve Inventory` / `Process Payment` | Declarative failure routing to a cleanup path, BPMN's alternative to saga's manually-written `compensate()` step-unwinding loop |

### Service tasks

**Pros**
- Each unit of work is an independently deployable/scalable worker process, polling for its job type
- Retries, timeouts, and backoff are engine-managed, not hand-rolled per participant
- The process diagram itself documents which steps exist and in what order

**Cons**
- Requires running a separate broker/engine — more moving parts than a single Java method call
- Debugging spans the process engine and the worker process, rather than a single stack trace

**Typical use cases**
- Any multi-step business process where each step could reasonably be its own service or team's responsibility
- Long-running processes that must survive a restart of the participating services

### Exclusive gateway

**Pros**
- Branching logic lives in the diagram, visible to non-developers reviewing the process
- FEEL expressions on sequence flows are simple, readable conditions — no hidden Java `if`/`else` to hunt for
- A `default` flow makes the "otherwise" case explicit and impossible to accidentally omit

**Cons**
- Complex branching logic (many conditions, nested decisions) can make a diagram harder to read than well-organized code
- Expression syntax (FEEL) is an additional thing to learn beyond the host language

**Typical use cases**
- Any point where the process should take a different path based on data already in its variables (order value, customer tier, risk score)

### User task

**Pros**
- First-class human-in-the-loop step — the process instance genuinely waits, with no polling loop to write
- Assignable to users/groups, queryable ("what's pending for me?"), and completable via a documented API
- Same instance keeps all the context (variables) gathered so far, available to whoever completes the task

**Cons**
- Introduces real wall-clock waiting into the process — needs monitoring for stuck/forgotten tasks
- Requires *some* task-consuming client (a real UI, or in this demo, a REST call) — the engine alone doesn't notify a human

**Typical use cases**
- Approvals, reviews, manual exception handling — anywhere a human decision gates progress

### Error boundary event

**Pros**
- Declarative failure routing: attach a boundary event to a task, point it at a cleanup path, done — no manual unwinding loop
- Different tasks can route to different (or shared) cleanup paths just by connecting the boundary event's outgoing flow
- The diagram shows the failure path as clearly as the happy path

**Cons**
- Only as good as the error codes the workers actually throw — a worker that throws a generic/unclassified exception produces an unhandled incident, not a routed error
- Complex compensation (undoing several already-completed steps in reverse order) needs either several boundary events wired carefully, or full BPMN Compensation Events (not used in this demo — see Scope)

**Typical use cases**
- Any step whose failure should trigger cleanup/rollback of what came before it, rather than just failing the whole process

## Running the demo

Requires Docker (Camunda 8 self-managed + Elasticsearch).

```bash
cd workflow-engines/camunda
docker compose -f docker/docker-compose.yml up -d
```

Wait ~40 seconds, then verify it's healthy (this unified image has no `/actuator/health` endpoint — `/v2/topology` is the equivalent readiness signal):

```bash
curl -s http://localhost:8080/v2/topology
```

Operate (visual process-instance monitoring — watch instances move through the diagram live as you exercise the API below) is at [http://localhost:8080/operate](http://localhost:8080/operate); Tasklist (browse/complete the `Approve Order` user task by hand instead of via `curl`) at [http://localhost:8080/tasklist](http://localhost:8080/tasklist).

```bash
cd spring-demo
mvn spring-boot:run
```

See [spring-demo/README.md](spring-demo/README.md) for `curl` walkthroughs of all four patterns.

## Scope

- Camunda 8 self-managed only, unauthenticated (`CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=true`) — no Camunda 8 SaaS, no Identity/OIDC, matching this repo's "local demo, not production hardening" scope of every other module.
- Error boundary events only, not full BPMN Compensation Events (throw-compensation + `isForCompensation` associations) — this demo's cleanup path is a single shared "release inventory" service task reached via boundary events and a gateway branch, not a formal compensation subprocess.
- No Connectors, no DMN decision tables, no multi-instance/parallel gateways — one linear happy path plus the two branches described above, to keep the diagram legible.
- In-memory read model in the Spring app (`OrderReadModel`) for the order-status endpoint — no separate persistence layer.
- `mvn test` requires a working Docker daemon (Camunda's official Testcontainers-backed test library pulls and runs a real Camunda runtime per test class) — the one module in this repo where that's true.

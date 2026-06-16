---
name: Code Review Rules
description: Review rules for all modules in this repository. Auto-loaded during code review of any Java or configuration file.
globs: ["message-brokers/**/src/**/*.java", "backend/**/src/**/*.java", "frontend/**/*.ts"]
alwaysApply: false
featureAreas: ["code-review", "quality"]
invokeFor: ["code review", "spec review", "quality review", "review implementation", "review test"]
---

# Code Review Rules

## FailureSimulator consistency

All `FailureSimulator` utility classes across `message-brokers/` modules must follow the pattern established in the Kafka module (`message-brokers/kafka/spring-demo/src/main/java/com/testingai/kafka/util/FailureSimulator.java`):

- Use a named constant `FAILURE_RATE = 0.05` (5%)
- Expose `maybeThrow(String context)` that throws `RuntimeException` on failure
- Do **not** use a `shouldFail()` method returning `boolean`

## Modern Java feature preference (Java 17 / 21 LTS)

All modules target Java 21. Flag code that reaches for a pre-modern idiom when a cleaner language feature exists:

| Prefer | Instead of |
|---|---|
| `record` | boilerplate POJO with fields, constructor, `equals`/`hashCode`/`toString` |
| Sealed classes + `permits` | open class hierarchies where the set of subtypes is fixed |
| Pattern matching `instanceof` (`if (x instanceof Foo f)`) | explicit cast after `instanceof` check |
| Switch expressions (`yield`) | multi-line `switch` statements with `break` |
| Pattern matching in `switch` (Java 21) | chains of `if/else instanceof` |
| Record patterns in `switch` (Java 21) | manual field extraction after a pattern match |
| Text blocks (`"""`) | string concatenation for multi-line literals (JSON, SQL, logs) |
| `SequencedCollection` API — `getFirst()` / `getLast()` (Java 21) | `list.get(0)` / `list.get(list.size() - 1)` |
| Virtual threads (`Thread.ofVirtual()` / `Executors.newVirtualThreadPerTaskExecutor()`) | manually managed thread pools for I/O-bound work |

Flag only when the older idiom is actually used in modified code — do not flag pre-existing code that was not touched by the PR.

## Unnecessary `toString()` calls

Do not call `.toString()` explicitly when the value is passed to an SLF4J logger placeholder (`{}`), string concatenation, or any context where `toString()` is called implicitly. Flag any of these patterns:

```java
// bad
log.info("received: {}", someObject.toString());
log.warn("value={}", ctx.getMessage().getBody().toString());

// good
log.info("received: {}", someObject);
log.warn("value={}", ctx.getMessage().getBody());
```

Exception: `.toString()` is necessary when the result must be typed as `String` at compile time (e.g. passed to `assertThat(...).isEqualTo(String)` in tests, or assigned to a `String` variable).

## `AutoCloseable` resources in long-running Spring components

`ServiceBusProcessorClient` (and any other `AutoCloseable`) must not be left as a bare local variable in `ApplicationRunner.run()` — the method returns immediately, so `try`-with-resources would close the client before it can process anything.

The correct pattern for long-running processors in a Spring component:

```java
// store as field, close on context shutdown
private ServiceBusProcessorClient processorClient;

@Override
public void run(ApplicationArguments args) {
    processorClient = clientBuilder.processor()...buildProcessorClient();
    processorClient.start();
}

@PreDestroy
public void close() {
    processorClient.close();
}
```

- Use `@PreDestroy` (from `jakarta.annotation`) to close the client when the Spring context shuts down.
- If a single component owns multiple processor clients, declare a separate field for each and close all of them in `@PreDestroy`.
- Do **not** declare the client as a local variable inside `run()` without immediately wrapping it in `try`-with-resources.

## Field modifiers — `private final`

All instance fields that are assigned once (at declaration or in the constructor) and never reassigned must be declared `private final`. This applies everywhere, including Gatling `Simulation` subclasses where `HttpProtocolBuilder` and `ScenarioBuilder` fields are commonly left package-private and non-final by mistake:

```java
// bad
HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8080");
ScenarioBuilder simpleScenario = scenario("Simple").exec(...);

// good
private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8080");
private final ScenarioBuilder simpleScenario = scenario("Simple").exec(...);
```

Fields that are assigned in `@PostConstruct` / `ApplicationRunner.run()` / `@PreDestroy` lifecycle methods (like processor clients) are exempt from `final` but must still be `private`.

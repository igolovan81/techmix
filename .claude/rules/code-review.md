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

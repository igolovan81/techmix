# Survey Monkey Import Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new `data-integration/survey-monkey-import` pair of Spring Boot apps — `source-demo` (a fake SurveyMonkey) and `importer-demo` (the reliable ingestion pipeline) — demonstrating pagination, idempotency, rate limiting, retries, monitoring, and dead-letter queues.

**Architecture:** `source-demo` seeds in-memory survey data, exposes a paginated responses API mimicking SurveyMonkey's real shape, injects failures on demand, and dispatches HMAC-signed webhooks. `importer-demo` runs a scheduled + webhook-triggered connector that fetches one page (or one single response) per queued job, wrapped in Resilience4j retry/circuit-breaker/rate-limiter, idempotently upserts into H2 via native-SQL update-then-insert, redelivers failed jobs with backoff via an in-process `DelayQueue`, dead-letters exhausted/permanent failures with a redrive endpoint, and exposes Micrometer/Actuator metrics plus a human-readable status endpoint.

**Tech Stack:** Spring Boot 3.4.4, Java 21, Spring Data JPA + H2, Resilience4j (`resilience4j-spring-boot3` + `spring-boot-starter-aop`), Micrometer + `micrometer-registry-prometheus`, JUnit 5 + MockMvc + Mockito + AssertJ, Gatling (load test only).

**Spec:** `docs/superpowers/specs/2026-08-26-survey-monkey-import-demo-design.md`

## Global Constraints

- Java 21 (`maven.compiler.release`), Spring Boot 3.4.4 — matches every other module in this repo.
- No external infrastructure (no Docker, no real Kafka) — the job queue is in-process (`DelayQueue`), storage is H2.
- Both apps configure `spring.jackson.property-naming-strategy: SNAKE_CASE` — all JSON wire payloads use snake_case field names (`date_modified`, `survey_id`, etc.), mimicking SurveyMonkey's real API and the webhook payload shape; Java record/entity fields stay camelCase. Query parameters are **not** covered by this and must be explicitly named (`@RequestParam(name = "per_page", ...)`).
- `FailureInjector` (source-demo) is deliberately **not** shaped like the `.claude/rules/code-review.md` `FailureSimulator` convention (`FAILURE_RATE = 0.05` / `maybeThrow(context)`) — that convention is for `message-brokers/` modules simulating a fixed background failure rate. This demo needs deterministic, on-demand, mode-selectable failure injection for a live walkthrough (mirroring `communication-protocols/webhooks`' consumer-demo convention instead), so it takes a configurable `FailureMode` + `rate` set via an admin endpoint.
- Long-running resources (`SyncWorkerPool`'s `ExecutorService`) must be started in `@PostConstruct` and shut down in `@PreDestroy` — never left as a bare local (`.claude/rules/code-review.md`).
- All instance fields assigned once and never reassigned must be `private final`; fields assigned in `@PostConstruct`/`@PreDestroy` are exempt from `final` but must still be `private` (`.claude/rules/code-review.md`).
- Do not call `.toString()` explicitly when a value goes to an SLF4J `{}` placeholder or string concatenation (`.claude/rules/code-review.md`).
- Do not declare `throws InterruptedException` where the framework/loop controls the call — catch and restore the interrupt flag instead (`.claude/rules/code-review.md`).
- Prefer modern Java 21 idioms (records, pattern matching, switch expressions, `HexFormat`, text blocks) over pre-modern equivalents on any line you write (`.claude/rules/code-review.md`).
- Tests use plain JUnit 5 — `@WebMvcTest`/`@MockitoBean` (not the deprecated `@MockBean`) for web-layer tests, `@DataJpaTest` for repository/persistence tests, plain Mockito (`@ExtendWith(MockitoExtension.class)` or `Mockito.mock(...)`) for isolated unit tests — no Spock/Groovy.
- Ports: `source-demo` → `8101`, `importer-demo` → `8102`.
- Known survey IDs, shared literally between both apps (seed data in `source-demo`, config in `importer-demo`): `survey-1`, `survey-2`, `survey-3`.
- Shared HMAC webhook secret is a fixed demo value configured identically in both apps' `application.yml` — clearly not a production credential.
- Package roots: `com.testingai.surveysource` (source-demo), `com.testingai.surveyimporter` (importer-demo).

---

## Task 1: Reactor scaffold (both apps)

**Files:**
- Create: `data-integration/pom.xml`
- Create: `data-integration/eclipse-formatter.xml`
- Create: `data-integration/survey-monkey-import/source-demo/pom.xml`
- Create: `data-integration/survey-monkey-import/source-demo/src/main/resources/application.yml`
- Create: `data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/SurveySourceApplication.java`
- Test: `data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/SurveySourceApplicationTest.java`
- Create: `data-integration/survey-monkey-import/importer-demo/pom.xml`
- Create: `data-integration/survey-monkey-import/importer-demo/src/main/resources/application.yml`
- Create: `data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/SurveyImporterApplication.java`
- Test: `data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/SurveyImporterApplicationTest.java`
- Modify: `.githooks/pre-commit`

**Interfaces:**
- Produces: two buildable, empty Spring Boot apps on ports `8101`/`8102`, with all dependencies (JPA, H2, Actuator, Micrometer Prometheus, Resilience4j, AOP) already declared so later tasks only add code, plus a wired-up Spotless pre-commit hook for `data-integration/**/*.java`.

- [ ] **Step 1: Create the parent POM**

Create `data-integration/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.4</version>
    </parent>

    <groupId>com.testingai</groupId>
    <artifactId>data-integration</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Data Integration</name>
    <description>Parent POM for all data-integration demo modules</description>

    <modules>
        <module>survey-monkey-import/source-demo</module>
        <module>survey-monkey-import/importer-demo</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <lombok.version>1.18.38</lombok.version>
        <springdoc.version>2.8.6</springdoc.version>
        <gatling.version>3.13.1</gatling.version>
        <gatling-maven-plugin.version>4.15.0</gatling-maven-plugin.version>
        <spotless.version>2.43.0</spotless.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.gatling.highcharts</groupId>
            <artifactId>gatling-charts-highcharts</artifactId>
            <version>${gatling.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-Dnet.bytebuddy.experimental=true</argLine>
                    <excludes>
                        <exclude>**/performance/**</exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>io.gatling</groupId>
                <artifactId>gatling-maven-plugin</artifactId>
                <version>${gatling-maven-plugin.version}</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>install-git-hooks</id>
                        <phase>initialize</phase>
                        <goals>
                            <goal>exec</goal>
                        </goals>
                        <configuration>
                            <executable>git</executable>
                            <arguments>
                                <argument>config</argument>
                                <argument>core.hooksPath</argument>
                                <argument>.githooks</argument>
                            </arguments>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>${spotless.version}</version>
                <configuration>
                    <java>
                        <eclipse>
                            <version>4.31</version>
                            <file>${maven.multiModuleProjectDirectory}/eclipse-formatter.xml</file>
                        </eclipse>
                    </java>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Copy the Eclipse formatter config**

```bash
cp concurrency-patterns/eclipse-formatter.xml data-integration/eclipse-formatter.xml
```

- [ ] **Step 3: Create `source-demo`'s POM**

Create `data-integration/survey-monkey-import/source-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>data-integration</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>survey-source-demo</artifactId>
    <name>Survey Source Demo</name>
    <description>Fake SurveyMonkey API — paginated responses, on-demand failure injection, webhook dispatch</description>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.surveysource.SurveySourceApplication</mainClass>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create `source-demo`'s `application.yml`**

Create `data-integration/survey-monkey-import/source-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8101

spring:
  jackson:
    property-naming-strategy: SNAKE_CASE

importer:
  webhook-url: http://localhost:8102/webhooks/surveymonkey
  webhook-secret: demo-shared-secret-please-rotate
```

- [ ] **Step 5: Create `source-demo`'s application class**

Create `data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/SurveySourceApplication.java`:

```java
package com.testingai.surveysource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SurveySourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurveySourceApplication.class, args);
    }
}
```

- [ ] **Step 6: Write `source-demo`'s context-loads test**

Create `data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/SurveySourceApplicationTest.java`:

```java
package com.testingai.surveysource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SurveySourceApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 7: Create `importer-demo`'s POM**

Create `data-integration/survey-monkey-import/importer-demo/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.testingai</groupId>
        <artifactId>data-integration</artifactId>
        <version>1.0.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>survey-importer-demo</artifactId>
    <name>Survey Importer Demo</name>
    <description>Reliable SurveyMonkey ingestion pipeline — pagination, idempotency, rate limiting, retries, monitoring, DLQ</description>

    <properties>
        <resilience4j.version>2.2.0</resilience4j.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.testingai.surveyimporter.SurveyImporterApplication</mainClass>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>io.gatling</groupId>
                <artifactId>gatling-maven-plugin</artifactId>
                <configuration>
                    <simulationClass>com.testingai.surveyimporter.performance.DemoSimulation</simulationClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 8: Create `importer-demo`'s `application.yml`**

Create `data-integration/survey-monkey-import/importer-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8102

spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
  datasource:
    url: jdbc:h2:mem:survey_importer;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: update

surveymonkey:
  base-url: http://localhost:8101
  webhook-secret: demo-shared-secret-please-rotate

importer:
  known-survey-ids: survey-1,survey-2,survey-3
  scheduler:
    fixed-delay-ms: 60000

resilience4j:
  retry:
    instances:
      surveyMonkey:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
        randomized-wait-factor: 0.5
  circuitbreaker:
    instances:
      surveyMonkey:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 3
  ratelimiter:
    instances:
      surveyMonkey:
        limit-for-period: 10
        limit-refresh-period: 5s
        timeout-duration: 2s

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

Note: the `retry-exceptions` list under `resilience4j.retry.instances.surveyMonkey` is deliberately **not** included yet — Resilience4j resolves that class reference eagerly at context startup (`Class.forName`), and `RetryableSyncException` doesn't exist until Task 9. Adding it now would fail every context-loads test between here and Task 9. Task 9 adds it back once the class exists.

- [ ] **Step 9: Create `importer-demo`'s application class**

Create `data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/SurveyImporterApplication.java`:

```java
package com.testingai.surveyimporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SurveyImporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurveyImporterApplication.class, args);
    }
}
```

- [ ] **Step 10: Write `importer-demo`'s context-loads test**

Create `data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/SurveyImporterApplicationTest.java`:

```java
package com.testingai.surveyimporter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SurveyImporterApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 11: Build and test both apps**

```bash
cd data-integration/survey-monkey-import/source-demo
mvn clean test
cd ../importer-demo
mvn clean test
```

Expected: `BUILD SUCCESS` for both, one `contextLoads` test each.

- [ ] **Step 12: Wire up the pre-commit Spotless hook**

Modify `.githooks/pre-commit` — extend the staged-file grep to include `data-integration`:

```bash
# old
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols|reactive-programming|workflow-engines|domain-driven-design|concurrency-patterns)/.*\.java$' || true)
```
```bash
# new
STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACM | grep -E '^(message-brokers|noSQL|cqrs-event-sourcing|template-engines|distributed-transactions|spring-boot-starters|communication-protocols|reactive-programming|workflow-engines|domain-driven-design|concurrency-patterns|data-integration)/.*\.java$' || true)
```

And add a matching block after the `concurrency-patterns` block (before the "Re-stage" comment):

```bash
if echo "$STAGED_JAVA" | grep -q '^data-integration/'; then
    echo "[pre-commit] Applying Spotless formatting to staged data-integration Java files..."
    (cd "$ROOT/data-integration" && mvn spotless:apply --quiet)
fi
```

- [ ] **Step 13: Commit**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git add data-integration .githooks/pre-commit
git commit -m "feat(data-integration): scaffold survey-source-demo and survey-importer-demo"
```

---

## Task 2: source-demo domain model

**Files:**
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/domain/Answer.java`
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/domain/SourceSurveyResponse.java`
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/domain/Links.java`
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/domain/ResponsesPage.java`
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/domain/FailureMode.java`
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/domain/FailureConfig.java`
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/webhook/WebhookEvent.java`
- Test: `.../source-demo/src/test/java/com/testingai/surveysource/domain/SourceSurveyResponseJsonTest.java`

(`.../source-demo` = `data-integration/survey-monkey-import/source-demo`, used as shorthand for the rest of this plan.)

**Interfaces:**
- Consumes: nothing (base domain layer).
- Produces: `record Answer(String questionId, String text)`; `record SourceSurveyResponse(String id, String surveyId, Instant dateModified, List<Answer> answers)`; `record Links(String next)`; `record ResponsesPage(List<SourceSurveyResponse> data, int page, int perPage, int total, Links links)`; `enum FailureMode { NONE, RATE_LIMIT, SERVER_ERROR, MALFORMED }`; `record FailureConfig(FailureMode mode, double rate)`; `record WebhookEvent(String surveyId, String responseId, String eventType)`. All later source-demo tasks build on these.

- [ ] **Step 1: Write the failing JSON-shape test**

Create `.../source-demo/src/test/java/com/testingai/surveysource/domain/SourceSurveyResponseJsonTest.java`:

```java
package com.testingai.surveysource.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceSurveyResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void serializesDateModifiedAsSnakeCase() throws Exception {
        SourceSurveyResponse response = new SourceSurveyResponse("resp-1", "survey-1", Instant.parse("2026-01-01T00:00:00Z"),
                List.of(new Answer("q1", "yes")));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"date_modified\"").contains("\"survey_id\"").contains("\"question_id\"");
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/source-demo
mvn test -Dtest=SourceSurveyResponseJsonTest
```

Expected: compile error — `SourceSurveyResponse`/`Answer` do not exist yet.

- [ ] **Step 3: Implement the domain records**

Create `.../source-demo/src/main/java/com/testingai/surveysource/domain/Answer.java`:

```java
package com.testingai.surveysource.domain;

public record Answer(String questionId, String text) {
}
```

Create `.../source-demo/src/main/java/com/testingai/surveysource/domain/SourceSurveyResponse.java`:

```java
package com.testingai.surveysource.domain;

import java.time.Instant;
import java.util.List;

public record SourceSurveyResponse(String id, String surveyId, Instant dateModified, List<Answer> answers) {
}
```

Create `.../source-demo/src/main/java/com/testingai/surveysource/domain/Links.java`:

```java
package com.testingai.surveysource.domain;

public record Links(String next) {
}
```

Create `.../source-demo/src/main/java/com/testingai/surveysource/domain/ResponsesPage.java`:

```java
package com.testingai.surveysource.domain;

import java.util.List;

public record ResponsesPage(List<SourceSurveyResponse> data, int page, int perPage, int total, Links links) {
}
```

Create `.../source-demo/src/main/java/com/testingai/surveysource/domain/FailureMode.java`:

```java
package com.testingai.surveysource.domain;

public enum FailureMode {
    NONE,
    RATE_LIMIT,
    SERVER_ERROR,
    MALFORMED
}
```

Create `.../source-demo/src/main/java/com/testingai/surveysource/domain/FailureConfig.java`:

```java
package com.testingai.surveysource.domain;

public record FailureConfig(FailureMode mode, double rate) {
}
```

Create `.../source-demo/src/main/java/com/testingai/surveysource/webhook/WebhookEvent.java`:

```java
package com.testingai.surveysource.webhook;

public record WebhookEvent(String surveyId, String responseId, String eventType) {
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SourceSurveyResponseJsonTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/domain \
        data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/webhook \
        data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/domain
git commit -m "feat(survey-source-demo): add domain model"
```

---

## Task 3: source-demo seed data

**Files:**
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/seed/SeedDataService.java`
- Test: `.../source-demo/src/test/java/com/testingai/surveysource/seed/SeedDataServiceTest.java`

**Interfaces:**
- Consumes: `SourceSurveyResponse`, `Answer` (Task 2).
- Produces: `class SeedDataService` — `void seed()` (`@PostConstruct`); `List<String> surveyIds()`; `List<SourceSurveyResponse> responsesFor(String surveyId, Instant startModifiedAt)` (`startModifiedAt` nullable — null means no filter); `Optional<SourceSurveyResponse> findResponse(String surveyId, String responseId)`. Consumed by `ResponsesController` (Task 5).

- [ ] **Step 1: Write the failing test**

Create `.../source-demo/src/test/java/com/testingai/surveysource/seed/SeedDataServiceTest.java`:

```java
package com.testingai.surveysource.seed;

import com.testingai.surveysource.domain.SourceSurveyResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataServiceTest {

    private final SeedDataService seedDataService = new SeedDataService();

    @BeforeEach
    void setUp() {
        seedDataService.seed();
    }

    @Test
    void seedsExpectedSurveysAndResponseCounts() {
        assertThat(seedDataService.surveyIds()).containsExactly("survey-1", "survey-2", "survey-3");
        assertThat(seedDataService.responsesFor("survey-1", null)).hasSize(250);
    }

    @Test
    void filtersByStartModifiedAt() {
        List<SourceSurveyResponse> all = seedDataService.responsesFor("survey-1", null);
        Instant midpoint = all.get(125).dateModified();

        List<SourceSurveyResponse> recent = seedDataService.responsesFor("survey-1", midpoint);

        assertThat(recent).allMatch(r -> r.dateModified().isAfter(midpoint));
        assertThat(recent.size()).isLessThan(all.size());
    }

    @Test
    void findResponseLocatesByIdWithinASurvey() {
        Optional<SourceSurveyResponse> found = seedDataService.findResponse("survey-1", "survey-1-response-0");

        assertThat(found).isPresent();
        assertThat(seedDataService.findResponse("survey-1", "does-not-exist")).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=SeedDataServiceTest
```

Expected: compile error — `SeedDataService` does not exist yet.

- [ ] **Step 3: Implement `SeedDataService`**

Create `.../source-demo/src/main/java/com/testingai/surveysource/seed/SeedDataService.java`:

```java
package com.testingai.surveysource.seed;

import com.testingai.surveysource.domain.Answer;
import com.testingai.surveysource.domain.SourceSurveyResponse;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SeedDataService {

    private static final List<String> SURVEY_IDS = List.of("survey-1", "survey-2", "survey-3");
    private static final int RESPONSES_PER_SURVEY = 250;

    private final Map<String, List<SourceSurveyResponse>> responsesBySurvey = new ConcurrentHashMap<>();

    @PostConstruct
    public void seed() {
        Instant now = Instant.now();
        for (String surveyId : SURVEY_IDS) {
            List<SourceSurveyResponse> responses = new ArrayList<>();
            for (int i = 0; i < RESPONSES_PER_SURVEY; i++) {
                String responseId = surveyId + "-response-" + i;
                Instant dateModified = now.minus(Duration.ofHours(3L * (RESPONSES_PER_SURVEY - i)));
                List<Answer> answers = List.of(new Answer("q1", "answer-" + i));
                responses.add(new SourceSurveyResponse(responseId, surveyId, dateModified, answers));
            }
            responsesBySurvey.put(surveyId, responses);
        }
    }

    public List<String> surveyIds() {
        return SURVEY_IDS;
    }

    public List<SourceSurveyResponse> responsesFor(String surveyId, Instant startModifiedAt) {
        List<SourceSurveyResponse> all = responsesBySurvey.getOrDefault(surveyId, List.of());
        if (startModifiedAt == null) {
            return all;
        }
        return all.stream().filter(response -> response.dateModified().isAfter(startModifiedAt)).toList();
    }

    public Optional<SourceSurveyResponse> findResponse(String surveyId, String responseId) {
        return responsesBySurvey.getOrDefault(surveyId, List.of()).stream()
                .filter(response -> response.id().equals(responseId)).findFirst();
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SeedDataServiceTest
```

Expected: `BUILD SUCCESS`, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/seed \
        data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/seed
git commit -m "feat(survey-source-demo): add seed data service"
```

---

## Task 4: source-demo failure injection

**Files:**
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/failure/FailureInjector.java`
- Test: `.../source-demo/src/test/java/com/testingai/surveysource/failure/FailureInjectorTest.java`

**Interfaces:**
- Consumes: `FailureMode`, `FailureConfig` (Task 2).
- Produces: `class FailureInjector` — `void configure(FailureConfig config)`; `FailureConfig current()`; `boolean shouldInject(FailureMode mode)`. Consumed by `ResponsesController` (Task 5) and `AdminController` (Task 6).

- [ ] **Step 1: Write the failing test**

Create `.../source-demo/src/test/java/com/testingai/surveysource/failure/FailureInjectorTest.java`:

```java
package com.testingai.surveysource.failure;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.domain.FailureMode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureInjectorTest {

    private final FailureInjector failureInjector = new FailureInjector();

    @Test
    void defaultsToNoInjection() {
        assertThat(failureInjector.shouldInject(FailureMode.RATE_LIMIT)).isFalse();
        assertThat(failureInjector.current()).isEqualTo(new FailureConfig(FailureMode.NONE, 0.0));
    }

    @Test
    void injectsOnlyForTheConfiguredModeAtTheConfiguredRate() {
        failureInjector.configure(new FailureConfig(FailureMode.SERVER_ERROR, 1.0));

        assertThat(failureInjector.shouldInject(FailureMode.SERVER_ERROR)).isTrue();
        assertThat(failureInjector.shouldInject(FailureMode.RATE_LIMIT)).isFalse();
        assertThat(failureInjector.current()).isEqualTo(new FailureConfig(FailureMode.SERVER_ERROR, 1.0));
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=FailureInjectorTest
```

Expected: compile error — `FailureInjector` does not exist yet.

- [ ] **Step 3: Implement `FailureInjector`**

Create `.../source-demo/src/main/java/com/testingai/surveysource/failure/FailureInjector.java`:

```java
package com.testingai.surveysource.failure;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.domain.FailureMode;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class FailureInjector {

    private final AtomicReference<FailureConfig> config = new AtomicReference<>(
            new FailureConfig(FailureMode.NONE, 0.0));

    public void configure(FailureConfig newConfig) {
        config.set(newConfig);
    }

    public FailureConfig current() {
        return config.get();
    }

    public boolean shouldInject(FailureMode mode) {
        FailureConfig active = config.get();
        return active.mode() == mode && Math.random() < active.rate();
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=FailureInjectorTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/failure \
        data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/failure
git commit -m "feat(survey-source-demo): add on-demand failure injection"
```

---

## Task 5: source-demo responses controller

**Files:**
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/controller/ResponsesController.java`
- Test: `.../source-demo/src/test/java/com/testingai/surveysource/controller/ResponsesControllerTest.java`

**Interfaces:**
- Consumes: `SeedDataService` (Task 3), `FailureInjector` (Task 4), `ResponsesPage`/`Links`/`FailureMode` (Task 2).
- Produces: `GET /v3/surveys/{surveyId}/responses/bulk?page=&per_page=&start_modified_at=` and `GET /v3/surveys/{surveyId}/responses/{responseId}`. Consumed by `importer-demo`'s `SurveyMonkeyClient` (Task 9) and this task's own test.

- [ ] **Step 1: Write the failing test**

Create `.../source-demo/src/test/java/com/testingai/surveysource/controller/ResponsesControllerTest.java`:

```java
package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureMode;
import com.testingai.surveysource.domain.SourceSurveyResponse;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.seed.SeedDataService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResponsesController.class)
class ResponsesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeedDataService seedDataService;

    @MockitoBean
    private FailureInjector failureInjector;

    @Test
    void bulkReturnsPagedResponsesWithNextLink() throws Exception {
        List<SourceSurveyResponse> responses = IntStream.range(0, 30)
                .mapToObj(i -> new SourceSurveyResponse("resp-" + i, "survey-1", Instant.now(), List.of())).toList();
        when(seedDataService.responsesFor(eq("survey-1"), isNull())).thenReturn(responses);
        when(failureInjector.shouldInject(any())).thenReturn(false);

        mockMvc.perform(get("/v3/surveys/survey-1/responses/bulk").param("page", "1").param("per_page", "25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(25))
                .andExpect(jsonPath("$.links.next").value("2"));
    }

    @Test
    void lastPageHasNoNextLink() throws Exception {
        List<SourceSurveyResponse> responses = IntStream.range(0, 30)
                .mapToObj(i -> new SourceSurveyResponse("resp-" + i, "survey-1", Instant.now(), List.of())).toList();
        when(seedDataService.responsesFor(eq("survey-1"), isNull())).thenReturn(responses);
        when(failureInjector.shouldInject(any())).thenReturn(false);

        mockMvc.perform(get("/v3/surveys/survey-1/responses/bulk").param("page", "2").param("per_page", "25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.links.next").doesNotExist());
    }

    @Test
    void rateLimitFailureModeReturns429WithRetryAfterHeader() throws Exception {
        when(failureInjector.shouldInject(FailureMode.RATE_LIMIT)).thenReturn(true);

        mockMvc.perform(get("/v3/surveys/survey-1/responses/bulk")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"));
    }

    @Test
    void singleResponseReturnsNotFoundWhenAbsent() throws Exception {
        when(seedDataService.findResponse("survey-1", "missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v3/surveys/survey-1/responses/missing")).andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
mvn test -Dtest=ResponsesControllerTest
```

Expected: compile error — `ResponsesController` does not exist yet.

- [ ] **Step 3: Implement `ResponsesController`**

Create `.../source-demo/src/main/java/com/testingai/surveysource/controller/ResponsesController.java`:

```java
package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureMode;
import com.testingai.surveysource.domain.Links;
import com.testingai.surveysource.domain.ResponsesPage;
import com.testingai.surveysource.domain.SourceSurveyResponse;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.seed.SeedDataService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v3/surveys/{surveyId}/responses")
public class ResponsesController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SeedDataService seedDataService;
    private final FailureInjector failureInjector;

    public ResponsesController(SeedDataService seedDataService, FailureInjector failureInjector) {
        this.seedDataService = seedDataService;
        this.failureInjector = failureInjector;
    }

    @GetMapping("/bulk")
    public ResponseEntity<Object> bulk(@PathVariable String surveyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "25") int perPage,
            @RequestParam(name = "start_modified_at", required = false) String startModifiedAt) {

        if (failureInjector.shouldInject(FailureMode.RATE_LIMIT)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "2").build();
        }
        if (failureInjector.shouldInject(FailureMode.SERVER_ERROR)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        int size = Math.min(perPage, MAX_PAGE_SIZE);
        List<SourceSurveyResponse> all = seedDataService.responsesFor(surveyId, parseInstant(startModifiedAt));
        int total = all.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<SourceSurveyResponse> pageData = new ArrayList<>(all.subList(fromIndex, toIndex));

        if (failureInjector.shouldInject(FailureMode.MALFORMED) && !pageData.isEmpty()) {
            SourceSurveyResponse original = pageData.get(0);
            pageData.set(0,
                    new SourceSurveyResponse(null, original.surveyId(), original.dateModified(), original.answers()));
        }

        boolean hasNext = toIndex < total;
        Links links = new Links(hasNext ? String.valueOf(page + 1) : null);
        return ResponseEntity.ok(new ResponsesPage(pageData, page, size, total, links));
    }

    @GetMapping("/{responseId}")
    public ResponseEntity<SourceSurveyResponse> single(@PathVariable String surveyId, @PathVariable String responseId) {
        if (failureInjector.shouldInject(FailureMode.SERVER_ERROR)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return seedDataService.findResponse(surveyId, responseId).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=ResponsesControllerTest
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/controller \
        data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/controller
git commit -m "feat(survey-source-demo): add paginated responses controller"
```

---

## Task 6: source-demo admin controller and webhook dispatch

**Files:**
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/webhook/WebhookDispatcher.java`
- Create: `.../source-demo/src/main/java/com/testingai/surveysource/controller/AdminController.java`
- Test: `.../source-demo/src/test/java/com/testingai/surveysource/webhook/WebhookDispatcherTest.java`
- Test: `.../source-demo/src/test/java/com/testingai/surveysource/controller/AdminControllerTest.java`

**Interfaces:**
- Consumes: `FailureInjector` (Task 4), `FailureConfig` (Task 2), `WebhookEvent` (Task 2).
- Produces: `class WebhookDispatcher` — `void dispatch(String surveyId, String responseId)`; `static String hmacSha256Hex(String data, String secret)` (package-visible, used by the importer's independent HMAC implementation only as a reference — not shared code). `POST /admin/failure-mode`, `GET /admin/failure-mode`, `POST /admin/webhooks/trigger?surveyId=&responseId=`.

- [ ] **Step 1: Write the failing `WebhookDispatcher` HMAC test**

Create `.../source-demo/src/test/java/com/testingai/surveysource/webhook/WebhookDispatcherTest.java`:

```java
package com.testingai.surveysource.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDispatcherTest {

    @Test
    void hmacSha256HexIsDeterministicAndHexEncoded() {
        String hex1 = WebhookDispatcher.hmacSha256Hex("payload", "secret");
        String hex2 = WebhookDispatcher.hmacSha256Hex("payload", "secret");

        assertThat(hex1).isEqualTo(hex2).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void differentPayloadsProduceDifferentSignatures() {
        String hexA = WebhookDispatcher.hmacSha256Hex("payload-a", "secret");
        String hexB = WebhookDispatcher.hmacSha256Hex("payload-b", "secret");

        assertThat(hexA).isNotEqualTo(hexB);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/source-demo
mvn test -Dtest=WebhookDispatcherTest
```

Expected: compile error — `WebhookDispatcher` does not exist yet.

- [ ] **Step 3: Implement `WebhookDispatcher`**

Create `.../source-demo/src/main/java/com/testingai/surveysource/webhook/WebhookDispatcher.java`:

```java
package com.testingai.surveysource.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class WebhookDispatcher {

    private final RestClient restClient;
    private final String webhookUrl;
    private final String secret;
    private final ObjectMapper objectMapper;

    public WebhookDispatcher(RestClient.Builder builder, @Value("${importer.webhook-url}") String webhookUrl,
            @Value("${importer.webhook-secret}") String secret, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
        this.secret = secret;
        this.objectMapper = objectMapper;
    }

    public void dispatch(String surveyId, String responseId) {
        String body = writeValue(new WebhookEvent(surveyId, responseId, "response_completed"));
        String signature = "sha256=" + hmacSha256Hex(body, secret);
        restClient.post().uri(webhookUrl).header("X-SurveyMonkey-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }

    private String writeValue(WebhookEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize webhook event", e);
        }
    }

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=WebhookDispatcherTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Write the failing `AdminController` test**

Create `.../source-demo/src/test/java/com/testingai/surveysource/controller/AdminControllerTest.java`:

```java
package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.domain.FailureMode;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.webhook.WebhookDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FailureInjector failureInjector;

    @MockitoBean
    private WebhookDispatcher webhookDispatcher;

    @Test
    void setFailureModeUpdatesInjector() throws Exception {
        mockMvc.perform(post("/admin/failure-mode").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"RATE_LIMIT\",\"rate\":0.5}")).andExpect(status().isOk());

        verify(failureInjector).configure(new FailureConfig(FailureMode.RATE_LIMIT, 0.5));
    }

    @Test
    void triggerWebhookDelegatesToDispatcher() throws Exception {
        mockMvc.perform(post("/admin/webhooks/trigger").param("surveyId", "survey-1").param("responseId", "resp-1"))
                .andExpect(status().isOk());

        verify(webhookDispatcher).dispatch("survey-1", "resp-1");
    }
}
```

- [ ] **Step 6: Run it to verify it fails to compile**

```bash
mvn test -Dtest=AdminControllerTest
```

Expected: compile error — `AdminController` does not exist yet.

- [ ] **Step 7: Implement `AdminController`**

Create `.../source-demo/src/main/java/com/testingai/surveysource/controller/AdminController.java`:

```java
package com.testingai.surveysource.controller;

import com.testingai.surveysource.domain.FailureConfig;
import com.testingai.surveysource.failure.FailureInjector;
import com.testingai.surveysource.webhook.WebhookDispatcher;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final FailureInjector failureInjector;
    private final WebhookDispatcher webhookDispatcher;

    public AdminController(FailureInjector failureInjector, WebhookDispatcher webhookDispatcher) {
        this.failureInjector = failureInjector;
        this.webhookDispatcher = webhookDispatcher;
    }

    @PostMapping("/failure-mode")
    public FailureConfig setFailureMode(@RequestBody FailureConfig config) {
        failureInjector.configure(config);
        return config;
    }

    @GetMapping("/failure-mode")
    public FailureConfig getFailureMode() {
        return failureInjector.current();
    }

    @PostMapping("/webhooks/trigger")
    public void triggerWebhook(@RequestParam String surveyId, @RequestParam String responseId) {
        webhookDispatcher.dispatch(surveyId, responseId);
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

```bash
mvn test -Dtest=AdminControllerTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 9: Run the full source-demo unit test suite**

```bash
mvn test
```

Expected: `BUILD SUCCESS`, all source-demo tests pass.

- [ ] **Step 10: Commit**

```bash
git add data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/webhook \
        data-integration/survey-monkey-import/source-demo/src/main/java/com/testingai/surveysource/controller/AdminController.java \
        data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/webhook \
        data-integration/survey-monkey-import/source-demo/src/test/java/com/testingai/surveysource/controller/AdminControllerTest.java
git commit -m "feat(survey-source-demo): add admin controller and webhook dispatch"
```

---

## Task 7: importer-demo domain model, entities, and repositories

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/domain/JobKind.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/domain/TriggerType.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/domain/SyncJob.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/entity/SurveyResponseEntity.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/entity/SyncWatermarkEntity.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/entity/DeadLetterJobEntity.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/SurveyResponseRepository.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/SyncWatermarkRepository.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/dlq/DeadLetterJobRepository.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/storage/SurveyResponseRepositoryTest.java`

(`.../importer-demo` = `data-integration/survey-monkey-import/importer-demo`, used as shorthand for the rest of this plan.)

**Interfaces:**
- Consumes: nothing (base layer for importer-demo).
- Produces: `enum JobKind { PAGE_SYNC, SINGLE_RESPONSE_SYNC }`; `enum TriggerType { SCHEDULED, WEBHOOK, MANUAL }`; `record SyncJob(UUID id, String surveyId, JobKind kind, String cursor, String responseId, TriggerType triggerType, int attemptCount, Instant nextAttemptAt)`; JPA entities `SurveyResponseEntity`, `SyncWatermarkEntity`, `DeadLetterJobEntity`; `interface SurveyResponseRepository extends JpaRepository<SurveyResponseEntity, Long>` with `Optional<SurveyResponseEntity> findBySurveyIdAndResponseId(String, String)`, `List<SurveyResponseEntity> findBySurveyId(String)`, `int updateIfNewer(String surveyId, String responseId, Instant dateModified, String payload, Instant importedAt)`; `interface SyncWatermarkRepository extends JpaRepository<SyncWatermarkEntity, String>`; `interface DeadLetterJobRepository extends JpaRepository<DeadLetterJobEntity, Long>`. All later importer-demo tasks build on these.

- [ ] **Step 1: Write the failing native-query test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/storage/SurveyResponseRepositoryTest.java`:

```java
package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SurveyResponseRepositoryTest {

    @Autowired
    private SurveyResponseRepository repository;

    @Test
    void updateIfNewerReturnsZeroWhenRowIsAbsent() {
        int updated = repository.updateIfNewer("survey-1", "resp-1", Instant.now(), "{}", Instant.now());

        assertThat(updated).isZero();
    }

    @Test
    void updateIfNewerUpdatesWhenIncomingIsNewer() {
        Instant older = Instant.now().minusSeconds(60);
        SurveyResponseEntity entity = new SurveyResponseEntity();
        entity.setSurveyId("survey-1");
        entity.setResponseId("resp-1");
        entity.setDateModified(older);
        entity.setPayload("old");
        entity.setImportedAt(older);
        repository.saveAndFlush(entity);

        Instant newer = Instant.now();
        int updated = repository.updateIfNewer("survey-1", "resp-1", newer, "new", newer);

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow().getPayload())
                .isEqualTo("new");
    }

    @Test
    void updateIfNewerNoOpsWhenIncomingIsOlderOrEqual() {
        Instant current = Instant.now();
        SurveyResponseEntity entity = new SurveyResponseEntity();
        entity.setSurveyId("survey-1");
        entity.setResponseId("resp-1");
        entity.setDateModified(current);
        entity.setPayload("current");
        entity.setImportedAt(current);
        repository.saveAndFlush(entity);

        int updated = repository.updateIfNewer("survey-1", "resp-1", current.minusSeconds(1), "stale", current);

        assertThat(updated).isZero();
        assertThat(repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow().getPayload())
                .isEqualTo("current");
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=SurveyResponseRepositoryTest
```

Expected: compile error — `SurveyResponseEntity`/`SurveyResponseRepository` do not exist yet.

- [ ] **Step 3: Implement the domain records, entities, and repositories**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/domain/JobKind.java`:

```java
package com.testingai.surveyimporter.domain;

public enum JobKind {
    PAGE_SYNC,
    SINGLE_RESPONSE_SYNC
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/domain/TriggerType.java`:

```java
package com.testingai.surveyimporter.domain;

public enum TriggerType {
    SCHEDULED,
    WEBHOOK,
    MANUAL
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/domain/SyncJob.java`:

```java
package com.testingai.surveyimporter.domain;

import java.time.Instant;
import java.util.UUID;

public record SyncJob(UUID id, String surveyId, JobKind kind, String cursor, String responseId,
        TriggerType triggerType, int attemptCount, Instant nextAttemptAt) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/entity/SurveyResponseEntity.java`:

```java
package com.testingai.surveyimporter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "survey_response", uniqueConstraints = @UniqueConstraint(columnNames = { "survey_id", "response_id" }))
@Getter
@Setter
@NoArgsConstructor
public class SurveyResponseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private String surveyId;

    @Column(name = "response_id", nullable = false)
    private String responseId;

    @Column(name = "date_modified", nullable = false)
    private Instant dateModified;

    @Column(columnDefinition = "CLOB")
    private String payload;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/entity/SyncWatermarkEntity.java`:

```java
package com.testingai.surveyimporter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sync_watermark")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncWatermarkEntity {

    @Id
    @Column(name = "survey_id")
    private String surveyId;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/entity/DeadLetterJobEntity.java`:

```java
package com.testingai.surveyimporter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "dead_letter_job")
@Getter
@Setter
@NoArgsConstructor
public class DeadLetterJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private String surveyId;

    private String kind;

    private String cursor;

    @Column(name = "response_id")
    private String responseId;

    @Column(name = "trigger_type")
    private String triggerType;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "error_class")
    private String errorClass;

    @Column(name = "error_message", columnDefinition = "CLOB")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/SurveyResponseRepository.java`:

```java
package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponseEntity, Long> {

    Optional<SurveyResponseEntity> findBySurveyIdAndResponseId(String surveyId, String responseId);

    List<SurveyResponseEntity> findBySurveyId(String surveyId);

    @Modifying
    @Query(value = "UPDATE survey_response SET date_modified = :dateModified, payload = :payload, "
            + "imported_at = :importedAt WHERE survey_id = :surveyId AND response_id = :responseId "
            + "AND date_modified < :dateModified", nativeQuery = true)
    int updateIfNewer(@Param("surveyId") String surveyId, @Param("responseId") String responseId,
            @Param("dateModified") Instant dateModified, @Param("payload") String payload,
            @Param("importedAt") Instant importedAt);
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/SyncWatermarkRepository.java`:

```java
package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SyncWatermarkEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncWatermarkRepository extends JpaRepository<SyncWatermarkEntity, String> {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/dlq/DeadLetterJobRepository.java`:

```java
package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.entity.DeadLetterJobEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterJobRepository extends JpaRepository<DeadLetterJobEntity, Long> {
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SurveyResponseRepositoryTest
```

Expected: `BUILD SUCCESS`, 3 tests passed. `@Modifying` native queries require the surrounding call to be transactional — `@DataJpaTest` wraps each test method in a transaction automatically, so no extra annotation is needed here.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/domain \
        data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/entity \
        data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/storage \
        data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/dlq/DeadLetterJobRepository.java \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/storage
git commit -m "feat(survey-importer-demo): add domain model, entities, and repositories"
```

---

## Task 8: importer-demo idempotent upsert

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/SurveyResponseUpsert.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/UpsertService.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/storage/UpsertServiceTest.java`

**Interfaces:**
- Consumes: `SurveyResponseEntity`, `SurveyResponseRepository` (Task 7).
- Produces: `record SurveyResponseUpsert(String surveyId, String responseId, Instant dateModified, String payload)`; `class UpsertService` — `void upsert(SurveyResponseUpsert upsert)`. Consumed by `ConnectorService` (Task 12).

**Design note (deviation from the spec's exception-catching description):** rather than wrapping the insert attempt in a `try/catch (DataIntegrityViolationException)`, `UpsertService` lets a genuine concurrent-insert race propagate. `SyncWorkerPool` (Task 14) already retries any unexpected exception at the job level; on retry, `updateIfNewer`/`findBySurveyIdAndResponseId` correctly see the row the other writer just inserted and no-op. This reuses the existing retry infrastructure instead of adding bespoke exception-swallowing, and avoids continuing to use an `EntityManager` after a constraint-violation exception within the same transaction (risky under some JPA providers).

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/storage/UpsertServiceTest.java`:

```java
package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(UpsertService.class)
class UpsertServiceTest {

    @Autowired
    private UpsertService upsertService;

    @Autowired
    private SurveyResponseRepository repository;

    @Test
    void insertsWhenAbsent() {
        upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", Instant.now(), "{\"a\":1}"));

        SurveyResponseEntity saved = repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow();
        assertThat(saved.getPayload()).isEqualTo("{\"a\":1}");
    }

    @Test
    void updatesWhenIncomingIsNewer() {
        Instant older = Instant.now().minusSeconds(60);
        Instant newer = Instant.now();
        upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", older, "old-payload"));

        upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", newer, "new-payload"));

        SurveyResponseEntity saved = repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow();
        assertThat(saved.getPayload()).isEqualTo("new-payload");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void noOpsWhenIncomingIsOlderOrEqual() {
        Instant newer = Instant.now();
        Instant older = newer.minusSeconds(60);
        upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", newer, "current-payload"));

        upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", older, "stale-payload"));

        SurveyResponseEntity saved = repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow();
        assertThat(saved.getPayload()).isEqualTo("current-payload");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void replayingTheSameUpsertAfterAnotherWriterAlreadyWonIsANoOp() {
        Instant now = Instant.now();
        SurveyResponseEntity winner = new SurveyResponseEntity();
        winner.setSurveyId("survey-1");
        winner.setResponseId("resp-1");
        winner.setDateModified(now);
        winner.setPayload("winner");
        winner.setImportedAt(now);
        repository.saveAndFlush(winner);

        upsertService.upsert(new SurveyResponseUpsert("survey-1", "resp-1", now, "loser"));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findBySurveyIdAndResponseId("survey-1", "resp-1").orElseThrow().getPayload())
                .isEqualTo("winner");
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=UpsertServiceTest
```

Expected: compile error — `SurveyResponseUpsert`/`UpsertService` do not exist yet.

- [ ] **Step 3: Implement `SurveyResponseUpsert` and `UpsertService`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/SurveyResponseUpsert.java`:

```java
package com.testingai.surveyimporter.storage;

import java.time.Instant;

public record SurveyResponseUpsert(String surveyId, String responseId, Instant dateModified, String payload) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/storage/UpsertService.java`:

```java
package com.testingai.surveyimporter.storage;

import com.testingai.surveyimporter.entity.SurveyResponseEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UpsertService {

    private final SurveyResponseRepository repository;

    public UpsertService(SurveyResponseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsert(SurveyResponseUpsert upsert) {
        Instant importedAt = Instant.now();
        int updated = repository.updateIfNewer(upsert.surveyId(), upsert.responseId(), upsert.dateModified(),
                upsert.payload(), importedAt);
        if (updated > 0) {
            return;
        }
        boolean exists = repository.findBySurveyIdAndResponseId(upsert.surveyId(), upsert.responseId()).isPresent();
        if (exists) {
            return;
        }
        SurveyResponseEntity entity = new SurveyResponseEntity();
        entity.setSurveyId(upsert.surveyId());
        entity.setResponseId(upsert.responseId());
        entity.setDateModified(upsert.dateModified());
        entity.setPayload(upsert.payload());
        entity.setImportedAt(importedAt);
        repository.save(entity);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=UpsertServiceTest
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/storage/SurveyResponseUpsert.java \
        data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/storage/UpsertService.java \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/storage/UpsertServiceTest.java
git commit -m "feat(survey-importer-demo): add idempotent upsert service"
```

---

## Task 9: importer-demo SurveyMonkey HTTP client (retry, circuit breaker, rate limiter)

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/RetryableSyncException.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/PermanentSyncException.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/SurveyMonkeyClient.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/client/SurveyMonkeyClientClassifyTest.java`

**Interfaces:**
- Consumes: nothing new (uses `ResponsesPage`-shaped JSON over HTTP — importer-demo defines its own copies of the wire-shape records, since modules in this repo don't share code across module boundaries).
- Produces: `class RetryableSyncException extends RuntimeException`; `class PermanentSyncException extends RuntimeException`; `class SurveyMonkeyClient` — `ResponsesPage fetchResponsesPage(String surveyId, String cursor, int perPage, Instant startModifiedAt)`, `SourceSurveyResponseView fetchSingleResponse(String surveyId, String responseId)`, `static RuntimeException classify(RestClientResponseException e)`. Consumed by `ConnectorService` (Task 12).

Since `importer-demo` is a separate module from `source-demo`, it defines its own wire-shape records (`ResponsesPage`, `LinksView`, `SourceSurveyResponseView`, `AnswerView`) in its `client` package — structurally identical to `source-demo`'s domain records but independently owned, consistent with this repo's module-isolation convention.

- [ ] **Step 1: Write the failing classification test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/client/SurveyMonkeyClientClassifyTest.java`:

```java
package com.testingai.surveyimporter.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;

class SurveyMonkeyClientClassifyTest {

    @Test
    void tooManyRequestsIsRetryable() {
        RestClientResponseException e = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(SurveyMonkeyClient.classify(e)).isInstanceOf(RetryableSyncException.class);
    }

    @Test
    void serverErrorIsRetryable() {
        RestClientResponseException e = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);

        assertThat(SurveyMonkeyClient.classify(e)).isInstanceOf(RetryableSyncException.class);
    }

    @Test
    void notFoundIsPermanent() {
        RestClientResponseException e = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                HttpHeaders.EMPTY, new byte[0], null);

        assertThat(SurveyMonkeyClient.classify(e)).isInstanceOf(PermanentSyncException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=SurveyMonkeyClientClassifyTest
```

Expected: compile error — `SurveyMonkeyClient` does not exist yet.

- [ ] **Step 3: Implement the exceptions, wire-shape records, and `SurveyMonkeyClient`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/RetryableSyncException.java`:

```java
package com.testingai.surveyimporter.client;

public class RetryableSyncException extends RuntimeException {

    public RetryableSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/PermanentSyncException.java`:

```java
package com.testingai.surveyimporter.client;

public class PermanentSyncException extends RuntimeException {

    public PermanentSyncException(String message) {
        super(message);
    }

    public PermanentSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/AnswerView.java`:

```java
package com.testingai.surveyimporter.client;

public record AnswerView(String questionId, String text) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/SourceSurveyResponseView.java`:

```java
package com.testingai.surveyimporter.client;

import java.time.Instant;
import java.util.List;

public record SourceSurveyResponseView(String id, String surveyId, Instant dateModified, List<AnswerView> answers) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/LinksView.java`:

```java
package com.testingai.surveyimporter.client;

public record LinksView(String next) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/ResponsesPage.java`:

```java
package com.testingai.surveyimporter.client;

import java.util.List;

public record ResponsesPage(List<SourceSurveyResponseView> data, int page, int perPage, int total, LinksView links) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/client/SurveyMonkeyClient.java`:

```java
package com.testingai.surveyimporter.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Component
public class SurveyMonkeyClient {

    private final RestClient restClient;

    public SurveyMonkeyClient(RestClient.Builder builder, @Value("${surveymonkey.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Retry(name = "surveyMonkey")
    @CircuitBreaker(name = "surveyMonkey")
    @RateLimiter(name = "surveyMonkey")
    public ResponsesPage fetchResponsesPage(String surveyId, String cursor, int perPage, Instant startModifiedAt) {
        try {
            return restClient.get().uri(uriBuilder -> {
                uriBuilder.path("/v3/surveys/{surveyId}/responses/bulk").queryParam("page",
                        cursor != null ? cursor : "1").queryParam("per_page", perPage);
                if (startModifiedAt != null) {
                    uriBuilder.queryParam("start_modified_at", startModifiedAt.toString());
                }
                return uriBuilder.build(surveyId);
            }).retrieve().body(ResponsesPage.class);
        } catch (RestClientResponseException e) {
            throw classify(e);
        } catch (ResourceAccessException e) {
            throw new RetryableSyncException("Network error calling SurveyMonkey", e);
        }
    }

    @Retry(name = "surveyMonkey")
    @CircuitBreaker(name = "surveyMonkey")
    @RateLimiter(name = "surveyMonkey")
    public SourceSurveyResponseView fetchSingleResponse(String surveyId, String responseId) {
        try {
            return restClient.get().uri("/v3/surveys/{surveyId}/responses/{responseId}", surveyId, responseId)
                    .retrieve().body(SourceSurveyResponseView.class);
        } catch (RestClientResponseException e) {
            throw classify(e);
        } catch (ResourceAccessException e) {
            throw new RetryableSyncException("Network error calling SurveyMonkey", e);
        }
    }

    static RuntimeException classify(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 429 || status >= 500) {
            return new RetryableSyncException("Retryable SurveyMonkey error: HTTP " + status, e);
        }
        return new PermanentSyncException("Permanent SurveyMonkey error: HTTP " + status, e);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SurveyMonkeyClientClassifyTest
```

Expected: `BUILD SUCCESS`, 3 tests passed.

- [ ] **Step 5: Restore the `retry-exceptions` config now that the class exists**

Modify `.../importer-demo/src/main/resources/application.yml` — `RetryableSyncException` now exists, so add back the `retry-exceptions` list Task 1 deliberately omitted:

```yaml
# old
        exponential-backoff-multiplier: 2
        randomized-wait-factor: 0.5
  circuitbreaker:
```
```yaml
# new
        exponential-backoff-multiplier: 2
        randomized-wait-factor: 0.5
        retry-exceptions:
          - com.testingai.surveyimporter.client.RetryableSyncException
  circuitbreaker:
```

- [ ] **Step 6: Run the full importer-demo unit test suite to confirm the context still loads**

```bash
mvn test
```

Expected: `BUILD SUCCESS` — confirms `SurveyImporterApplicationTest`'s context-loads test now succeeds with the `retry-exceptions` class reference resolvable.

- [ ] **Step 7: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/client \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/client \
        data-integration/survey-monkey-import/importer-demo/src/main/resources/application.yml
git commit -m "feat(survey-importer-demo): add SurveyMonkey client with retry/circuit-breaker/rate-limiter"
```

---

## Task 10: importer-demo in-process job queue

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/queue/DelayedSyncJob.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/queue/JobQueue.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/queue/JobQueueTest.java`

**Interfaces:**
- Consumes: `SyncJob` (Task 7).
- Produces: `class JobQueue` — `void enqueue(SyncJob job)`, `void enqueue(SyncJob job, Duration delay)`, `SyncJob take()` (blocking), `int size()`. Consumed by `ConnectorService` (Task 12), `SyncWorkerPool` (Task 14), `DeadLetterService` (Task 13), `WebhookController` (Task 16), `SyncScheduler` (Task 17), `DemoController` (Task 18).

`DelayedSyncJob` is package-private plumbing (implements `Delayed`) — never used outside `JobQueue`, so it has no dedicated test; it's exercised indirectly through `JobQueueTest`.

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/queue/JobQueueTest.java`:

```java
package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobQueueTest {

    private final JobQueue jobQueue = new JobQueue();

    @Test
    void enqueueWithoutDelayIsImmediatelyAvailable() throws InterruptedException {
        SyncJob job = newJob();
        jobQueue.enqueue(job);

        assertThat(jobQueue.take()).isEqualTo(job);
    }

    @Test
    void enqueueWithDelayIsCountedInSizeBeforeItsDelayElapses() throws InterruptedException {
        SyncJob job = newJob();
        jobQueue.enqueue(job, Duration.ofMillis(200));

        assertThat(jobQueue.size()).isEqualTo(1);

        assertThat(jobQueue.take()).isEqualTo(job);
    }

    private SyncJob newJob() {
        return new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, null, null, TriggerType.MANUAL, 0,
                Instant.now());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=JobQueueTest
```

Expected: compile error — `JobQueue` does not exist yet.

- [ ] **Step 3: Implement `DelayedSyncJob` and `JobQueue`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/queue/DelayedSyncJob.java`:

```java
package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.domain.SyncJob;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

final class DelayedSyncJob implements Delayed {

    private final SyncJob job;
    private final long readyAtNanos;

    DelayedSyncJob(SyncJob job, Duration delay) {
        this.job = job;
        this.readyAtNanos = System.nanoTime() + delay.toNanos();
    }

    SyncJob job() {
        return job;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(readyAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        return Long.compare(this.readyAtNanos, ((DelayedSyncJob) other).readyAtNanos);
    }
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/queue/JobQueue.java`:

```java
package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.domain.SyncJob;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.DelayQueue;

@Component
public class JobQueue {

    private final DelayQueue<DelayedSyncJob> queue = new DelayQueue<>();

    public void enqueue(SyncJob job) {
        enqueue(job, Duration.ZERO);
    }

    public void enqueue(SyncJob job, Duration delay) {
        queue.put(new DelayedSyncJob(job, delay));
    }

    public SyncJob take() throws InterruptedException {
        return queue.take().job();
    }

    public int size() {
        return queue.size();
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=JobQueueTest
```

Expected: `BUILD SUCCESS`, 2 tests passed (the second takes ~200ms while it blocks on the delay).

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/queue \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/queue
git commit -m "feat(survey-importer-demo): add in-process delayed job queue"
```

---

## Task 11: importer-demo connector service (pagination)

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/connector/ConnectorService.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/connector/ConnectorServiceTest.java`

**Interfaces:**
- Consumes: `SurveyMonkeyClient`, `ResponsesPage`, `SourceSurveyResponseView` (Task 9); `UpsertService`, `SurveyResponseUpsert` (Task 8); `SyncWatermarkRepository`, `SyncWatermarkEntity` (Task 7); `JobQueue` (Task 10); `SyncJob`, `JobKind` (Task 7).
- Produces: `class ConnectorService` — `void process(SyncJob job)`. Consumed by `SyncWorkerPool` (Task 13).

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/connector/ConnectorServiceTest.java`:

```java
package com.testingai.surveyimporter.connector;

import com.testingai.surveyimporter.client.AnswerView;
import com.testingai.surveyimporter.client.LinksView;
import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.client.ResponsesPage;
import com.testingai.surveyimporter.client.SourceSurveyResponseView;
import com.testingai.surveyimporter.client.SurveyMonkeyClient;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;
import com.testingai.surveyimporter.storage.UpsertService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    @Mock
    private SurveyMonkeyClient client;

    @Mock
    private UpsertService upsertService;

    @Mock
    private SyncWatermarkRepository watermarkRepository;

    @Mock
    private JobQueue jobQueue;

    private ConnectorService connectorService;

    @BeforeEach
    void setUp() {
        connectorService = new ConnectorService(client, upsertService, watermarkRepository, jobQueue,
                new ObjectMapper());
    }

    @Test
    void lastPageUpdatesWatermarkWithoutEnqueueingContinuation() {
        SourceSurveyResponseView response = new SourceSurveyResponseView("resp-1", "survey-1", Instant.now(),
                List.of(new AnswerView("q1", "yes")));
        ResponsesPage page = new ResponsesPage(List.of(response), 1, 25, 1, new LinksView(null));
        when(client.fetchResponsesPage(eq("survey-1"), isNull(), anyInt(), any())).thenReturn(page);
        when(watermarkRepository.findById("survey-1")).thenReturn(Optional.empty());

        connectorService.process(newPageSyncJob(null));

        verify(upsertService).upsert(argThat(u -> u.responseId().equals("resp-1")));
        verify(watermarkRepository).save(any(SyncWatermarkEntity.class));
        verify(jobQueue, never()).enqueue(any());
    }

    @Test
    void pageWithNextCursorEnqueuesContinuationJob() {
        SourceSurveyResponseView response = new SourceSurveyResponseView("resp-1", "survey-1", Instant.now(),
                List.of());
        ResponsesPage page = new ResponsesPage(List.of(response), 1, 25, 50, new LinksView("2"));
        when(client.fetchResponsesPage(eq("survey-1"), isNull(), anyInt(), any())).thenReturn(page);
        when(watermarkRepository.findById("survey-1")).thenReturn(Optional.empty());

        connectorService.process(newPageSyncJob(null));

        ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
        verify(jobQueue).enqueue(captor.capture());
        assertThat(captor.getValue().cursor()).isEqualTo("2");
        verify(watermarkRepository, never()).save(any());
    }

    @Test
    void pageWithMissingIdThrowsPermanentSyncException() {
        SourceSurveyResponseView malformed = new SourceSurveyResponseView(null, "survey-1", Instant.now(), List.of());
        ResponsesPage page = new ResponsesPage(List.of(malformed), 1, 25, 1, new LinksView(null));
        when(client.fetchResponsesPage(eq("survey-1"), isNull(), anyInt(), any())).thenReturn(page);

        assertThatThrownBy(() -> connectorService.process(newPageSyncJob(null)))
                .isInstanceOf(PermanentSyncException.class);
        verifyNoInteractions(upsertService);
    }

    @Test
    void singleResponseSyncUpsertsOneResponseAndSkipsWatermark() {
        SourceSurveyResponseView response = new SourceSurveyResponseView("resp-9", "survey-1", Instant.now(),
                List.of());
        when(client.fetchSingleResponse("survey-1", "resp-9")).thenReturn(response);

        SyncJob job = new SyncJob(UUID.randomUUID(), "survey-1", JobKind.SINGLE_RESPONSE_SYNC, null, "resp-9",
                TriggerType.WEBHOOK, 0, Instant.now());
        connectorService.process(job);

        verify(upsertService).upsert(argThat(u -> u.responseId().equals("resp-9")));
        verifyNoInteractions(watermarkRepository);
    }

    private SyncJob newPageSyncJob(String cursor) {
        return new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, cursor, null, TriggerType.MANUAL, 0,
                Instant.now());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=ConnectorServiceTest
```

Expected: compile error — `ConnectorService` does not exist yet.

- [ ] **Step 3: Implement `ConnectorService`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/connector/ConnectorService.java`:

```java
package com.testingai.surveyimporter.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.client.ResponsesPage;
import com.testingai.surveyimporter.client.SourceSurveyResponseView;
import com.testingai.surveyimporter.client.SurveyMonkeyClient;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SurveyResponseUpsert;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;
import com.testingai.surveyimporter.storage.UpsertService;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ConnectorService {

    private static final int PAGE_SIZE = 25;

    private final SurveyMonkeyClient client;
    private final UpsertService upsertService;
    private final SyncWatermarkRepository watermarkRepository;
    private final JobQueue jobQueue;
    private final ObjectMapper objectMapper;

    public ConnectorService(SurveyMonkeyClient client, UpsertService upsertService,
            SyncWatermarkRepository watermarkRepository, JobQueue jobQueue, ObjectMapper objectMapper) {
        this.client = client;
        this.upsertService = upsertService;
        this.watermarkRepository = watermarkRepository;
        this.jobQueue = jobQueue;
        this.objectMapper = objectMapper;
    }

    public void process(SyncJob job) {
        if (job.kind() == JobKind.SINGLE_RESPONSE_SYNC) {
            processSingleResponse(job);
        } else {
            processPage(job);
        }
    }

    private void processPage(SyncJob job) {
        Instant startModifiedAt = job.cursor() == null
                ? watermarkRepository.findById(job.surveyId()).map(SyncWatermarkEntity::getLastSyncedAt).orElse(null)
                : null;
        ResponsesPage page = client.fetchResponsesPage(job.surveyId(), job.cursor(), PAGE_SIZE, startModifiedAt);

        for (SourceSurveyResponseView response : page.data()) {
            if (response.id() == null || response.dateModified() == null) {
                throw new PermanentSyncException("Malformed response in page: missing id or dateModified");
            }
        }
        for (SourceSurveyResponseView response : page.data()) {
            upsertService.upsert(new SurveyResponseUpsert(job.surveyId(), response.id(), response.dateModified(),
                    toPayload(response)));
        }

        if (page.links() != null && page.links().next() != null) {
            jobQueue.enqueue(new SyncJob(UUID.randomUUID(), job.surveyId(), JobKind.PAGE_SYNC, page.links().next(),
                    null, job.triggerType(), 0, Instant.now()));
        } else {
            watermarkRepository.save(new SyncWatermarkEntity(job.surveyId(), Instant.now()));
        }
    }

    private void processSingleResponse(SyncJob job) {
        SourceSurveyResponseView response = client.fetchSingleResponse(job.surveyId(), job.responseId());
        if (response.id() == null || response.dateModified() == null) {
            throw new PermanentSyncException("Malformed single response");
        }
        upsertService.upsert(new SurveyResponseUpsert(job.surveyId(), response.id(), response.dateModified(),
                toPayload(response)));
    }

    private String toPayload(SourceSurveyResponseView response) {
        try {
            return objectMapper.writeValueAsString(response.answers());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response answers", e);
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=ConnectorServiceTest
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/connector \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/connector
git commit -m "feat(survey-importer-demo): add connector service with resumable pagination"
```

---

## Task 12: importer-demo dead-letter queue

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/dlq/DeadLetterService.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/dlq/DlqController.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/dlq/DeadLetterServiceTest.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/dlq/DlqControllerTest.java`

**Interfaces:**
- Consumes: `DeadLetterJobRepository`, `DeadLetterJobEntity` (Task 7); `JobQueue` (Task 10); `SyncJob`, `JobKind`, `TriggerType` (Task 7).
- Produces: `class DeadLetterService` — `void deadLetter(SyncJob job, Exception cause)`, `List<DeadLetterJobEntity> list()`, `void redrive(Long id)`; `GET /demo/dlq`, `POST /demo/dlq/{id}/redrive`. Consumed by `SyncWorkerPool` (Task 13).

- [ ] **Step 1: Write the failing `DeadLetterService` test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/dlq/DeadLetterServiceTest.java`:

```java
package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.client.RetryableSyncException;
import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.DeadLetterJobEntity;
import com.testingai.surveyimporter.queue.JobQueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ DeadLetterService.class, JobQueue.class })
class DeadLetterServiceTest {

    @Autowired
    private DeadLetterService deadLetterService;

    @Autowired
    private DeadLetterJobRepository repository;

    @Autowired
    private JobQueue jobQueue;

    @Test
    void deadLetterPersistsFullContext() {
        SyncJob job = new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, "3", null, TriggerType.SCHEDULED,
                5, Instant.now());

        deadLetterService.deadLetter(job, new RetryableSyncException("boom", null));

        List<DeadLetterJobEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSurveyId()).isEqualTo("survey-1");
        assertThat(all.get(0).getCursor()).isEqualTo("3");
        assertThat(all.get(0).getAttemptCount()).isEqualTo(5);
        assertThat(all.get(0).getErrorClass()).isEqualTo(RetryableSyncException.class.getName());
    }

    @Test
    void redriveReenqueuesWithResetAttemptCountAndRemovesTheEntry() throws InterruptedException {
        SyncJob job = new SyncJob(UUID.randomUUID(), "survey-2", JobKind.SINGLE_RESPONSE_SYNC, null, "resp-9",
                TriggerType.WEBHOOK, 5, Instant.now());
        deadLetterService.deadLetter(job, new PermanentSyncException("bad data"));
        Long id = repository.findAll().get(0).getId();

        deadLetterService.redrive(id);

        assertThat(repository.findById(id)).isEmpty();
        SyncJob redriven = jobQueue.take();
        assertThat(redriven.surveyId()).isEqualTo("survey-2");
        assertThat(redriven.responseId()).isEqualTo("resp-9");
        assertThat(redriven.attemptCount()).isZero();
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=DeadLetterServiceTest
```

Expected: compile error — `DeadLetterService` does not exist yet.

- [ ] **Step 3: Implement `DeadLetterService`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/dlq/DeadLetterService.java`:

```java
package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.DeadLetterJobEntity;
import com.testingai.surveyimporter.queue.JobQueue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeadLetterService {

    private final DeadLetterJobRepository repository;
    private final JobQueue jobQueue;

    public DeadLetterService(DeadLetterJobRepository repository, JobQueue jobQueue) {
        this.repository = repository;
        this.jobQueue = jobQueue;
    }

    @Transactional
    public void deadLetter(SyncJob job, Exception cause) {
        DeadLetterJobEntity entity = new DeadLetterJobEntity();
        entity.setSurveyId(job.surveyId());
        entity.setKind(job.kind().name());
        entity.setCursor(job.cursor());
        entity.setResponseId(job.responseId());
        entity.setTriggerType(job.triggerType().name());
        entity.setAttemptCount(job.attemptCount());
        entity.setErrorClass(cause.getClass().getName());
        entity.setErrorMessage(cause.getMessage());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setLastAttemptAt(now);
        repository.save(entity);
    }

    public List<DeadLetterJobEntity> list() {
        return repository.findAll();
    }

    @Transactional
    public void redrive(Long id) {
        DeadLetterJobEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown DLQ entry: " + id));
        SyncJob job = new SyncJob(UUID.randomUUID(), entity.getSurveyId(), JobKind.valueOf(entity.getKind()),
                entity.getCursor(), entity.getResponseId(), TriggerType.valueOf(entity.getTriggerType()), 0,
                Instant.now());
        jobQueue.enqueue(job);
        repository.delete(entity);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=DeadLetterServiceTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Write the failing `DlqController` test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/dlq/DlqControllerTest.java`:

```java
package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.entity.DeadLetterJobEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DlqController.class)
class DlqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeadLetterService deadLetterService;

    @Test
    void listReturnsDlqEntries() throws Exception {
        DeadLetterJobEntity entity = new DeadLetterJobEntity();
        entity.setId(1L);
        entity.setSurveyId("survey-1");
        entity.setCreatedAt(Instant.now());
        when(deadLetterService.list()).thenReturn(List.of(entity));

        mockMvc.perform(get("/demo/dlq")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].survey_id").value("survey-1"));
    }

    @Test
    void redriveDelegatesToService() throws Exception {
        mockMvc.perform(post("/demo/dlq/1/redrive")).andExpect(status().isOk());

        verify(deadLetterService).redrive(1L);
    }
}
```

- [ ] **Step 6: Run it to verify it fails to compile**

```bash
mvn test -Dtest=DlqControllerTest
```

Expected: compile error — `DlqController` does not exist yet.

- [ ] **Step 7: Implement `DlqController`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/dlq/DlqController.java`:

```java
package com.testingai.surveyimporter.dlq;

import com.testingai.surveyimporter.entity.DeadLetterJobEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo/dlq")
public class DlqController {

    private final DeadLetterService deadLetterService;

    public DlqController(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @GetMapping
    public List<DeadLetterJobEntity> list() {
        return deadLetterService.list();
    }

    @PostMapping("/{id}/redrive")
    public void redrive(@PathVariable Long id) {
        deadLetterService.redrive(id);
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

```bash
mvn test -Dtest=DlqControllerTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 9: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/dlq \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/dlq
git commit -m "feat(survey-importer-demo): add dead-letter queue with redrive"
```

---

## Task 13: importer-demo monitoring metrics

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/SyncMetrics.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/monitoring/SyncMetricsTest.java`

**Interfaces:**
- Consumes: `JobQueue` (Task 10); `DeadLetterJobRepository` (Task 7); `SyncWatermarkRepository`, `SyncWatermarkEntity` (Task 7).
- Produces: `class SyncMetrics` — constructor `SyncMetrics(MeterRegistry, JobQueue, DeadLetterJobRepository, SyncWatermarkRepository, List<String> knownSurveyIds)`; `void recordProcessed(String outcome)` (`outcome` one of `"success"`, `"retried"`, `"dead_lettered"`). Consumed by `SyncWorkerPool` (Task 14).

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/monitoring/SyncMetricsTest.java`:

```java
package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncMetricsTest {

    @Test
    void recordProcessedIncrementsTheCounterForTheGivenOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobQueue jobQueue = new JobQueue();
        DeadLetterJobRepository dlqRepository = mock(DeadLetterJobRepository.class);
        when(dlqRepository.count()).thenReturn(0L);
        SyncWatermarkRepository watermarkRepository = mock(SyncWatermarkRepository.class);
        SyncMetrics metrics = new SyncMetrics(registry, jobQueue, dlqRepository, watermarkRepository,
                List.of("survey-1"));

        metrics.recordProcessed("success");
        metrics.recordProcessed("success");
        metrics.recordProcessed("retried");

        assertThat(registry.get("sync.jobs.processed").tag("outcome", "success").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("sync.jobs.processed").tag("outcome", "retried").counter().count()).isEqualTo(1.0);
    }

    @Test
    void rejectsAnUnknownOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobQueue jobQueue = new JobQueue();
        DeadLetterJobRepository dlqRepository = mock(DeadLetterJobRepository.class);
        when(dlqRepository.count()).thenReturn(0L);
        SyncWatermarkRepository watermarkRepository = mock(SyncWatermarkRepository.class);
        SyncMetrics metrics = new SyncMetrics(registry, jobQueue, dlqRepository, watermarkRepository, List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> metrics.recordProcessed("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=SyncMetricsTest
```

Expected: compile error — `SyncMetrics` does not exist yet.

- [ ] **Step 3: Implement `SyncMetrics`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/SyncMetrics.java`:

```java
package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class SyncMetrics {

    private final Counter processedSuccess;
    private final Counter processedRetried;
    private final Counter processedDeadLettered;

    public SyncMetrics(MeterRegistry meterRegistry, JobQueue jobQueue, DeadLetterJobRepository dlqRepository,
            SyncWatermarkRepository watermarkRepository, List<String> knownSurveyIds) {
        this.processedSuccess = Counter.builder("sync.jobs.processed").tag("outcome", "success")
                .register(meterRegistry);
        this.processedRetried = Counter.builder("sync.jobs.processed").tag("outcome", "retried")
                .register(meterRegistry);
        this.processedDeadLettered = Counter.builder("sync.jobs.processed").tag("outcome", "dead_lettered")
                .register(meterRegistry);
        Gauge.builder("sync.queue.depth", jobQueue, JobQueue::size).register(meterRegistry);
        Gauge.builder("sync.dlq.size", dlqRepository, DeadLetterJobRepository::count).register(meterRegistry);
        for (String surveyId : knownSurveyIds) {
            Gauge.builder("sync.lag.seconds", watermarkRepository, repo -> lagSeconds(repo, surveyId))
                    .tag("survey_id", surveyId).register(meterRegistry);
        }
    }

    public void recordProcessed(String outcome) {
        switch (outcome) {
            case "success" -> processedSuccess.increment();
            case "retried" -> processedRetried.increment();
            case "dead_lettered" -> processedDeadLettered.increment();
            default -> throw new IllegalArgumentException("Unknown outcome: " + outcome);
        }
    }

    private static double lagSeconds(SyncWatermarkRepository repository, String surveyId) {
        return repository.findById(surveyId).map(SyncWatermarkEntity::getLastSyncedAt)
                .map(lastSyncedAt -> (double) Duration.between(lastSyncedAt, Instant.now()).toSeconds()).orElse(-1.0);
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SyncMetricsTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/SyncMetrics.java \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/monitoring/SyncMetricsTest.java
git commit -m "feat(survey-importer-demo): add sync metrics"
```

---

## Task 14: importer-demo worker pool (retry redelivery + DLQ routing)

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/queue/SyncWorkerPool.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/queue/SyncWorkerPoolTest.java`

**Interfaces:**
- Consumes: `JobQueue` (Task 10); `ConnectorService` (Task 11); `DeadLetterService` (Task 12); `SyncMetrics` (Task 13); `RetryableSyncException`, `PermanentSyncException` (Task 9); `SyncJob` (Task 7).
- Produces: `class SyncWorkerPool` — `void start()` (`@PostConstruct`), `void shutdown()` (`@PreDestroy`), `void process(SyncJob job)` (public so it's directly testable and is also the method the internal run loop calls). This is the component that ties pagination, retries, and the DLQ together end to end.

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/queue/SyncWorkerPoolTest.java`:

```java
package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.client.RetryableSyncException;
import com.testingai.surveyimporter.connector.ConnectorService;
import com.testingai.surveyimporter.dlq.DeadLetterService;
import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.monitoring.SyncMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SyncWorkerPoolTest {

    @Mock
    private JobQueue jobQueue;

    @Mock
    private ConnectorService connectorService;

    @Mock
    private DeadLetterService deadLetterService;

    @Mock
    private SyncMetrics syncMetrics;

    private SyncWorkerPool workerPool;

    @BeforeEach
    void setUp() {
        workerPool = new SyncWorkerPool(jobQueue, connectorService, deadLetterService, syncMetrics);
    }

    @Test
    void successfulProcessingRecordsSuccessMetric() {
        SyncJob job = newJob(0);

        workerPool.process(job);

        verify(connectorService).process(job);
        verify(syncMetrics).recordProcessed("success");
        verifyNoInteractions(deadLetterService);
    }

    @Test
    void retryableFailureRequeuesWithIncrementedAttemptCount() {
        SyncJob job = newJob(0);
        doThrow(new RetryableSyncException("boom", null)).when(connectorService).process(job);

        workerPool.process(job);

        ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
        verify(jobQueue).enqueue(captor.capture(), any(Duration.class));
        assertThat(captor.getValue().attemptCount()).isEqualTo(1);
        verify(syncMetrics).recordProcessed("retried");
        verifyNoInteractions(deadLetterService);
    }

    @Test
    void exhaustedAttemptsDeadLetters() {
        SyncJob job = newJob(4);
        doThrow(new RetryableSyncException("boom", null)).when(connectorService).process(job);

        workerPool.process(job);

        verify(deadLetterService).deadLetter(eq(job), any());
        verify(syncMetrics).recordProcessed("dead_lettered");
        verify(jobQueue, never()).enqueue(any(), any());
    }

    @Test
    void permanentFailureDeadLettersImmediatelyWithoutRequeue() {
        SyncJob job = newJob(0);
        doThrow(new PermanentSyncException("bad data")).when(connectorService).process(job);

        workerPool.process(job);

        verify(deadLetterService).deadLetter(eq(job), any());
        verify(syncMetrics).recordProcessed("dead_lettered");
        verify(jobQueue, never()).enqueue(any(), any());
    }

    private SyncJob newJob(int attemptCount) {
        return new SyncJob(UUID.randomUUID(), "survey-1", JobKind.PAGE_SYNC, null, null, TriggerType.MANUAL,
                attemptCount, Instant.now());
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=SyncWorkerPoolTest
```

Expected: compile error — `SyncWorkerPool` does not exist yet.

- [ ] **Step 3: Implement `SyncWorkerPool`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/queue/SyncWorkerPool.java`:

```java
package com.testingai.surveyimporter.queue;

import com.testingai.surveyimporter.client.PermanentSyncException;
import com.testingai.surveyimporter.connector.ConnectorService;
import com.testingai.surveyimporter.dlq.DeadLetterService;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.monitoring.SyncMetrics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SyncWorkerPool {

    private static final int WORKER_COUNT = 3;
    private static final int MAX_ATTEMPTS = 5;

    private final JobQueue jobQueue;
    private final ConnectorService connectorService;
    private final DeadLetterService deadLetterService;
    private final SyncMetrics syncMetrics;

    private ExecutorService executor;

    public SyncWorkerPool(JobQueue jobQueue, ConnectorService connectorService, DeadLetterService deadLetterService,
            SyncMetrics syncMetrics) {
        this.jobQueue = jobQueue;
        this.connectorService = connectorService;
        this.deadLetterService = deadLetterService;
        this.syncMetrics = syncMetrics;
    }

    @PostConstruct
    public void start() {
        executor = Executors.newFixedThreadPool(WORKER_COUNT);
        for (int i = 0; i < WORKER_COUNT; i++) {
            executor.submit(this::runLoop);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void runLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                process(jobQueue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void process(SyncJob job) {
        try {
            connectorService.process(job);
            syncMetrics.recordProcessed("success");
        } catch (PermanentSyncException e) {
            deadLetterService.deadLetter(job, e);
            syncMetrics.recordProcessed("dead_lettered");
        } catch (Exception e) {
            if (job.attemptCount() + 1 >= MAX_ATTEMPTS) {
                deadLetterService.deadLetter(job, e);
                syncMetrics.recordProcessed("dead_lettered");
            } else {
                SyncJob retryJob = new SyncJob(job.id(), job.surveyId(), job.kind(), job.cursor(), job.responseId(),
                        job.triggerType(), job.attemptCount() + 1, Instant.now());
                Duration delay = Duration.ofMillis((long) (500 * Math.pow(2, job.attemptCount())));
                jobQueue.enqueue(retryJob, delay);
                syncMetrics.recordProcessed("retried");
            }
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SyncWorkerPoolTest
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/queue/SyncWorkerPool.java \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/queue/SyncWorkerPoolTest.java
git commit -m "feat(survey-importer-demo): add worker pool with retry redelivery and DLQ routing"
```

---

## Task 15: importer-demo status endpoint

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/SurveyStatus.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/DemoStatusResponse.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/DemoStatusController.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/monitoring/DemoStatusControllerTest.java`

**Interfaces:**
- Consumes: `SyncWatermarkRepository`, `SyncWatermarkEntity` (Task 7); `JobQueue` (Task 10); `DeadLetterJobRepository` (Task 7); Resilience4j's `CircuitBreakerRegistry` (auto-configured bean from `resilience4j-spring-boot3`, added in Task 1).
- Produces: `record SurveyStatus(String surveyId, Instant lastSyncedAt, Long lagSeconds)`; `record DemoStatusResponse(List<SurveyStatus> surveys, int queueDepth, long dlqSize, String circuitBreakerState)`; `GET /demo/status`.

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/monitoring/DemoStatusControllerTest.java`:

```java
package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoStatusController.class)
@TestPropertySource(properties = "importer.known-survey-ids=survey-1,survey-2")
class DemoStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SyncWatermarkRepository watermarkRepository;

    @MockitoBean
    private JobQueue jobQueue;

    @MockitoBean
    private DeadLetterJobRepository dlqRepository;

    @MockitoBean
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void statusReflectsQueueDlqAndCircuitBreakerState() throws Exception {
        when(watermarkRepository.findById(anyString())).thenReturn(Optional.empty());
        when(jobQueue.size()).thenReturn(3);
        when(dlqRepository.count()).thenReturn(2L);
        CircuitBreaker breaker = mock(CircuitBreaker.class);
        when(breaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(circuitBreakerRegistry.circuitBreaker("surveyMonkey")).thenReturn(breaker);

        mockMvc.perform(get("/demo/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.queue_depth").value(3)).andExpect(jsonPath("$.dlq_size").value(2))
                .andExpect(jsonPath("$.circuit_breaker_state").value("CLOSED"))
                .andExpect(jsonPath("$.surveys.length()").value(2));
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=DemoStatusControllerTest
```

Expected: compile error — `DemoStatusController` does not exist yet.

- [ ] **Step 3: Implement the status DTOs and controller**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/SurveyStatus.java`:

```java
package com.testingai.surveyimporter.monitoring;

import java.time.Instant;

public record SurveyStatus(String surveyId, Instant lastSyncedAt, Long lagSeconds) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/DemoStatusResponse.java`:

```java
package com.testingai.surveyimporter.monitoring;

import java.util.List;

public record DemoStatusResponse(List<SurveyStatus> surveys, int queueDepth, long dlqSize,
        String circuitBreakerState) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/DemoStatusController.java`:

```java
package com.testingai.surveyimporter.monitoring;

import com.testingai.surveyimporter.dlq.DeadLetterJobRepository;
import com.testingai.surveyimporter.entity.SyncWatermarkEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SyncWatermarkRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/demo")
public class DemoStatusController {

    private final SyncWatermarkRepository watermarkRepository;
    private final JobQueue jobQueue;
    private final DeadLetterJobRepository dlqRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final List<String> knownSurveyIds;

    public DemoStatusController(SyncWatermarkRepository watermarkRepository, JobQueue jobQueue,
            DeadLetterJobRepository dlqRepository, CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("#{'${importer.known-survey-ids}'.split(',')}") List<String> knownSurveyIds) {
        this.watermarkRepository = watermarkRepository;
        this.jobQueue = jobQueue;
        this.dlqRepository = dlqRepository;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.knownSurveyIds = knownSurveyIds;
    }

    @GetMapping("/status")
    public DemoStatusResponse status() {
        List<SurveyStatus> surveys = knownSurveyIds.stream().map(this::statusFor).toList();
        String breakerState = circuitBreakerRegistry.circuitBreaker("surveyMonkey").getState().name();
        return new DemoStatusResponse(surveys, jobQueue.size(), dlqRepository.count(), breakerState);
    }

    private SurveyStatus statusFor(String surveyId) {
        return watermarkRepository.findById(surveyId).map(SyncWatermarkEntity::getLastSyncedAt)
                .map(lastSyncedAt -> new SurveyStatus(surveyId, lastSyncedAt,
                        Duration.between(lastSyncedAt, Instant.now()).toSeconds()))
                .orElse(new SurveyStatus(surveyId, null, null));
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=DemoStatusControllerTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/SurveyStatus.java \
        data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/DemoStatusResponse.java \
        data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/monitoring/DemoStatusController.java \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/monitoring/DemoStatusControllerTest.java
git commit -m "feat(survey-importer-demo): add demo status endpoint"
```

---

## Task 16: importer-demo webhook receiver

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/webhook/WebhookEvent.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/webhook/WebhookSignatureVerifier.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/webhook/WebhookController.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/webhook/WebhookSignatureVerifierTest.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/webhook/WebhookControllerTest.java`

**Interfaces:**
- Consumes: `JobQueue`, `SyncJob`, `JobKind`, `TriggerType` (Tasks 7, 10).
- Produces: `record WebhookEvent(String surveyId, String responseId, String eventType)`; `class WebhookSignatureVerifier` — `boolean isValid(String rawBody, String signatureHeader)`, `static String hmacSha256Hex(String data, String secret)` (package-visible); `POST /webhooks/surveymonkey`.

- [ ] **Step 1: Write the failing `WebhookSignatureVerifier` test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/webhook/WebhookSignatureVerifierTest.java`:

```java
package com.testingai.surveyimporter.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("test-secret");

    @Test
    void validSignatureMatchesComputedHmac() {
        String body = "{\"survey_id\":\"survey-1\"}";
        String expected = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex(body, "test-secret");

        assertThat(verifier.isValid(body, expected)).isTrue();
    }

    @Test
    void tamperedBodyFailsVerification() {
        String signature = "sha256=" + WebhookSignatureVerifier.hmacSha256Hex("original", "test-secret");

        assertThat(verifier.isValid("tampered", signature)).isFalse();
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=WebhookSignatureVerifierTest
```

Expected: compile error — `WebhookSignatureVerifier` does not exist yet.

- [ ] **Step 3: Implement `WebhookEvent` and `WebhookSignatureVerifier`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/webhook/WebhookEvent.java`:

```java
package com.testingai.surveyimporter.webhook;

public record WebhookEvent(String surveyId, String responseId, String eventType) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/webhook/WebhookSignatureVerifier.java`:

```java
package com.testingai.surveyimporter.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class WebhookSignatureVerifier {

    private final String secret;

    public WebhookSignatureVerifier(@Value("${surveymonkey.webhook-secret}") String secret) {
        this.secret = secret;
    }

    public boolean isValid(String rawBody, String signatureHeader) {
        String expected = "sha256=" + hmacSha256Hex(rawBody, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=WebhookSignatureVerifierTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Write the failing `WebhookController` test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/webhook/WebhookControllerTest.java`:

```java
package com.testingai.surveyimporter.webhook;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.queue.JobQueue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookSignatureVerifier signatureVerifier;

    @MockitoBean
    private JobQueue jobQueue;

    @Test
    void validSignatureEnqueuesJobAndReturnsOk() throws Exception {
        String body = "{\"survey_id\":\"survey-1\",\"response_id\":\"resp-1\",\"event_type\":\"response_completed\"}";
        when(signatureVerifier.isValid(eq(body), anyString())).thenReturn(true);

        mockMvc.perform(post("/webhooks/surveymonkey").header("X-SurveyMonkey-Signature", "sha256=abc")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        verify(jobQueue).enqueue(argThat(job -> job.surveyId().equals("survey-1")
                && job.responseId().equals("resp-1") && job.kind() == JobKind.SINGLE_RESPONSE_SYNC));
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        when(signatureVerifier.isValid(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/webhooks/surveymonkey").header("X-SurveyMonkey-Signature", "sha256=bad")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());

        verifyNoInteractions(jobQueue);
    }
}
```

- [ ] **Step 6: Run it to verify it fails to compile**

```bash
mvn test -Dtest=WebhookControllerTest
```

Expected: compile error — `WebhookController` does not exist yet.

- [ ] **Step 7: Implement `WebhookController`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/webhook/WebhookController.java`:

```java
package com.testingai.surveyimporter.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class WebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final JobQueue jobQueue;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookSignatureVerifier signatureVerifier, JobQueue jobQueue,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.jobQueue = jobQueue;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhooks/surveymonkey")
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
            @RequestHeader("X-SurveyMonkey-Signature") String signatureHeader) {
        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WebhookEvent event = parse(rawBody);
        jobQueue.enqueue(new SyncJob(UUID.randomUUID(), event.surveyId(), JobKind.SINGLE_RESPONSE_SYNC, null,
                event.responseId(), TriggerType.WEBHOOK, 0, Instant.now()));
        return ResponseEntity.ok().build();
    }

    private WebhookEvent parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WebhookEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid webhook payload", e);
        }
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

```bash
mvn test -Dtest=WebhookControllerTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 9: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/webhook \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/webhook
git commit -m "feat(survey-importer-demo): add HMAC-verified webhook receiver"
```

---

## Task 17: importer-demo reconciliation scheduler

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/scheduler/SyncScheduler.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/scheduler/SyncSchedulerTest.java`

**Interfaces:**
- Consumes: `JobQueue`, `SyncJob`, `JobKind`, `TriggerType` (Tasks 7, 10).
- Produces: `class SyncScheduler` — `void scheduleSync()` (`@Scheduled`). This is the reconciliation mechanism described in the spec — no separate count-comparison component exists; idempotent upserts make a re-poll of a survey safe even if nothing changed.

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/scheduler/SyncSchedulerTest.java`:

```java
package com.testingai.surveyimporter.scheduler;

import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SyncSchedulerTest {

    @Test
    void enqueuesOnePageSyncJobPerKnownSurvey() {
        JobQueue jobQueue = mock(JobQueue.class);
        SyncScheduler scheduler = new SyncScheduler(jobQueue, List.of("survey-1", "survey-2"));

        scheduler.scheduleSync();

        verify(jobQueue, times(2)).enqueue(argThat(job -> job.triggerType() == TriggerType.SCHEDULED));
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=SyncSchedulerTest
```

Expected: compile error — `SyncScheduler` does not exist yet.

- [ ] **Step 3: Implement `SyncScheduler`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/scheduler/SyncScheduler.java`:

```java
package com.testingai.surveyimporter.scheduler;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class SyncScheduler {

    private final JobQueue jobQueue;
    private final List<String> knownSurveyIds;

    public SyncScheduler(JobQueue jobQueue,
            @Value("#{'${importer.known-survey-ids}'.split(',')}") List<String> knownSurveyIds) {
        this.jobQueue = jobQueue;
        this.knownSurveyIds = knownSurveyIds;
    }

    @Scheduled(fixedDelayString = "${importer.scheduler.fixed-delay-ms:60000}")
    public void scheduleSync() {
        for (String surveyId : knownSurveyIds) {
            jobQueue.enqueue(new SyncJob(UUID.randomUUID(), surveyId, JobKind.PAGE_SYNC, null, null,
                    TriggerType.SCHEDULED, 0, Instant.now()));
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=SyncSchedulerTest
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/scheduler \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/scheduler
git commit -m "feat(survey-importer-demo): add reconciliation scheduler"
```

---

## Task 18: importer-demo demo controller

**Files:**
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/controller/SurveyResponseView.java`
- Create: `.../importer-demo/src/main/java/com/testingai/surveyimporter/controller/DemoController.java`
- Test: `.../importer-demo/src/test/java/com/testingai/surveyimporter/controller/DemoControllerTest.java`

**Interfaces:**
- Consumes: `JobQueue`, `SyncJob`, `JobKind`, `TriggerType` (Tasks 7, 10); `SurveyResponseRepository`, `SurveyResponseEntity` (Task 7).
- Produces: `record SurveyResponseView(String responseId, Instant dateModified, String payload)`; `POST /demo/surveys/{surveyId}/sync`; `GET /demo/surveys/{surveyId}/responses`.

- [ ] **Step 1: Write the failing test**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/controller/DemoControllerTest.java`:

```java
package com.testingai.surveyimporter.controller;

import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.entity.SurveyResponseEntity;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SurveyResponseRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobQueue jobQueue;

    @MockitoBean
    private SurveyResponseRepository repository;

    @Test
    void triggerSyncEnqueuesManualJob() throws Exception {
        mockMvc.perform(post("/demo/surveys/survey-1/sync")).andExpect(status().isAccepted());

        verify(jobQueue).enqueue(
                argThat(job -> job.surveyId().equals("survey-1") && job.triggerType() == TriggerType.MANUAL));
    }

    @Test
    void listsImportedResponsesForASurvey() throws Exception {
        SurveyResponseEntity entity = new SurveyResponseEntity();
        entity.setResponseId("resp-1");
        entity.setDateModified(Instant.now());
        entity.setPayload("[]");
        when(repository.findBySurveyId("survey-1")).thenReturn(List.of(entity));

        mockMvc.perform(get("/demo/surveys/survey-1/responses")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].response_id").value("resp-1"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails to compile**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test -Dtest=DemoControllerTest
```

Expected: compile error — `DemoController` does not exist yet.

- [ ] **Step 3: Implement `SurveyResponseView` and `DemoController`**

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/controller/SurveyResponseView.java`:

```java
package com.testingai.surveyimporter.controller;

import java.time.Instant;

public record SurveyResponseView(String responseId, Instant dateModified, String payload) {
}
```

Create `.../importer-demo/src/main/java/com/testingai/surveyimporter/controller/DemoController.java`:

```java
package com.testingai.surveyimporter.controller;

import com.testingai.surveyimporter.domain.JobKind;
import com.testingai.surveyimporter.domain.SyncJob;
import com.testingai.surveyimporter.domain.TriggerType;
import com.testingai.surveyimporter.queue.JobQueue;
import com.testingai.surveyimporter.storage.SurveyResponseRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final JobQueue jobQueue;
    private final SurveyResponseRepository repository;

    public DemoController(JobQueue jobQueue, SurveyResponseRepository repository) {
        this.jobQueue = jobQueue;
        this.repository = repository;
    }

    @PostMapping("/surveys/{surveyId}/sync")
    public ResponseEntity<Void> triggerSync(@PathVariable String surveyId) {
        jobQueue.enqueue(new SyncJob(UUID.randomUUID(), surveyId, JobKind.PAGE_SYNC, null, null, TriggerType.MANUAL,
                0, Instant.now()));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/surveys/{surveyId}/responses")
    public List<SurveyResponseView> responses(@PathVariable String surveyId) {
        return repository.findBySurveyId(surveyId).stream()
                .map(entity -> new SurveyResponseView(entity.getResponseId(), entity.getDateModified(),
                        entity.getPayload()))
                .toList();
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
mvn test -Dtest=DemoControllerTest
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Run the full importer-demo unit test suite**

```bash
mvn test
```

Expected: `BUILD SUCCESS`, all importer-demo tests across every package pass.

- [ ] **Step 6: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/controller \
        data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/controller
git commit -m "feat(survey-importer-demo): add demo controller for manual sync and response inspection"
```

---

## Task 19: Gatling load test (importer-demo)

**Files:**
- Create: `.../importer-demo/src/test/java/com/testingai/surveyimporter/performance/DemoSimulation.java`

**Interfaces:**
- Consumes: `POST /demo/surveys/{id}/sync`, `GET /demo/status` (Tasks 18, 15).
- Produces: nothing consumed by other tasks — this is the module's Gatling entry point, matching `<simulationClass>` already set in Task 1's POM and excluded from `mvn test` by the inherited `**/performance/**` surefire exclude.

- [ ] **Step 1: Implement the simulation**

Create `.../importer-demo/src/test/java/com/testingai/surveyimporter/performance/DemoSimulation.java`:

```java
package com.testingai.surveyimporter.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DemoSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8102")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder importScenario = scenario("Survey Import")
            .exec(http("Trigger Sync — survey-1").post("/demo/surveys/survey-1/sync").check(status().is(202)))
            .exec(http("Trigger Sync — survey-2").post("/demo/surveys/survey-2/sync").check(status().is(202)))
            .exec(http("Status").get("/demo/status").check(status().is(200)));

    {
        setUp(importScenario.injectOpen(atOnceUsers(5))).protocols(httpProtocol).maxDuration(Duration.ofSeconds(60));
    }
}
```

- [ ] **Step 2: Verify `mvn test` still excludes it**

```bash
cd data-integration/survey-monkey-import/importer-demo
mvn test
```

Expected: `BUILD SUCCESS`; the test output must not mention `DemoSimulation`.

- [ ] **Step 3: Commit**

```bash
git add data-integration/survey-monkey-import/importer-demo/src/test/java/com/testingai/surveyimporter/performance
git commit -m "test(survey-importer-demo): add Gatling load test"
```

---

## Task 20: module and category READMEs

**Files:**
- Create: `data-integration/survey-monkey-import/README.md`

**Interfaces:**
- Consumes: nothing — documentation only.
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Write the module README**

Create `data-integration/survey-monkey-import/README.md`:

```markdown
# Survey Monkey Import Demo

A two-app demonstration of a reliable external-API ingestion pipeline: importing survey responses from SurveyMonkey. The design is a webhook + polling hybrid — webhooks for freshness, a scheduled poll for backfill and reconciliation — and demonstrates concretely the six concerns that motivate it: **pagination, idempotency, rate limiting, retries, monitoring, and dead-letter queues**.

## Apps

| App | Port | Role |
|---|---|---|
| [`source-demo`](source-demo/) | `8101` | A fake SurveyMonkey — seeded survey data, a paginated responses API, on-demand failure injection, HMAC-signed webhook dispatch |
| [`importer-demo`](importer-demo/) | `8102` | The actual subject of the design — connector, Resilience4j retry/circuit-breaker/rate-limiter, in-process job queue, idempotent storage, dead-letter queue, webhook receiver, scheduler, monitoring |

## Prerequisites

- Java 21
- Maven

No Docker/external infrastructure required — everything runs in-process, `source-demo` and `importer-demo` talk to each other over plain HTTP on localhost.

## Running

Start `source-demo` first, then `importer-demo`:

```bash
cd source-demo && mvn spring-boot:run
# in another terminal
cd importer-demo && mvn spring-boot:run
```

Swagger UI: `http://localhost:8101/swagger-ui/index.html` and `http://localhost:8102/swagger-ui/index.html`.

## Walkthrough — all six concerns

**Pagination** — a full backfill walks 10 pages per survey (25 responses/page, 250 seeded per survey), one queued job per page:

```bash
curl -X POST http://localhost:8102/demo/surveys/survey-1/sync
sleep 2
curl http://localhost:8102/demo/surveys/survey-1/responses | jq 'length'   # 250
```

**Idempotency** — trigger the same sync again; the response count doesn't change and no duplicates are created (upserts are keyed on `survey_id`+`response_id`, only applied when the incoming record is newer):

```bash
curl -X POST http://localhost:8102/demo/surveys/survey-1/sync
sleep 2
curl http://localhost:8102/demo/surveys/survey-1/responses | jq 'length'   # still 250
```

**Rate limiting + retries + circuit breaker** — force every call to fail with 429, trigger a sync, and watch the client retry with backoff, then the circuit breaker trip open:

```bash
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"RATE_LIMIT","rate":1.0}'
curl -X POST http://localhost:8102/demo/surveys/survey-2/sync
curl http://localhost:8102/demo/status | jq '.circuit_breaker_state'   # OPEN after enough failures
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"NONE","rate":0.0}'
```

**Dead-letter queue** — force malformed data, watch a job land in the DLQ, then redrive it once the failure mode is cleared:

```bash
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"MALFORMED","rate":1.0}'
curl -X POST http://localhost:8102/demo/surveys/survey-3/sync
sleep 2
curl http://localhost:8102/demo/dlq | jq '.[0].id'
curl -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' \
  -d '{"mode":"NONE","rate":0.0}'
curl -X POST http://localhost:8102/demo/dlq/1/redrive
```

**Webhooks (freshness)** — push one response without a full-survey poll:

```bash
curl -X POST "http://localhost:8101/admin/webhooks/trigger?surveyId=survey-1&responseId=survey-1-response-0"
```

**Monitoring**:

```bash
curl http://localhost:8102/demo/status
curl http://localhost:8102/actuator/metrics/sync.jobs.processed
curl http://localhost:8102/actuator/prometheus
```

## Testing

```bash
mvn test                # unit tests for both apps (Gatling excluded automatically)
mvn test -Dtest=ClassName
mvn gatling:test -pl survey-monkey-import/importer-demo   # load test — requires both apps running first
```

## Scope limits

- In-process job queue — jobs mid-flight are lost on a crash. Recoverable via the watermark + scheduled reconciliation and idempotent upserts, but not the durability a real broker would provide.
- Page-level validation granularity — one malformed response in a page dead-letters the whole page, not just that response.
- `source-demo`'s webhook dispatch is single-attempt, no retry — outbound webhook delivery retry is already the subject of `communication-protocols/webhooks`.
- No real SurveyMonkey OAuth — `source-demo` simulates only the data shape and failure behavior relevant to this design.
- No real dashboard — metrics are exposed via Actuator/Prometheus-format endpoints, not visualized.
- Single JVM per app — `importer-demo`'s workers are threads in one process, not independently-scalable instances.
```

- [ ] **Step 2: Commit**

```bash
git add data-integration/survey-monkey-import/README.md
git commit -m "docs(survey-monkey-import): add walkthrough README covering all six concerns"
```

---

## Task 21: cross-cutting repo documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing — documentation only.
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Add the command section to `CLAUDE.md`**

Modify `CLAUDE.md` — insert a new section immediately after the "### LMAX Disruptor demo" section (before "### DDD banking ledger demo"):

```markdown
### Survey Monkey import demo (two apps — run from each app's root, no docker infrastructure required)

```bash
cd data-integration/survey-monkey-import/source-demo
mvn clean package                    # build
mvn test                             # unit tests
mvn spring-boot:run                   # run first (fake SurveyMonkey, :8101)

cd ../importer-demo
mvn clean package                    # build
mvn test                             # unit tests (Gatling excluded automatically)
mvn test -Dtest=ClassName            # single test class
mvn spring-boot:run                   # run second (:8102) — requires source-demo running
mvn gatling:test                     # load test — requires both apps running first
```
```

- [ ] **Step 2: Add the repository layout row to `CLAUDE.md`**

Modify `CLAUDE.md` — in the "### Repository layout" table, add a row after the `concurrency-patterns/lmax-disruptor/spring-demo/` row and before the `domain-driven-design/banking/spring-demo/` row:

```markdown
| `data-integration/survey-monkey-import/{source-demo,importer-demo}/` | Reliable external-API ingestion pipeline demo — a fake SurveyMonkey (`source-demo`) plus a webhook+polling hybrid importer (`importer-demo`) demonstrating pagination, idempotency, rate limiting, retries, monitoring, and dead-letter queues; `source-demo` must be started before `importer-demo` — no external infrastructure required (H2 only) |
```

- [ ] **Step 3: Add the row to the root `README.md`**

Modify `README.md` — in the "## Repository layout" table, add a row after the `concurrency-patterns/` row and before the `domain-driven-design/` row:

```markdown
| `data-integration/` | Reliable external-API ingestion pipeline (SurveyMonkey survey-response import) — pagination, idempotency, rate limiting, retries, monitoring, dead-letter queues |
```

- [ ] **Step 4: Verify the docs render sensibly**

```bash
git diff CLAUDE.md README.md
```

Expected: two clean, additive diffs — a new command section + one new table row in `CLAUDE.md`, one new table row in `README.md`. No other lines touched.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: document the data-integration/survey-monkey-import module"
```

---

## Task 22: Final verification

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Full clean build (both apps)**

```bash
cd data-integration/survey-monkey-import/source-demo && mvn clean package
cd ../importer-demo && mvn clean package
```

Expected: `BUILD SUCCESS` for both, jars produced under each `target/`.

- [ ] **Step 2: Full unit test run (both apps)**

```bash
cd data-integration/survey-monkey-import/source-demo && mvn test
cd ../importer-demo && mvn test
```

Expected: `BUILD SUCCESS` for both; every test in every package passes; `performance/DemoSimulation` does not run.

- [ ] **Step 3: Smoke-test the six concerns against the running apps**

```bash
cd data-integration/survey-monkey-import/source-demo
mvn spring-boot:run &
sleep 8
cd ../importer-demo
mvn spring-boot:run &
sleep 8

# Pagination + idempotency
curl -s -X POST http://localhost:8102/demo/surveys/survey-1/sync
sleep 3
curl -s http://localhost:8102/demo/surveys/survey-1/responses | grep -o "response_id" | wc -l   # expect 250
curl -s -X POST http://localhost:8102/demo/surveys/survey-1/sync
sleep 3
curl -s http://localhost:8102/demo/surveys/survey-1/responses | grep -o "response_id" | wc -l   # still 250

# Rate limiting + retries + circuit breaker
curl -s -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' -d '{"mode":"RATE_LIMIT","rate":1.0}'
curl -s -X POST http://localhost:8102/demo/surveys/survey-2/sync
sleep 5
curl -s http://localhost:8102/demo/status
curl -s -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' -d '{"mode":"NONE","rate":0.0}'

# Dead-letter queue + redrive
curl -s -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' -d '{"mode":"MALFORMED","rate":1.0}'
curl -s -X POST http://localhost:8102/demo/surveys/survey-3/sync
sleep 3
curl -s http://localhost:8102/demo/dlq
curl -s -X POST http://localhost:8101/admin/failure-mode -H 'Content-Type: application/json' -d '{"mode":"NONE","rate":0.0}'

# Webhook path
curl -s -X POST "http://localhost:8101/admin/webhooks/trigger?surveyId=survey-1&responseId=survey-1-response-1"
sleep 1
curl -s http://localhost:8102/demo/surveys/survey-1/responses | grep -o "survey-1-response-1"

# Monitoring
curl -s http://localhost:8102/actuator/prometheus | grep sync_jobs_processed

kill %1 %2
```

Expected: pagination produces exactly 250 responses and stays at 250 on replay (idempotency); `/demo/status` eventually shows a non-`CLOSED` circuit-breaker state under forced `RATE_LIMIT`; at least one entry appears in `/demo/dlq` under forced `MALFORMED` and disappears after redrive; the webhook-triggered response ID appears in the imported list; the Prometheus endpoint exposes `sync_jobs_processed_total`.

- [ ] **Step 4: Confirm the pre-commit hook covers the new module**

```bash
cd /Users/admin/IdeaProjects/private/techmix-copy
git log --oneline -1 -- data-integration/survey-monkey-import/importer-demo/src/main/java/com/testingai/surveyimporter/controller/DemoController.java
```

Expected: shows the Task 18 commit — confirms the file was committed cleanly through the hook.

- [ ] **Step 5: Final status check**

```bash
git status
```

Expected: clean working tree — every file created during this plan has been committed.

---

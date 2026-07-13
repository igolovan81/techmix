# SDLC Agent Phase 1 (Intake + Investigate) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `ai/sdlc-agent/spring-demo`, a standalone Spring Boot module implementing Phase 1 of `ai/sdlc-agent/README.md`: fetch a ticket from Jira or Zendesk, agentically query a real Splunk instance for correlated log evidence via a `query_logs` tool, and return a structured `RootCauseHypothesis`. Read-only — no file writes, git operations, deploy, or release.

**Architecture:** `InvestigateService` runs the same agentic-loop shape already proven in this repo's `ai/task-automation-agent/service/AgentService.java`: fetch the ticket deterministically, then loop Claude against a single `query_logs` tool (backed by `SplunkLogSource`) until it returns a final JSON answer or the iteration cap is hit. `TicketSource` is a pluggable interface with `JiraTicketSource`/`ZendeskTicketSource` implementations, selected via `@ConditionalOnProperty(sdlc.ticket-source)` so exactly one is active at a time.

**Tech Stack:** Java 21, Spring Boot 3.4.4 (standalone module, own `spring-boot-starter-parent`, not part of any multi-module reactor — matches `ai/task-automation-agent`), Anthropic Java SDK 2.40.1, `spring-boot-starter-web`, `spring-boot-starter-validation`, WireMock 3.5.4 (test), JUnit 5 + Mockito + AssertJ, real Splunk via Docker.

## Global Constraints

- Standalone Maven module: `<parent>org.springframework.boot:spring-boot-starter-parent:3.4.4</parent>` with empty `<relativePath/>`, exactly like `ai/task-automation-agent/spring-demo/pom.xml` — do **not** wire this into any parent reactor pom.
- Plain explicit constructors throughout (no Lombok annotations), matching every class in `ai/task-automation-agent` and `ai/code-review-agent` despite `lombok` being a listed (unused) dependency.
- `@MockBean` (not `@MockitoBean`) in `@WebMvcTest` classes — matches `AgentControllerTest`'s established convention in this sibling module.
- HTTP mocking in unit tests uses WireMock (`org.wiremock:wiremock-standalone:3.5.4`), started with `wireMockConfig().dynamicPort()` per test class — matches `WebSearchToolTest`.
- The integration test is tagged `@Tag("integration")` and excluded from `mvn test` via `<excludedGroups>integration</excludedGroups>` in `maven-surefire-plugin` config (not a package-name pattern) — matches `AgentIntegrationTest`/this module's own pom convention, not the `**/performance/**` pattern used elsewhere in this repo.
- No Gatling — this module follows `task-automation-agent`'s convention of no `performance/` package; load-testing an LLM-backed endpoint isn't a meaningful signal here.
- Prefer records, pattern matching, switch expressions, text blocks over pre-Java-21 idioms on any line this plan adds.
- Catch `InterruptedException` and restore the interrupt flag rather than declaring `throws` where the framework controls the call (per `.claude/rules/code-review.md`).
- No explicit `.toString()` on values passed to SLF4J `{}` placeholders or string concatenation, except where a compile-time `String`/`CharSequence` return type is required.

---

### Task 1: Scaffold the `sdlc-agent-demo` module skeleton

**Files:**
- Create: `ai/sdlc-agent/spring-demo/pom.xml`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/SdlcAgentApplication.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/resources/application.yml`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/SdlcAgentApplicationTest.java`

**Interfaces:**
- Produces: `com.testingai.sdlc.SdlcAgentApplication` (Spring Boot main class), Maven coordinates `com.testingai:sdlc-agent-spring-demo`, server port `8089`.

- [ ] **Step 1: Create the module POM**

`ai/sdlc-agent/spring-demo/pom.xml`:

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
        <relativePath/>
    </parent>

    <groupId>com.testingai</groupId>
    <artifactId>sdlc-agent-spring-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>sdlc-agent</name>

    <properties>
        <java.version>21</java.version>
        <lombok.version>1.18.38</lombok.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.anthropic</groupId>
            <artifactId>anthropic-java</artifactId>
            <version>2.40.1</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>3.5.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <excludedGroups>integration</excludedGroups>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create the Spring Boot main class**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/SdlcAgentApplication.java`:

```java
package com.testingai.sdlc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SdlcAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SdlcAgentApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `application.yml`**

`ai/sdlc-agent/spring-demo/src/main/resources/application.yml`:

```yaml
server:
  port: 8089

sdlc:
  ticket-source: jira

agent:
  max-iterations: 10

anthropic:
  api-key: ${ANTHROPIC_API_KEY:}
  model: claude-sonnet-4-6

jira:
  base-url: ${JIRA_BASE_URL:}
  email: ${JIRA_EMAIL:}
  api-token: ${JIRA_API_TOKEN:}
  service-field: customfield_10050

zendesk:
  subdomain: ${ZENDESK_SUBDOMAIN:}
  email: ${ZENDESK_EMAIL:}
  api-token: ${ZENDESK_API_TOKEN:}
  service-tag-prefix: ""

splunk:
  base-url: https://localhost:8093
  api-token: ${SPLUNK_API_TOKEN:}
  search-timeout-seconds: 10
```

- [ ] **Step 4: Write the application smoke test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/SdlcAgentApplicationTest.java`:

```java
package com.testingai.sdlc;

import org.junit.jupiter.api.Test;

class SdlcAgentApplicationTest {

    @Test
    void mainClassExists() {
        new SdlcAgentApplication();
    }
}
```

- [ ] **Step 5: Build the module**

Run: `cd ai/sdlc-agent/spring-demo && mvn clean package`
Expected: `BUILD SUCCESS`, `SdlcAgentApplicationTest` reported passing.

- [ ] **Step 6: Commit**

```bash
git add ai/sdlc-agent/spring-demo/pom.xml \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/SdlcAgentApplication.java \
  ai/sdlc-agent/spring-demo/src/main/resources/application.yml \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/SdlcAgentApplicationTest.java
git commit -m "feat(sdlc-agent): scaffold sdlc-agent-demo module"
```

---

### Task 2: Configuration properties and `AppConfig`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/SdlcProperties.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/AgentProperties.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/AnthropicProperties.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/JiraProperties.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/ZendeskProperties.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/SplunkProperties.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/AppConfig.java`

**Interfaces:**
- Produces: six `@ConfigurationProperties` records bound from `application.yml` (Task 1); `AppConfig` beans `AnthropicClient`, `RestClient jiraRestClient`, `RestClient zendeskRestClient`, `RestClient splunkRestClient` — consumed by `JiraTicketSource`/`ZendeskTicketSource` (Tasks 4–5), `SplunkLogSource` (Task 6), `InvestigateService` (Task 8).
- `@PostConstruct` validates `ANTHROPIC_API_KEY` and `SPLUNK_API_TOKEN` always, plus `JIRA_API_TOKEN` or `ZENDESK_API_TOKEN` depending on `sdlc.ticket-source` — fails fast on missing credentials, matching `AppConfig` in `task-automation-agent`.

No dedicated unit test for this task — `@ConfigurationProperties` records have no logic to test, and `AppConfig`'s `@PostConstruct` validation is verified end-to-end in Task 14's manual smoke test (matches `task-automation-agent`, which has no `AppConfigTest` either).

- [ ] **Step 1: Create the property records**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/SdlcProperties.java`:

```java
package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sdlc")
public record SdlcProperties(String ticketSource) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/AgentProperties.java`:

```java
package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(int maxIterations) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/AnthropicProperties.java`:

```java
package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(String apiKey, String model) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/JiraProperties.java`:

```java
package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public record JiraProperties(String baseUrl, String email, String apiToken, String serviceField) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/ZendeskProperties.java`:

```java
package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zendesk")
public record ZendeskProperties(String subdomain, String email, String apiToken, String serviceTagPrefix) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/SplunkProperties.java`:

```java
package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "splunk")
public record SplunkProperties(String baseUrl, String apiToken, int searchTimeoutSeconds) {
}
```

- [ ] **Step 2: Create `AppConfig`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/AppConfig.java`:

```java
package com.testingai.sdlc.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private final SdlcProperties sdlc;
    private final AnthropicProperties anthropic;
    private final JiraProperties jira;
    private final ZendeskProperties zendesk;
    private final SplunkProperties splunk;

    public AppConfig(SdlcProperties sdlc, AnthropicProperties anthropic, JiraProperties jira,
            ZendeskProperties zendesk, SplunkProperties splunk) {
        this.sdlc = sdlc;
        this.anthropic = anthropic;
        this.jira = jira;
        this.zendesk = zendesk;
        this.splunk = splunk;
    }

    @PostConstruct
    public void validateApiKeys() {
        require(anthropic.apiKey(), "ANTHROPIC_API_KEY");
        require(splunk.apiToken(), "SPLUNK_API_TOKEN");
        if ("zendesk".equalsIgnoreCase(sdlc.ticketSource())) {
            require(zendesk.apiToken(), "ZENDESK_API_TOKEN");
        } else {
            require(jira.apiToken(), "JIRA_API_TOKEN");
        }
    }

    private void require(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " environment variable is not set");
        }
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder().apiKey(anthropic.apiKey()).build();
    }

    @Bean
    public RestClient jiraRestClient() {
        return RestClient.builder().baseUrl(jira.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(jira.email(), jira.apiToken())).build();
    }

    @Bean
    public RestClient zendeskRestClient() {
        return RestClient.builder().baseUrl("https://" + zendesk.subdomain() + ".zendesk.com")
                .defaultHeaders(headers -> headers.setBasicAuth(zendesk.email() + "/token", zendesk.apiToken()))
                .build();
    }

    @Bean
    public RestClient splunkRestClient() {
        return RestClient.builder().baseUrl(splunk.baseUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(splunk.apiToken())).build();
    }
}
```

- [ ] **Step 3: Compile**

Run: `cd ai/sdlc-agent/spring-demo && mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/
git commit -m "feat(sdlc-agent): add configuration properties and AppConfig"
```

---

### Task 3: Domain model records

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/Ticket.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/LogEntry.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/RootCauseHypothesis.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/InvestigateRequest.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/InvestigateResponse.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/StepRecord.java`

**Interfaces:**
- Produces: `Ticket(String id, String title, String description, String severity, String service, Instant reportedAt)`; `LogEntry(Instant timestamp, String service, String level, String message, String correlationId)`; `RootCauseHypothesis(String summary, List<String> evidence, String confidence, List<String> suspectedFiles)`; `InvestigateRequest(@NotBlank String ticketId)`; `InvestigateResponse(RootCauseHypothesis rootCause, int iterations, List<StepRecord> steps, boolean truncated)`; `StepRecord(String tool, String input, String output)`. Consumed throughout Tasks 4–10.

No dedicated tests — pure data records with no behavior, verified indirectly through every later task's tests (matches this repo's convention for plain event/DTO records, e.g. `cqrs-event-sourcing/axon`'s event records).

- [ ] **Step 1: Create the domain records**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/Ticket.java`:

```java
package com.testingai.sdlc.ticket;

import java.time.Instant;

public record Ticket(String id, String title, String description, String severity, String service,
        Instant reportedAt) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/LogEntry.java`:

```java
package com.testingai.sdlc.log;

import java.time.Instant;

public record LogEntry(Instant timestamp, String service, String level, String message, String correlationId) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/RootCauseHypothesis.java`:

```java
package com.testingai.sdlc.model;

import java.util.List;

public record RootCauseHypothesis(String summary, List<String> evidence, String confidence,
        List<String> suspectedFiles) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/InvestigateRequest.java`:

```java
package com.testingai.sdlc.model;

import jakarta.validation.constraints.NotBlank;

public record InvestigateRequest(@NotBlank String ticketId) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/InvestigateResponse.java`:

```java
package com.testingai.sdlc.model;

import java.util.List;

public record InvestigateResponse(RootCauseHypothesis rootCause, int iterations, List<StepRecord> steps,
        boolean truncated) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/StepRecord.java`:

```java
package com.testingai.sdlc.model;

public record StepRecord(String tool, String input, String output) {
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/Ticket.java \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/LogEntry.java \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/
git commit -m "feat(sdlc-agent): add domain model records"
```

---

### Task 4: `TicketSource` interface and `JiraTicketSource`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/TicketSource.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/AdfTextExtractor.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/JiraTicketSource.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/AdfTextExtractorTest.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/JiraTicketSourceTest.java`

**Interfaces:**
- Consumes: `JiraProperties` (Task 2), `Ticket` (Task 3).
- Produces: `TicketSource.fetch(String ticketId): Ticket`, implemented by `JiraTicketSource` — active by default (`@ConditionalOnProperty(sdlc.ticket-source, havingValue="jira", matchIfMissing=true)`). Consumed by `InvestigateService` (Task 8).

- [ ] **Step 1: Create the `TicketSource` interface**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/TicketSource.java`:

```java
package com.testingai.sdlc.ticket;

public interface TicketSource {

    Ticket fetch(String ticketId);
}
```

- [ ] **Step 2: Write the failing test for `AdfTextExtractor`**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/AdfTextExtractorTest.java`:

```java
package com.testingai.sdlc.ticket;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdfTextExtractorTest {

    @Test
    void extractText_shouldFlattenSingleParagraph() {
        Object adf = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(
                                Map.of("type", "text", "text", "Checkout fails intermittently.")))));

        assertThat(AdfTextExtractor.extractText(adf)).contains("Checkout fails intermittently.");
    }

    @Test
    void extractText_shouldJoinMultipleParagraphs() {
        Object adf = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "First."))),
                        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "Second.")))));

        String result = AdfTextExtractor.extractText(adf);

        assertThat(result).contains("First.").contains("Second.");
    }

    @Test
    void extractText_shouldReturnEmptyStringForNull() {
        assertThat(AdfTextExtractor.extractText(null)).isEmpty();
    }

    @Test
    void extractText_shouldReturnPlainStringUnchanged() {
        assertThat(AdfTextExtractor.extractText("already plain text")).isEqualTo("already plain text");
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn test -Dtest=AdfTextExtractorTest`
Expected: COMPILATION FAILURE — `AdfTextExtractor` does not exist yet.

- [ ] **Step 4: Implement `AdfTextExtractor`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/AdfTextExtractor.java`:

```java
package com.testingai.sdlc.ticket;

import java.util.List;
import java.util.Map;

public final class AdfTextExtractor {

    private AdfTextExtractor() {
    }

    public static String extractText(Object node) {
        if (node == null) {
            return "";
        }
        if (node instanceof String text) {
            return text;
        }
        if (node instanceof Map<?, ?> map) {
            return extractFromMap(map);
        }
        if (node instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                sb.append(extractText(item));
            }
            return sb.toString();
        }
        return "";
    }

    private static String extractFromMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        if ("text".equals(map.get("type")) && map.get("text") instanceof String text) {
            sb.append(text);
        }
        Object content = map.get("content");
        if (content instanceof List<?> children) {
            for (Object child : children) {
                sb.append(extractText(child));
            }
            if (!children.isEmpty() && "paragraph".equals(map.get("type"))) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=AdfTextExtractorTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Write the failing test for `JiraTicketSource`**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/JiraTicketSourceTest.java`:

```java
package com.testingai.sdlc.ticket;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.sdlc.config.JiraProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraTicketSourceTest {

    private WireMockServer wireMock;
    private JiraTicketSource source;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        JiraProperties props = new JiraProperties("http://localhost:" + wireMock.port(), "user@example.com",
                "token", "customfield_10050");
        RestClient restClient = RestClient.builder().baseUrl(props.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(props.email(), props.apiToken())).build();
        source = new JiraTicketSource(restClient, props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetch_shouldMapJiraIssueToTicket() {
        stubFor(get(urlEqualTo("/rest/api/3/issue/DEMO-101")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "key": "DEMO-101",
                          "fields": {
                            "summary": "Checkout fails with 500 error for some orders",
                            "description": {
                              "type": "doc",
                              "content": [
                                {"type": "paragraph", "content": [
                                  {"type": "text", "text": "Intermittent failures reported."}
                                ]}
                              ]
                            },
                            "priority": {"name": "High"},
                            "created": "2026-07-10T14:00:00.000+0000",
                            "customfield_10050": "checkout-service"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("DEMO-101");

        assertThat(ticket.id()).isEqualTo("DEMO-101");
        assertThat(ticket.title()).isEqualTo("Checkout fails with 500 error for some orders");
        assertThat(ticket.description()).contains("Intermittent failures reported.");
        assertThat(ticket.severity()).isEqualTo("High");
        assertThat(ticket.service()).isEqualTo("checkout-service");
        assertThat(ticket.reportedAt()).isEqualTo(Instant.parse("2026-07-10T14:00:00Z"));
    }

    @Test
    void fetch_shouldDefaultServiceToUnknownWhenCustomFieldMissing() {
        stubFor(get(urlEqualTo("/rest/api/3/issue/DEMO-102")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "key": "DEMO-102",
                          "fields": {
                            "summary": "Some other bug",
                            "description": null,
                            "priority": {"name": "Low"},
                            "created": "2026-07-10T14:00:00.000+0000"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("DEMO-102");

        assertThat(ticket.service()).isEqualTo("unknown");
        assertThat(ticket.description()).isEmpty();
    }

    @Test
    void fetch_shouldThrowNotFoundWhenJiraReturns404() {
        stubFor(get(urlEqualTo("/rest/api/3/issue/MISSING-1")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> source.fetch("MISSING-1")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MISSING-1");
    }
}
```

- [ ] **Step 7: Run it to verify it fails**

Run: `mvn test -Dtest=JiraTicketSourceTest`
Expected: COMPILATION FAILURE — `JiraTicketSource` does not exist yet.

- [ ] **Step 8: Implement `JiraTicketSource`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/JiraTicketSource.java`:

```java
package com.testingai.sdlc.ticket;

import com.testingai.sdlc.config.JiraProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sdlc.ticket-source", havingValue = "jira", matchIfMissing = true)
public class JiraTicketSource implements TicketSource {

    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final RestClient restClient;
    private final JiraProperties jiraProperties;

    public JiraTicketSource(RestClient jiraRestClient, JiraProperties jiraProperties) {
        this.restClient = jiraRestClient;
        this.jiraProperties = jiraProperties;
    }

    @Override
    public Ticket fetch(String ticketId) {
        Map<String, Object> response = fetchIssue(ticketId);
        Map<String, Object> fields = castMap(response.get("fields"));
        Map<String, Object> priority = castMap(fields.get("priority"));

        Object serviceValue = fields.get(jiraProperties.serviceField());
        String service = serviceValue != null ? serviceValue.toString() : "unknown";
        String severity = priority.get("name") != null ? priority.get("name").toString() : "unknown";

        return new Ticket(String.valueOf(response.get("key")), String.valueOf(fields.get("summary")),
                AdfTextExtractor.extractText(fields.get("description")), severity, service,
                OffsetDateTime.parse(String.valueOf(fields.get("created")), JIRA_TIMESTAMP_FORMAT).toInstant());
    }

    private Map<String, Object> fetchIssue(String ticketId) {
        try {
            Map<String, Object> response = restClient.get().uri("/rest/api/3/issue/{key}", ticketId).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId);
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId, e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch Jira ticket: " + ticketId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `mvn test -Dtest=JiraTicketSourceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 10: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/ \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/
git commit -m "feat(sdlc-agent): add TicketSource interface and JiraTicketSource"
```

---

### Task 5: `ZendeskTicketSource`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/ZendeskTicketSource.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/ZendeskTicketSourceTest.java`

**Interfaces:**
- Consumes: `ZendeskProperties` (Task 2), `Ticket`, `TicketSource` (Task 4).
- Produces: second `TicketSource` implementation, active only when `sdlc.ticket-source=zendesk`.

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/ZendeskTicketSourceTest.java`:

```java
package com.testingai.sdlc.ticket;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.sdlc.config.ZendeskProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZendeskTicketSourceTest {

    private WireMockServer wireMock;
    private ZendeskTicketSource source;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        ZendeskProperties props = new ZendeskProperties("acme", "agent@example.com", "token", "");
        RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + wireMock.port())
                .defaultHeaders(headers -> headers.setBasicAuth(props.email() + "/token", props.apiToken())).build();
        source = new ZendeskTicketSource(restClient, props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetch_shouldMapZendeskTicketToTicket() {
        stubFor(get(urlEqualTo("/api/v2/tickets/1001.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "ticket": {
                            "id": 1001,
                            "subject": "Checkout fails with 500 error for some orders",
                            "description": "Intermittent failures reported.",
                            "priority": "high",
                            "tags": ["checkout-service", "bug"],
                            "created_at": "2026-07-10T14:00:00Z"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("1001");

        assertThat(ticket.id()).isEqualTo("1001");
        assertThat(ticket.title()).isEqualTo("Checkout fails with 500 error for some orders");
        assertThat(ticket.description()).isEqualTo("Intermittent failures reported.");
        assertThat(ticket.severity()).isEqualTo("high");
        assertThat(ticket.service()).isEqualTo("checkout-service");
        assertThat(ticket.reportedAt()).isEqualTo(Instant.parse("2026-07-10T14:00:00Z"));
    }

    @Test
    void fetch_shouldDefaultServiceToUnknownWhenNoTags() {
        stubFor(get(urlEqualTo("/api/v2/tickets/1002.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "ticket": {
                            "id": 1002,
                            "subject": "Some other bug",
                            "description": "N/A",
                            "priority": "low",
                            "tags": [],
                            "created_at": "2026-07-10T14:00:00Z"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("1002");

        assertThat(ticket.service()).isEqualTo("unknown");
    }

    @Test
    void fetch_shouldThrowNotFoundWhenZendeskReturns404() {
        stubFor(get(urlEqualTo("/api/v2/tickets/9999.json")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> source.fetch("9999")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("9999");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=ZendeskTicketSourceTest`
Expected: COMPILATION FAILURE — `ZendeskTicketSource` does not exist yet.

- [ ] **Step 3: Implement `ZendeskTicketSource`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/ZendeskTicketSource.java`:

```java
package com.testingai.sdlc.ticket;

import com.testingai.sdlc.config.ZendeskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sdlc.ticket-source", havingValue = "zendesk")
public class ZendeskTicketSource implements TicketSource {

    private final RestClient restClient;
    private final ZendeskProperties zendeskProperties;

    public ZendeskTicketSource(RestClient zendeskRestClient, ZendeskProperties zendeskProperties) {
        this.restClient = zendeskRestClient;
        this.zendeskProperties = zendeskProperties;
    }

    @Override
    public Ticket fetch(String ticketId) {
        Map<String, Object> response = fetchTicket(ticketId);
        Map<String, Object> ticket = castMap(response.get("ticket"));

        String priority = ticket.get("priority") != null ? ticket.get("priority").toString() : "unknown";
        String service = extractServiceTag((List<?>) ticket.get("tags"), zendeskProperties.serviceTagPrefix());

        return new Ticket(String.valueOf(ticket.get("id")), String.valueOf(ticket.get("subject")),
                String.valueOf(ticket.get("description")), priority, service,
                Instant.parse(String.valueOf(ticket.get("created_at"))));
    }

    private Map<String, Object> fetchTicket(String ticketId) {
        try {
            Map<String, Object> response = restClient.get().uri("/api/v2/tickets/{id}.json", ticketId).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId);
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId, e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch Zendesk ticket: " + ticketId,
                    e);
        }
    }

    private static String extractServiceTag(List<?> tags, String prefix) {
        if (tags == null) {
            return "unknown";
        }
        for (Object tag : tags) {
            String candidate = String.valueOf(tag);
            if (prefix == null || prefix.isBlank() || candidate.startsWith(prefix)) {
                return candidate;
            }
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=ZendeskTicketSourceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/ticket/ZendeskTicketSource.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/ticket/ZendeskTicketSourceTest.java
git commit -m "feat(sdlc-agent): add ZendeskTicketSource"
```

---

### Task 6: `LogSource` interface and `SplunkLogSource`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/LogSource.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/SplunkLogSource.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/log/SplunkLogSourceTest.java`

**Interfaces:**
- Consumes: `SplunkProperties` (Task 2), `LogEntry` (Task 3).
- Produces: `LogSource.query(String service, Instant from, Instant to, String keyword, String correlationId): List<LogEntry>`, implemented by `SplunkLogSource`. Consumed by `QueryLogsTool` (Task 7).

- [ ] **Step 1: Create the `LogSource` interface**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/LogSource.java`:

```java
package com.testingai.sdlc.log;

import java.time.Instant;
import java.util.List;

public interface LogSource {

    List<LogEntry> query(String service, Instant from, Instant to, String keyword, String correlationId);
}
```

- [ ] **Step 2: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/log/SplunkLogSourceTest.java`:

```java
package com.testingai.sdlc.log;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.sdlc.config.SplunkProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class SplunkLogSourceTest {

    private WireMockServer wireMock;
    private SplunkLogSource source;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        SplunkProperties props = new SplunkProperties("http://localhost:" + wireMock.port(), "token", 5);
        RestClient restClient = RestClient.builder().baseUrl(props.baseUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(props.apiToken())).build();
        source = new SplunkLogSource(restClient, props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void query_shouldCreateJobPollAndReturnResults() {
        stubFor(post(urlPathEqualTo("/services/search/jobs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\": \"12345\"}")));
        stubFor(get(urlPathEqualTo("/services/search/jobs/12345")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"entry\": [{\"content\": {\"dispatchState\": \"DONE\"}}]}")));
        stubFor(get(urlPathEqualTo("/services/search/jobs/12345/results")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "results": [
                            {
                              "_time": "2026-07-10T14:22:01Z",
                              "_raw": "java.lang.NullPointerException: Cannot invoke \\"String.length()\\" because \\"discountCode\\" is null",
                              "level": "ERROR",
                              "correlationId": "corr-abc"
                            }
                          ]
                        }
                        """)));

        List<LogEntry> entries = source.query("checkout-service", Instant.parse("2026-07-10T00:00:00Z"),
                Instant.parse("2026-07-11T00:00:00Z"), "NullPointerException", null);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().service()).isEqualTo("checkout-service");
        assertThat(entries.getFirst().level()).isEqualTo("ERROR");
        assertThat(entries.getFirst().message()).contains("NullPointerException");
        assertThat(entries.getFirst().correlationId()).isEqualTo("corr-abc");
    }

    @Test
    void query_shouldReturnEmptyListWhenJobNeverCompletes() {
        stubFor(post(urlPathEqualTo("/services/search/jobs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\": \"stuck-job\"}")));
        stubFor(get(urlPathEqualTo("/services/search/jobs/stuck-job")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"entry\": [{\"content\": {\"dispatchState\": \"RUNNING\"}}]}")));

        SplunkProperties fastTimeoutProps = new SplunkProperties("http://localhost:" + wireMock.port(), "token", 1);
        RestClient restClient = RestClient.builder().baseUrl(fastTimeoutProps.baseUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(fastTimeoutProps.apiToken())).build();
        SplunkLogSource fastTimeoutSource = new SplunkLogSource(restClient, fastTimeoutProps);

        List<LogEntry> entries = fastTimeoutSource.query("checkout-service", Instant.now(), Instant.now(), null,
                null);

        assertThat(entries).isEmpty();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn test -Dtest=SplunkLogSourceTest`
Expected: COMPILATION FAILURE — `SplunkLogSource` does not exist yet.

- [ ] **Step 4: Implement `SplunkLogSource`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/SplunkLogSource.java`:

```java
package com.testingai.sdlc.log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.testingai.sdlc.config.SplunkProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

@Component
public class SplunkLogSource implements LogSource {

    private static final long POLL_INTERVAL_MILLIS = 500;

    private final RestClient restClient;
    private final SplunkProperties splunkProperties;

    public SplunkLogSource(RestClient splunkRestClient, SplunkProperties splunkProperties) {
        this.restClient = splunkRestClient;
        this.splunkProperties = splunkProperties;
    }

    @Override
    public List<LogEntry> query(String service, Instant from, Instant to, String keyword, String correlationId) {
        String sid = createSearchJob(buildSearchString(service, keyword, correlationId), from, to);
        if (!waitForCompletion(sid)) {
            return List.of();
        }
        return fetchResults(sid, service);
    }

    private String buildSearchString(String service, String keyword, String correlationId) {
        StringBuilder search = new StringBuilder("search index=main service=\"").append(service).append('"');
        if (keyword != null && !keyword.isBlank()) {
            search.append(" \"").append(keyword).append('"');
        }
        if (correlationId != null && !correlationId.isBlank()) {
            search.append(" correlationId=\"").append(correlationId).append('"');
        }
        return search.toString();
    }

    private String createSearchJob(String search, Instant from, Instant to) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("search", search);
        form.add("earliest_time", from.toString());
        form.add("latest_time", to.toString());
        form.add("output_mode", "json");
        CreateJobResponse response = restClient.post().uri("/services/search/jobs")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                .body(CreateJobResponse.class);
        return response != null ? response.sid() : "";
    }

    private boolean waitForCompletion(String sid) {
        long deadline = System.currentTimeMillis() + splunkProperties.searchTimeoutSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            JobStatusResponse status = restClient.get().uri("/services/search/jobs/{sid}?output_mode=json", sid)
                    .retrieve().body(JobStatusResponse.class);
            if (status != null && !status.entry().isEmpty()
                    && "DONE".equals(status.entry().getFirst().content().dispatchState())) {
                return true;
            }
            sleep();
        }
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<LogEntry> fetchResults(String sid, String service) {
        ResultsResponse response = restClient.get().uri("/services/search/jobs/{sid}/results?output_mode=json", sid)
                .retrieve().body(ResultsResponse.class);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .map(r -> new LogEntry(Instant.parse(r.time()), service, r.level(), r.raw(), r.correlationId()))
                .toList();
    }

    record CreateJobResponse(String sid) {
    }

    record JobStatusResponse(List<JobEntry> entry) {
    }

    record JobEntry(JobContent content) {
    }

    record JobContent(String dispatchState) {
    }

    record ResultsResponse(List<SplunkResult> results) {
    }

    record SplunkResult(@JsonProperty("_time") String time, @JsonProperty("_raw") String raw, String level,
            String correlationId) {
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest=SplunkLogSourceTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/log/ \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/log/
git commit -m "feat(sdlc-agent): add LogSource interface and SplunkLogSource"
```

---

### Task 7: `QueryLogsTool` and `ToolExecutor`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/QueryLogsTool.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ToolExecutor.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/QueryLogsToolTest.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ToolExecutorTest.java`

**Interfaces:**
- Consumes: `LogSource` (Task 6).
- Produces: `QueryLogsTool.query(String service, String from, String to, String keyword, String correlationId): String` (JSON), `QueryLogsTool.definition(): Tool`; `ToolExecutor.execute(String toolName, JsonValue input): String`. Consumed by `InvestigateService` (Task 8).

- [ ] **Step 1: Write the failing test for `QueryLogsTool`**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/QueryLogsToolTest.java`:

```java
package com.testingai.sdlc.tool;

import com.testingai.sdlc.log.LogEntry;
import com.testingai.sdlc.log.LogSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryLogsToolTest {

    @Mock
    private LogSource logSource;

    private QueryLogsTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryLogsTool(logSource);
    }

    @Test
    void query_shouldSerializeLogEntriesAsJsonArray() {
        when(logSource.query(eq("checkout-service"), any(), any(), eq("NullPointerException"), eq(null)))
                .thenReturn(List.of(new LogEntry(Instant.parse("2026-07-10T14:22:01Z"), "checkout-service", "ERROR",
                        "NullPointerException: discountCode is null", "corr-abc")));

        String result = tool.query("checkout-service", "2026-07-10T00:00:00Z", "2026-07-11T00:00:00Z",
                "NullPointerException", null);

        assertThat(result).contains("checkout-service").contains("ERROR").contains("corr-abc");
    }

    @Test
    void query_shouldDefaultTimeWindowWhenFromToOmitted() {
        when(logSource.query(eq("checkout-service"), any(), any(), eq(null), eq(null))).thenReturn(List.of());

        String result = tool.query("checkout-service", null, null, null, null);

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void definition_hasCorrectNameAndRequiredField() {
        assertThat(tool.definition().name()).isEqualTo("query_logs");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=QueryLogsToolTest`
Expected: COMPILATION FAILURE — `QueryLogsTool` does not exist yet.

- [ ] **Step 3: Implement `QueryLogsTool`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/QueryLogsTool.java`:

```java
package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.sdlc.log.LogEntry;
import com.testingai.sdlc.log.LogSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class QueryLogsTool {

    private final LogSource logSource;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public QueryLogsTool(LogSource logSource) {
        this.logSource = logSource;
    }

    public String query(String service, String from, String to, String keyword, String correlationId) {
        try {
            Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(Duration.ofDays(1));
            Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
            List<LogEntry> entries = logSource.query(service, fromInstant, toInstant, keyword, correlationId);
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    public Tool definition() {
        return Tool.builder().name("query_logs")
                .description("Search production logs for a given service within a time window, optionally "
                        + "filtered by keyword or correlation ID. Call this multiple times to narrow down — "
                        + "e.g. a broad keyword search first, then a follow-up scoped to a correlationId found "
                        + "in a promising result.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("service", JsonValue.from(Map.of("type", "string",
                                        "description", "The service name to search logs for, e.g. checkout-service")))
                                .putAdditionalProperty("from", JsonValue.from(Map.of("type", "string", "description",
                                        "ISO-8601 start of the time window. Optional.")))
                                .putAdditionalProperty("to", JsonValue.from(Map.of("type", "string", "description",
                                        "ISO-8601 end of the time window. Optional.")))
                                .putAdditionalProperty("keyword", JsonValue.from(Map.of("type", "string",
                                        "description", "Free-text keyword, e.g. an exception class name. Optional.")))
                                .putAdditionalProperty("correlationId", JsonValue.from(Map.of("type", "string",
                                        "description", "A specific correlation/trace ID to fetch related entries for. Optional.")))
                                .build())
                        .required(List.of("service")).putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=QueryLogsToolTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Write the failing test for `ToolExecutor`**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ToolExecutorTest.java`:

```java
package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock
    private QueryLogsTool queryLogsTool;

    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        toolExecutor = new ToolExecutor(queryLogsTool);
    }

    @Test
    void execute_shouldDispatchQueryLogsWithAllFields() {
        when(queryLogsTool.query("checkout-service", "2026-07-10T00:00:00Z", null, "NullPointerException", null))
                .thenReturn("[]");

        String result = toolExecutor.execute("query_logs", JsonValue.from(Map.of("service", "checkout-service",
                "from", "2026-07-10T00:00:00Z", "keyword", "NullPointerException")));

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void execute_shouldReturnErrorWhenServiceFieldMissing() {
        String result = toolExecutor.execute("query_logs", JsonValue.from(Map.of("keyword", "test")));

        assertThat(result).contains("error").contains("service");
    }

    @Test
    void execute_shouldReturnErrorForUnknownTool() {
        String result = toolExecutor.execute("unknown_tool", JsonValue.from(Map.of()));

        assertThat(result).contains("error").contains("unknown_tool");
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `mvn test -Dtest=ToolExecutorTest`
Expected: COMPILATION FAILURE — `ToolExecutor` does not exist yet.

- [ ] **Step 7: Implement `ToolExecutor`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ToolExecutor.java`:

```java
package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolExecutor {

    private final QueryLogsTool queryLogsTool;

    public ToolExecutor(QueryLogsTool queryLogsTool) {
        this.queryLogsTool = queryLogsTool;
    }

    public String execute(String toolName, JsonValue input) {
        try {
            Map<String, Object> fields = input.convert(new TypeReference<Map<String, Object>>() {
            });
            if (fields == null) {
                return "{\"error\": \"Tool input must be a JSON object\"}";
            }
            return switch (toolName) {
                case "query_logs" -> executeQueryLogs(fields);
                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\": \"ToolExecutor error: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String executeQueryLogs(Map<String, Object> fields) {
        Object service = fields.get("service");
        if (service == null) {
            return "{\"error\": \"query_logs: missing required field 'service'\"}";
        }
        String from = stringOrNull(fields.get("from"));
        String to = stringOrNull(fields.get("to"));
        String keyword = stringOrNull(fields.get("keyword"));
        String correlationId = stringOrNull(fields.get("correlationId"));
        return queryLogsTool.query(service.toString(), from, to, keyword, correlationId);
    }

    private String stringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `mvn test -Dtest=ToolExecutorTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/
git commit -m "feat(sdlc-agent): add QueryLogsTool and ToolExecutor"
```

---

### Task 8: `InvestigateService` — the agentic loop

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigateService.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigateServiceTest.java`

**Interfaces:**
- Consumes: `AnthropicClient` (Task 2), `TicketSource` (Tasks 4–5), `ToolExecutor`, `QueryLogsTool` (Task 7), `AgentProperties`, `AnthropicProperties` (Task 2), `RootCauseHypothesis`, `InvestigateResponse`, `StepRecord` (Task 3).
- Produces: `InvestigateService.investigate(String ticketId): InvestigateResponse`. Consumed by `InvestigateController` (Task 9).

- [ ] **Step 1: Write the failing tests**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigateServiceTest.java`:

```java
package com.testingai.sdlc.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import com.testingai.sdlc.tool.QueryLogsTool;
import com.testingai.sdlc.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigateServiceTest {

    private static final Ticket TICKET = new Ticket("DEMO-101", "Checkout fails with 500 error for some orders",
            "Intermittent failures reported.", "High", "checkout-service", Instant.parse("2026-07-10T10:00:00Z"));

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock
    private TicketSource ticketSource;
    @Mock
    private ToolExecutor toolExecutor;
    @Mock
    private QueryLogsTool queryLogsTool;

    private InvestigateService investigateService;

    @BeforeEach
    void setUp() {
        Tool stubTool = Tool.builder().name("query_logs").inputSchema(Tool.InputSchema.builder().build()).build();
        when(queryLogsTool.definition()).thenReturn(stubTool);
        when(ticketSource.fetch("DEMO-101")).thenReturn(TICKET);
        investigateService = new InvestigateService(anthropic, ticketSource, toolExecutor, queryLogsTool,
                new AgentProperties(10), new AnthropicProperties("test-key", "claude-sonnet-4-6"));
    }

    @Test
    void investigate_singleIteration_returnsParsedHypothesis() {
        String json = """
                {"summary": "NPE in DiscountService", "evidence": ["line1"], "confidence": "high", "suspectedFiles": ["DiscountService.java"]}
                """;
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(buildTextMessage(json));

        InvestigateResponse result = investigateService.investigate("DEMO-101");

        assertThat(result.rootCause().summary()).isEqualTo("NPE in DiscountService");
        assertThat(result.rootCause().confidence()).isEqualTo("high");
        assertThat(result.rootCause().suspectedFiles()).containsExactly("DiscountService.java");
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
        verify(ticketSource).fetch("DEMO-101");
    }

    @Test
    void investigate_multiIteration_executesQueryLogsThenReturnsHypothesis() {
        Message toolCallResponse = buildToolUseMessage("tool_1", "query_logs",
                JsonValue.from(Map.of("service", "checkout-service", "keyword", "NullPointerException")));
        String json = """
                {"summary": "NPE", "evidence": [], "confidence": "medium", "suspectedFiles": []}
                """;
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(toolCallResponse)
                .thenReturn(buildTextMessage(json));
        when(toolExecutor.execute(eq("query_logs"), any())).thenReturn("[]");

        InvestigateResponse result = investigateService.investigate("DEMO-101");

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().tool()).isEqualTo("query_logs");
        assertThat(result.iterations()).isEqualTo(2);
    }

    @Test
    void investigate_truncatesWhenIterationCapReached() {
        Message loopingToolCall = buildToolUseMessage("tool_loop", "query_logs",
                JsonValue.from(Map.of("service", "checkout-service")));
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(loopingToolCall);
        when(toolExecutor.execute(any(), any())).thenReturn("[]");
        investigateService = new InvestigateService(anthropic, ticketSource, toolExecutor, queryLogsTool,
                new AgentProperties(2), new AnthropicProperties("test-key", "claude-sonnet-4-6"));

        InvestigateResponse result = investigateService.investigate("DEMO-101");

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.rootCause().confidence()).isEqualTo("low");
    }

    @Test
    void investigate_fallsBackToLowConfidenceWhenFinalTextIsNotValidJson() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("I couldn't determine a root cause."));

        InvestigateResponse result = investigateService.investigate("DEMO-101");

        assertThat(result.rootCause().confidence()).isEqualTo("low");
        assertThat(result.rootCause().summary()).contains("couldn't determine");
    }

    @Test
    void investigate_stripsMarkdownCodeFenceBeforeParsing() {
        String fenced = """
                ```json
                {"summary": "NPE", "evidence": [], "confidence": "high", "suspectedFiles": []}
                ```
                """;
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(buildTextMessage(fenced));

        InvestigateResponse result = investigateService.investigate("DEMO-101");

        assertThat(result.rootCause().summary()).isEqualTo("NPE");
        assertThat(result.rootCause().confidence()).isEqualTo("high");
    }

    // --- helpers (mirrors AgentServiceTest in ai/task-automation-agent) ---

    private Message buildTextMessage(String text) {
        TextBlock textBlock = TextBlock.builder().citations(Optional.empty()).text(text).build();
        return buildMessage(List.of(ContentBlock.ofText(textBlock)));
    }

    private Message buildToolUseMessage(String id, String name, JsonValue input) {
        ToolUseBlock toolUse = ToolUseBlock.builder().id(id).caller(DirectCaller.builder().build()).input(input)
                .name(name).build();
        return buildMessage(List.of(ContentBlock.ofToolUse(toolUse)));
    }

    private Message buildMessage(List<ContentBlock> blocks) {
        Usage usage = Usage.builder().cacheCreation(Optional.empty()).cacheCreationInputTokens(Optional.empty())
                .cacheReadInputTokens(Optional.empty()).inferenceGeo(Optional.empty()).inputTokens(0L)
                .outputTokens(0L).outputTokensDetails(Optional.empty()).serverToolUse(Optional.empty())
                .serviceTier(Optional.empty()).build();
        return Message.builder().id("msg_test").content(blocks).model("claude-sonnet-4-6")
                .stopDetails(Optional.empty()).stopReason(Optional.empty()).stopSequence(Optional.empty())
                .usage(usage).build();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=InvestigateServiceTest`
Expected: COMPILATION FAILURE — `InvestigateService` does not exist yet.

- [ ] **Step 3: Implement `InvestigateService`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigateService.java`:

```java
package com.testingai.sdlc.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.model.StepRecord;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import com.testingai.sdlc.tool.QueryLogsTool;
import com.testingai.sdlc.tool.ToolExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InvestigateService {

    private static final int MAX_TOKENS = 4096;
    private static final String INSTRUCTIONS = """
            You are investigating a production support ticket. Use the query_logs tool to search \
            production logs for evidence related to the ticket. You may call query_logs multiple \
            times - for example, a broad keyword search first, then a follow-up scoped to a \
            correlationId you spot in a promising result.

            Once you have enough evidence, respond with ONLY a JSON object (no other text, no \
            markdown code fences) matching this exact shape:
            {"summary": "...", "evidence": ["...matching log lines..."], "confidence": "high|medium|low", "suspectedFiles": ["..."]}""";

    private final AnthropicClient anthropic;
    private final TicketSource ticketSource;
    private final ToolExecutor toolExecutor;
    private final QueryLogsTool queryLogsTool;
    private final AgentProperties agentProperties;
    private final AnthropicProperties anthropicProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvestigateService(AnthropicClient anthropic, TicketSource ticketSource, ToolExecutor toolExecutor,
            QueryLogsTool queryLogsTool, AgentProperties agentProperties, AnthropicProperties anthropicProperties) {
        this.anthropic = anthropic;
        this.ticketSource = ticketSource;
        this.toolExecutor = toolExecutor;
        this.queryLogsTool = queryLogsTool;
        this.agentProperties = agentProperties;
        this.anthropicProperties = anthropicProperties;
    }

    public InvestigateResponse investigate(String ticketId) {
        Ticket ticket = ticketSource.fetch(ticketId);

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(buildInitialPrompt(ticket))
                .build());

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProperties.maxIterations()) {
            Message response = anthropic.messages().create(MessageCreateParams.builder()
                    .model(anthropicProperties.model()).maxTokens(MAX_TOKENS).messages(messages)
                    .addTool(queryLogsTool.definition()).toolChoice(ToolChoiceAuto.builder().build()).build());

            List<ContentBlockParam> assistantBlocks = response.content().stream().map(ContentBlock::toParam)
                    .filter(Objects::nonNull).toList();
            messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantBlocks).build());

            iterations++;

            List<ToolUseBlock> toolCalls = response.content().stream().filter(ContentBlock::isToolUse)
                    .map(ContentBlock::asToolUse).toList();

            if (toolCalls.isEmpty()) {
                String text = response.content().stream().filter(ContentBlock::isText).map(ContentBlock::asText)
                        .map(TextBlock::text).collect(Collectors.joining(""));
                return new InvestigateResponse(parseRootCause(text), iterations, steps, false);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ToolUseBlock call : toolCalls) {
                String output = toolExecutor.execute(call.name(), call._input());
                steps.add(new StepRecord(call.name(), call._input().toString(), output));
                toolResults.add(ContentBlockParam
                        .ofToolResult(ToolResultBlockParam.builder().toolUseId(call.id()).content(output).build()));
            }
            messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(toolResults)
                    .build());
        }

        return new InvestigateResponse(truncatedHypothesis(), iterations, steps, true);
    }

    private String buildInitialPrompt(Ticket ticket) {
        return INSTRUCTIONS + "\n\nTicket " + ticket.id() + " (" + ticket.service() + ", severity "
                + ticket.severity() + ", reported " + ticket.reportedAt() + "): " + ticket.title() + "\n"
                + ticket.description();
    }

    private RootCauseHypothesis parseRootCause(String text) {
        try {
            return objectMapper.readValue(stripCodeFence(text), RootCauseHypothesis.class);
        } catch (JsonProcessingException e) {
            return new RootCauseHypothesis(text, List.of(), "low", List.of());
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline == -1 || lastFence <= firstNewline) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, lastFence).trim();
    }

    private RootCauseHypothesis truncatedHypothesis() {
        return new RootCauseHypothesis("Investigation truncated: iteration limit reached before a conclusion.",
                List.of(), "low", List.of());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=InvestigateServiceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/ \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/
git commit -m "feat(sdlc-agent): add InvestigateService agentic loop"
```

---

### Task 9: `InvestigateController`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/controller/InvestigateController.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/controller/InvestigateControllerTest.java`

**Interfaces:**
- Consumes: `InvestigateService` (Task 8), `InvestigateRequest`, `InvestigateResponse` (Task 3).
- Produces: `POST /api/sdlc/investigate`.

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/controller/InvestigateControllerTest.java`:

```java
package com.testingai.sdlc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.model.InvestigateRequest;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.service.InvestigateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestigateController.class)
class InvestigateControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private InvestigateService investigateService;

    @Test
    void investigate_returnsInvestigateResponseAsJson() throws Exception {
        RootCauseHypothesis hypothesis = new RootCauseHypothesis("NPE in DiscountService", List.of("line1"), "high",
                List.of("DiscountService.java"));
        InvestigateResponse expected = new InvestigateResponse(hypothesis, 2, List.of(), false);
        when(investigateService.investigate("DEMO-101")).thenReturn(expected);

        mockMvc.perform(post("/api/sdlc/investigate").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InvestigateRequest("DEMO-101"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rootCause.summary").value("NPE in DiscountService"))
                .andExpect(jsonPath("$.rootCause.confidence").value("high")).andExpect(jsonPath("$.iterations").value(2))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void investigate_returns400WhenTicketIdIsBlank() throws Exception {
        mockMvc.perform(post("/api/sdlc/investigate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketId\": \"\"}")).andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=InvestigateControllerTest`
Expected: COMPILATION FAILURE — `InvestigateController` does not exist yet.

- [ ] **Step 3: Implement `InvestigateController`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/controller/InvestigateController.java`:

```java
package com.testingai.sdlc.controller;

import com.testingai.sdlc.model.InvestigateRequest;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.service.InvestigateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sdlc")
public class InvestigateController {

    private final InvestigateService investigateService;

    public InvestigateController(InvestigateService investigateService) {
        this.investigateService = investigateService;
    }

    @PostMapping("/investigate")
    public InvestigateResponse investigate(@RequestBody @Valid InvestigateRequest request) {
        return investigateService.investigate(request.ticketId());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=InvestigateControllerTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full unit test suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all prior test classes still passing.

- [ ] **Step 6: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/controller/ \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/controller/
git commit -m "feat(sdlc-agent): add InvestigateController"
```

---

### Task 10: Integration test

**Files:**
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/integration/SdlcAgentIntegrationTest.java`

**Interfaces:**
- Consumes: the full running app (real `ANTHROPIC_API_KEY`, `JIRA_API_TOKEN`/`ZENDESK_API_TOKEN`, `SPLUNK_API_TOKEN`, a running Splunk with the seeded `DEMO-101`/ticket-`1001` scenario from Task 11). Not run by default.

- [ ] **Step 1: Write the integration test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/integration/SdlcAgentIntegrationTest.java`:

```java
package com.testingai.sdlc.integration;

import com.testingai.sdlc.model.InvestigateResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SdlcAgentIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate http = new TestRestTemplate();

    @Test
    void investigate_withRealApis_returnsNonBlankRootCause() {
        var request = Map.of("ticketId", "DEMO-101");

        ResponseEntity<InvestigateResponse> response = http
                .postForEntity("http://localhost:" + port + "/api/sdlc/investigate", request,
                        InvestigateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rootCause().summary()).isNotBlank();
    }
}
```

- [ ] **Step 2: Verify it's excluded from the regular test run**

Run: `mvn test`
Expected: `BUILD SUCCESS`, surefire report does not mention `SdlcAgentIntegrationTest` (excluded by `<excludedGroups>integration</excludedGroups>` from Task 1's pom).

- [ ] **Step 3: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/integration/
git commit -m "test(sdlc-agent): add integration test for the full investigate path"
```

---

### Task 11: Splunk Docker infrastructure and log seeding

**Files:**
- Create: `ai/sdlc-agent/docker/docker-compose.yml`
- Create: `ai/sdlc-agent/docker/seed-logs.sh`

**Interfaces:**
- Produces: a running Splunk instance (web UI `:8000`, HEC `:8092`, REST API `:8093`) seeded with the `checkout-service` `NullPointerException` scenario — required for the manual smoke test (Task 14) and the integration test (Task 10), not for any unit test task.

- [ ] **Step 1: Write the compose file**

`ai/sdlc-agent/docker/docker-compose.yml`:

```yaml
name: sdlc-agent-splunk

services:
  splunk:
    image: splunk/splunk:latest
    hostname: splunk
    environment:
      - SPLUNK_START_ARGS=--accept-license
      - SPLUNK_PASSWORD=changeme123
      - SPLUNK_HEC_TOKEN=00000000-0000-0000-0000-000000000000
    ports:
      - "8000:8000"
      - "8092:8088"
      - "8093:8089"
    volumes:
      - splunk-var:/opt/splunk/var

volumes:
  splunk-var:
```

- [ ] **Step 2: Write the log-seeding script**

`ai/sdlc-agent/docker/seed-logs.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

HEC_URL="https://localhost:8092/services/collector/event"
HEC_TOKEN="00000000-0000-0000-0000-000000000000"
CORRELATION_ID="corr-abc-123"

send_event() {
    local level="$1"
    local message="$2"
    local correlation_id="${3:-}"
    curl -sk "$HEC_URL" \
        -H "Authorization: Splunk $HEC_TOKEN" \
        -d "{\"event\": {\"service\": \"checkout-service\", \"level\": \"$level\", \"message\": \"$message\", \"correlationId\": \"$correlation_id\"}}" \
        > /dev/null
}

echo "Seeding checkout-service logs into Splunk..."

for i in $(seq 1 40); do
    send_event "INFO" "Checkout completed successfully for order $i" "corr-ok-$i"
done

send_event "ERROR" "java.lang.NullPointerException: Cannot invoke \\\"String.length()\\\" because \\\"discountCode\\\" is null at com.example.checkout.DiscountService.apply(DiscountService.java:42)" "$CORRELATION_ID"
send_event "ERROR" "at com.example.checkout.CheckoutController.checkout(CheckoutController.java:58)" "$CORRELATION_ID"
send_event "INFO" "Checkout request received for order 9001" "$CORRELATION_ID"

for i in $(seq 1 5); do
    send_event "INFO" "Health check OK"
done

echo "Done. Seeded ~50 events for checkout-service."
```

- [ ] **Step 3: Make the seed script executable**

Run: `chmod +x ai/sdlc-agent/docker/seed-logs.sh`

- [ ] **Step 4: Start Splunk and verify it's reachable**

Run: `docker compose -f ai/sdlc-agent/docker/docker-compose.yml up -d`
Wait ~30s for first-boot initialization, then run: `curl -sk -u admin:changeme123 https://localhost:8093/services/server/info?output_mode=json`
Expected: HTTP 200 with a JSON body describing the Splunk server (confirms the REST API on `:8093` is up).

- [ ] **Step 5: Seed the logs**

Run: `./ai/sdlc-agent/docker/seed-logs.sh`
Expected: `Done. Seeded ~50 events for checkout-service.` printed, no curl errors.

- [ ] **Step 6: Stop the stack**

Run: `docker compose -f ai/sdlc-agent/docker/docker-compose.yml down`

- [ ] **Step 7: Commit**

```bash
git add ai/sdlc-agent/docker/
git commit -m "feat(sdlc-agent): add Splunk docker-compose infrastructure and log seeding"
```

---

### Task 12: Module README

**Files:**
- Create: `ai/sdlc-agent/spring-demo/README.md`

- [ ] **Step 1: Write the README**

`ai/sdlc-agent/spring-demo/README.md`:

```markdown
# SDLC Agent — Phase 1 (Intake + Investigate)

A Spring Boot app implementing Phase 1 of the [SDLC agent concept](../README.md): fetches a support ticket from Jira or Zendesk, agentically queries a real Splunk instance for correlated log evidence via a `query_logs` tool, and returns a structured root-cause hypothesis. Read-only — no file writes, git operations, deploy, or release.

## Prerequisites

| What | Where to get it |
|---|---|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `JIRA_API_TOKEN` (if `sdlc.ticket-source=jira`) | https://id.atlassian.com/manage-profile/security/api-tokens |
| `ZENDESK_API_TOKEN` (if `sdlc.ticket-source=zendesk`) | Zendesk Admin Center → Apps and integrations → APIs |
| `SPLUNK_API_TOKEN` | Generated locally — see below |

## Start Splunk and seed sample logs

```bash
docker compose -f ../docker/docker-compose.yml up -d
# wait ~30s for first-boot initialization
../docker/seed-logs.sh
```

Generate a Splunk auth token for `SplunkLogSource` (one-time, via the REST API):

```bash
curl -sk -u admin:changeme123 -X POST https://localhost:8093/services/authorization/tokens \
  -d name=admin -d audience=sdlc-agent | grep -o '<s:key[^<]*</s:key>'
```

Use the token value as `SPLUNK_API_TOKEN` below.

## Running

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export JIRA_BASE_URL=https://yourorg.atlassian.net
export JIRA_EMAIL=you@example.com
export JIRA_API_TOKEN=...
export SPLUNK_API_TOKEN=...

mvn spring-boot:run
```

App starts on **port 8089**.

## Try it

```bash
curl -s -X POST http://localhost:8089/api/sdlc/investigate \
  -H "Content-Type: application/json" \
  -d '{"ticketId": "DEMO-101"}' | jq .
```

Example response:

```json
{
  "rootCause": {
    "summary": "NullPointerException in DiscountService.apply when discountCode is null",
    "evidence": ["2026-07-10T14:22:01Z ERROR checkout-service: java.lang.NullPointerException: ..."],
    "confidence": "high",
    "suspectedFiles": ["DiscountService.java", "CheckoutController.java"]
  },
  "iterations": 3,
  "steps": [
    {"tool": "query_logs", "input": "{service=checkout-service, keyword=NullPointerException}", "output": "[...]"}
  ],
  "truncated": false
}
```

Swagger UI: not included in this module (matches `ai/task-automation-agent`).

## Build & test

```bash
mvn clean package          # build
mvn test                   # unit tests, no API keys needed (Jira/Zendesk/Splunk mocked with WireMock)
```

To run the integration test (requires real API keys, a running seeded Splunk, and a real ticket):

```bash
mvn test -Dtest=SdlcAgentIntegrationTest -Dgroups=integration
```

## Configuration

All defaults are in `src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `sdlc.ticket-source` | `jira` | `jira` or `zendesk` — selects which `TicketSource` bean is active |
| `agent.max-iterations` | `10` | Maximum loop iterations before truncation |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model to use |
| `jira.service-field` | `customfield_10050` | Jira custom field mapped to `Ticket.service` |
| `zendesk.service-tag-prefix` | `""` | Only tags with this prefix are considered the service tag (empty = first tag) |
| `splunk.base-url` | `https://localhost:8093` | Splunk REST API base URL |
| `splunk.search-timeout-seconds` | `10` | How long to poll a search job before giving up (returns empty results) |

## Module layout

```
spring-demo/src/main/java/com/testingai/sdlc/
├── SdlcAgentApplication.java
├── config/          AppConfig, SdlcProperties, AgentProperties, AnthropicProperties, JiraProperties, ZendeskProperties, SplunkProperties
├── controller/      InvestigateController — POST /api/sdlc/investigate
├── service/         InvestigateService — agentic loop
├── ticket/          TicketSource, Ticket, JiraTicketSource, ZendeskTicketSource, AdfTextExtractor
├── log/             LogSource, LogEntry, SplunkLogSource
├── tool/            ToolExecutor, QueryLogsTool
└── model/           InvestigateRequest, InvestigateResponse, RootCauseHypothesis, StepRecord
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.40.1
- Jira REST API v3, Zendesk Support API, Splunk REST API — real external integrations, no mocks in production code
- [WireMock](https://wiremock.org) — HTTP mocking in unit tests

## Scope

Phase 1 only: intake + investigate, read-only. Fix (propose + commit a patch), Deploy, Verify, and Release remain future phases — see [`../README.md`](../README.md).
```

- [ ] **Step 2: Commit**

```bash
git add ai/sdlc-agent/spring-demo/README.md
git commit -m "docs(sdlc-agent): add spring-demo module README"
```

---

### Task 13: Link Phase 1 in the concept doc

**Files:**
- Modify: `ai/sdlc-agent/README.md`

**Interfaces:**
- No code — updates the existing concept doc's phased-build-plan table to link to the now-existing `spring-demo/` module.

- [ ] **Step 1: Update the phased build plan table**

In `ai/sdlc-agent/README.md`, change:

```markdown
| **Phase 1 — buildable now** | Intake + Investigate | Read-only, no side effects (fetch ticket, query logs, produce `RootCauseHypothesis` JSON). Closest in risk profile to `ai/task-automation-agent` — the natural next concrete module to actually implement. |
```

to:

```markdown
| **Phase 1 — implemented** | Intake + Investigate | Read-only, no side effects. See [`spring-demo/`](spring-demo/) for the working implementation. |
```

- [ ] **Step 2: Commit**

```bash
git add ai/sdlc-agent/README.md
git commit -m "docs(sdlc-agent): link Phase 1 implementation from the concept doc"
```

---

### Task 14: Final build verification

**Files:** none (verification only)

- [ ] **Step 1: Full module build**

Run: `cd ai/sdlc-agent/spring-demo && mvn clean package`
Expected: `BUILD SUCCESS`, all unit tests pass, `SdlcAgentIntegrationTest` excluded.

- [ ] **Step 2: Verify `AppConfig`'s fail-fast validation without credentials**

Run: `unset ANTHROPIC_API_KEY JIRA_API_TOKEN ZENDESK_API_TOKEN SPLUNK_API_TOKEN; timeout 15 mvn spring-boot:run 2>&1 | grep -i "environment variable is not set"`
Expected: output containing `ANTHROPIC_API_KEY environment variable is not set` (or whichever required variable is checked first) and the app exits rather than starting — confirms `AppConfig.validateApiKeys()` (Task 2) works as designed.

- [ ] **Step 3: Verify test count and no stray `@Tag("integration")` leakage**

Run: `mvn test | grep -E "Tests run|BUILD"`
Expected: `BUILD SUCCESS`; total tests across all classes matches the sum from Tasks 1–9 (`AdfTextExtractorTest` 4, `JiraTicketSourceTest` 3, `ZendeskTicketSourceTest` 3, `SplunkLogSourceTest` 2, `QueryLogsToolTest` 3, `ToolExecutorTest` 3, `InvestigateServiceTest` 5, `InvestigateControllerTest` 2, `SdlcAgentApplicationTest` 1 — `SdlcAgentIntegrationTest` not included).

- [ ] **Step 4: Report completion**

No further action — this module is complete for Phase 1's scope. Deploy/Verify/Release remain out of scope per the design spec, to be planned separately.


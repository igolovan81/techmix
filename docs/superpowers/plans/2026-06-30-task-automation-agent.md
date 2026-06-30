# Task Automation Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot demo app with a single REST endpoint that accepts a natural-language goal and uses Claude + two tools (web search, page fetch) to research and summarise the answer autonomously.

**Architecture:** `AgentController` receives a goal, delegates to `AgentService`, which runs an agentic loop: call Claude with tool definitions → if `tool_use` blocks are returned, execute via `ToolExecutor` (`WebSearchTool` / `FetchPageTool`) → feed results back as `tool_result` blocks → repeat until `end_turn` or iteration cap.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Anthropic Java SDK 2.40.1, Jsoup 1.18.1, Spring `RestClient` (built-in), WireMock 3.5.4, JUnit 5 / Mockito (via `spring-boot-starter-test`)

## Global Constraints

- Java 21; Spring Boot 3.4.4; package root `com.testingai.agent`
- App port: 8084 (all lower ports taken by existing modules)
- Anthropic model: `claude-sonnet-4-6`
- Max iterations default: 10; fetch-page max chars default: 4 000 — both configurable
- Maven surefire **excludes** `@Tag("integration")` tests — run those manually only
- Lombok available but not required (records cover all model classes)
- No JPA, no Liquibase, no database — this is a stateless AI demo

---

### Task 1: Module scaffold — pom.xml, app entry point, config records, model records, application.yml

**Files:**
- Create: `ai/task-automation-agent/spring-demo/pom.xml`
- Create: `ai/task-automation-agent/spring-demo/src/main/resources/application.yml`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/AgentApplication.java`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/AgentProperties.java`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/AnthropicProperties.java`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/TavilyProperties.java`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/model/AgentRequest.java`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/model/AgentResponse.java`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/model/StepRecord.java`

**Interfaces:**
- Produces: `AgentRequest`, `AgentResponse`, `StepRecord` records; `AgentProperties`, `AnthropicProperties`, `TavilyProperties` — consumed by all later tasks

- [ ] **Step 1: Create directory tree**

```bash
mkdir -p ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/{config,controller,service,tool,model}
mkdir -p ai/task-automation-agent/spring-demo/src/main/resources
mkdir -p ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/{controller,service,tool,integration}
```

- [ ] **Step 2: Create pom.xml**

`ai/task-automation-agent/spring-demo/pom.xml`:
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
    <artifactId>agent-spring-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>task-automation-agent</name>

    <properties>
        <java.version>21</java.version>
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
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.18.1</version>
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
            <artifactId>wiremock</artifactId>
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

- [ ] **Step 3: Create application.yml**

`ai/task-automation-agent/spring-demo/src/main/resources/application.yml`:
```yaml
server:
  port: 8084

agent:
  max-iterations: 10
  fetch-page-max-chars: 4000

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

tavily:
  api-key: ${TAVILY_API_KEY}
  base-url: https://api.tavily.com
```

- [ ] **Step 4: Create AgentApplication.java**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/AgentApplication.java`:
```java
package com.testingai.agent;

import com.testingai.agent.config.AgentProperties;
import com.testingai.agent.config.AnthropicProperties;
import com.testingai.agent.config.TavilyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, AnthropicProperties.class, TavilyProperties.class})
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
```

- [ ] **Step 5: Create config property records**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/AgentProperties.java`:
```java
package com.testingai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(int maxIterations, int fetchPageMaxChars) {}
```

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/AnthropicProperties.java`:
```java
package com.testingai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(String apiKey, String model) {}
```

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/TavilyProperties.java`:
```java
package com.testingai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tavily")
public record TavilyProperties(String apiKey, String baseUrl) {}
```

- [ ] **Step 6: Create model records**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/model/StepRecord.java`:
```java
package com.testingai.agent.model;

public record StepRecord(String tool, String input, String output) {}
```

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/model/AgentRequest.java`:
```java
package com.testingai.agent.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(@NotBlank String goal) {}
```

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/model/AgentResponse.java`:
```java
package com.testingai.agent.model;

import java.util.List;

public record AgentResponse(
    String answer,
    List<StepRecord> steps,
    int iterations,
    boolean truncated
) {}
```

- [ ] **Step 7: Verify compile**

```bash
cd ai/task-automation-agent/spring-demo
mvn clean compile -q
```
Expected: `BUILD SUCCESS`. If `anthropic-java 2.40.1` is not yet in Maven Central, check https://central.sonatype.com/artifact/com.anthropic/anthropic-java for the latest available version and update `pom.xml`.

- [ ] **Step 8: Commit**

```bash
git add ai/task-automation-agent/spring-demo/
git commit -m "feat(ai-agent): scaffold task automation agent module"
```

---

### Task 2: AppConfig — Spring beans for Anthropic client, RestClient, HttpClient; startup key validation

**Files:**
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/AppConfig.java`

**Interfaces:**
- Consumes: `AnthropicProperties`, `TavilyProperties` from Task 1
- Produces:
  - `AnthropicClient` bean — injected into `AgentService` (Task 5)
  - `RestClient` bean named `tavilyRestClient` — injected into `WebSearchTool` (Task 3)
  - `HttpClient` bean — injected into `FetchPageTool` (Task 4)

- [ ] **Step 1: Create AppConfig.java**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/AppConfig.java`:
```java
package com.testingai.agent.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class AppConfig {

    private final AnthropicProperties anthropic;
    private final TavilyProperties tavily;

    public AppConfig(AnthropicProperties anthropic, TavilyProperties tavily) {
        this.anthropic = anthropic;
        this.tavily = tavily;
    }

    @PostConstruct
    public void validateApiKeys() {
        if (!StringUtils.hasText(anthropic.apiKey())) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }
        if (!StringUtils.hasText(tavily.apiKey())) {
            throw new IllegalStateException("TAVILY_API_KEY environment variable is not set");
        }
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropic.apiKey())
                .build();
    }

    @Bean
    public RestClient tavilyRestClient() {
        return RestClient.builder()
                .baseUrl(tavily.baseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
cd ai/task-automation-agent/spring-demo
mvn clean compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/config/AppConfig.java
git commit -m "feat(ai-agent): add Spring beans and startup API key validation"
```

---

### Task 3: WebSearchTool — Tavily search, Tool definition, WireMock tests

**Files:**
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/WebSearchTool.java`
- Create: `ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/tool/WebSearchToolTest.java`

**Interfaces:**
- Consumes: `RestClient` bean (`tavilyRestClient`) from Task 2; `TavilyProperties` from Task 1
- Produces:
  - `WebSearchTool.search(String query, int numResults): String` — JSON array string `[{title, url, content}]`
  - `WebSearchTool.definition(): Tool` — Claude tool definition consumed by `AgentService` (Task 5)

- [ ] **Step 1: Write failing test**

`ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/tool/WebSearchToolTest.java`:
```java
package com.testingai.agent.tool;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.agent.config.TavilyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class WebSearchToolTest {

    private WireMockServer wireMock;
    private WebSearchTool tool;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        var props = new TavilyProperties("test-key", "http://localhost:" + wireMock.port());
        tool = new WebSearchTool(
                RestClient.builder().baseUrl(props.baseUrl()).build(),
                props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void search_returnsParsedResultsAsJsonArray() {
        stubFor(post("/search")
                .withRequestBody(matchingJsonPath("$.query", equalTo("AI news")))
                .withRequestBody(matchingJsonPath("$.max_results", equalTo("3")))
                .willReturn(ok()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "results": [
                                    {
                                      "title": "AI Advances 2025",
                                      "url": "https://example.com/ai",
                                      "content": "Latest AI breakthroughs..."
                                    }
                                  ]
                                }
                                """)));

        String result = tool.search("AI news", 3);

        assertThat(result).contains("AI Advances 2025");
        assertThat(result).contains("https://example.com/ai");
        assertThat(result).contains("Latest AI breakthroughs");
    }

    @Test
    void search_returnsErrorJsonOnServerError() {
        stubFor(post("/search").willReturn(serverError()));

        String result = tool.search("anything", 5);

        assertThat(result).contains("error");
    }

    @Test
    void definition_hasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("web_search");
        assertThat(tool.definition().description()).isNotBlank();
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=WebSearchToolTest -q 2>&1 | tail -5
```
Expected: `COMPILATION ERROR` — `WebSearchTool cannot be resolved`

- [ ] **Step 3: Implement WebSearchTool.java**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/WebSearchTool.java`:
```java
package com.testingai.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.Tool;
import com.anthropic.models.ToolInputSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.agent.config.TavilyProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class WebSearchTool {

    private final RestClient restClient;
    private final TavilyProperties tavily;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSearchTool(RestClient tavilyRestClient, TavilyProperties tavily) {
        this.restClient = tavilyRestClient;
        this.tavily = tavily;
    }

    public String search(String query, int numResults) {
        try {
            var body = Map.of(
                    "api_key", tavily.apiKey(),
                    "query", query,
                    "max_results", numResults);
            TavilyResponse response = restClient.post()
                    .uri("/search")
                    .body(body)
                    .retrieve()
                    .body(TavilyResponse.class);
            List<TavilyResult> results = response != null ? response.results() : List.of();
            return objectMapper.writeValueAsString(results);
        } catch (RestClientException | JsonProcessingException e) {
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    public Tool definition() {
        return Tool.builder()
                .name("web_search")
                .description("Search the web for up-to-date information. Returns a list of results with title, URL, and text content.")
                .inputSchema(ToolInputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "The search query"),
                                "num_results", Map.of(
                                        "type", "integer",
                                        "description", "Number of results to return (default 5)"))))
                        .putAdditionalProperty("required", JsonValue.from(List.of("query")))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    record TavilyResponse(List<TavilyResult> results) {}
    record TavilyResult(String title, String url, String content) {}
}
```

> **SDK note:** If `ToolInputSchema` is not importable, check the SDK jar for the correct class name — it may appear as `Tool.InputSchema` or `InputSchema` depending on the SDK version. Run `jar tf ~/.m2/repository/com/anthropic/anthropic-java/2.40.1/anthropic-java-2.40.1.jar | grep -i schema` to locate it.

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=WebSearchToolTest -q
```
Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/WebSearchTool.java \
        ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/tool/WebSearchToolTest.java
git commit -m "feat(ai-agent): add WebSearchTool with Tavily integration"
```

---

### Task 4: FetchPageTool — HTTP fetch, Jsoup HTML strip, WireMock tests

**Files:**
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/FetchPageTool.java`
- Create: `ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/tool/FetchPageToolTest.java`

**Interfaces:**
- Consumes: `HttpClient` bean from Task 2; `AgentProperties.fetchPageMaxChars()` from Task 1
- Produces:
  - `FetchPageTool.fetch(String url): String` — plain text ≤ 4 000 chars, or error JSON
  - `FetchPageTool.definition(): Tool` — Claude tool definition consumed by `AgentService` (Task 5)

- [ ] **Step 1: Write failing test**

`ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/tool/FetchPageToolTest.java`:
```java
package com.testingai.agent.tool;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class FetchPageToolTest {

    private WireMockServer wireMock;
    private FetchPageTool tool;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        tool = new FetchPageTool(HttpClient.newHttpClient(), 4000);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetch_stripsHtmlAndReturnsPlainText() {
        stubFor(get("/article")
                .willReturn(ok()
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Quantum Computing</h1><p>New breakthrough.</p></body></html>")));

        String result = tool.fetch("http://localhost:" + wireMock.port() + "/article");

        assertThat(result).contains("Quantum Computing");
        assertThat(result).contains("New breakthrough");
        assertThat(result).doesNotContain("<html>");
        assertThat(result).doesNotContain("<p>");
    }

    @Test
    void fetch_trimsOutputToMaxChars() {
        String longContent = "x".repeat(10_000);
        stubFor(get("/long")
                .willReturn(ok()
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><p>" + longContent + "</p></body></html>")));

        String result = tool.fetch("http://localhost:" + wireMock.port() + "/long");

        assertThat(result.length()).isLessThanOrEqualTo(4000);
    }

    @Test
    void fetch_returnsErrorJsonOnNon200() {
        stubFor(get("/missing").willReturn(notFound()));

        String result = tool.fetch("http://localhost:" + wireMock.port() + "/missing");

        assertThat(result).contains("error");
    }

    @Test
    void definition_hasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("fetch_page");
        assertThat(tool.definition().description()).isNotBlank();
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=FetchPageToolTest -q 2>&1 | tail -5
```
Expected: `COMPILATION ERROR` — `FetchPageTool cannot be resolved`

- [ ] **Step 3: Implement FetchPageTool.java**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/FetchPageTool.java`:
```java
package com.testingai.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.Tool;
import com.anthropic.models.ToolInputSchema;
import com.testingai.agent.config.AgentProperties;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Component
public class FetchPageTool {

    private final HttpClient httpClient;
    private final int maxChars;

    public FetchPageTool(HttpClient httpClient, AgentProperties agentProperties) {
        this.httpClient = httpClient;
        this.maxChars = agentProperties.fetchPageMaxChars();
    }

    // Package-private constructor used by tests to inject fixed maxChars without a full Spring context
    FetchPageTool(HttpClient httpClient, int maxChars) {
        this.httpClient = httpClient;
        this.maxChars = maxChars;
    }

    public String fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AgentBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "{\"error\": \"HTTP " + response.statusCode() + " fetching " + url + "\"}";
            }
            String text = Jsoup.parse(response.body()).text();
            return text.length() > maxChars ? text.substring(0, maxChars) : text;
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    public Tool definition() {
        return Tool.builder()
                .name("fetch_page")
                .description("Fetch the full text content of a web page by URL. Use this after web_search to read a result in detail.")
                .inputSchema(ToolInputSchema.builder()
                        .properties(JsonValue.from(Map.of(
                                "url", Map.of(
                                        "type", "string",
                                        "description", "The full URL of the page to fetch"))))
                        .putAdditionalProperty("required", JsonValue.from(List.of("url")))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=FetchPageToolTest -q
```
Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/FetchPageTool.java \
        ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/tool/FetchPageToolTest.java
git commit -m "feat(ai-agent): add FetchPageTool with Jsoup HTML stripping"
```

---

### Task 5: ToolExecutor + AgentService — agentic loop and unit tests

**Files:**
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/ToolExecutor.java`
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/service/AgentService.java`
- Create: `ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/service/AgentServiceTest.java`

**Interfaces:**
- Consumes:
  - `AnthropicClient` bean (Task 2)
  - `WebSearchTool.search(String, int)`, `WebSearchTool.definition()` (Task 3)
  - `FetchPageTool.fetch(String)`, `FetchPageTool.definition()` (Task 4)
  - `AgentProperties`, `AnthropicProperties` (Task 1)
- Produces: `AgentService.run(String goal): AgentResponse` — called by `AgentController` (Task 6)

- [ ] **Step 1: Write failing tests**

`ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/service/AgentServiceTest.java`:
```java
package com.testingai.agent.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.*;
import com.testingai.agent.config.AgentProperties;
import com.testingai.agent.config.AnthropicProperties;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.tool.FetchPageTool;
import com.testingai.agent.tool.ToolExecutor;
import com.testingai.agent.tool.WebSearchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    // RETURNS_DEEP_STUBS allows chaining: anthropic.messages().create(any())
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock private ToolExecutor toolExecutor;
    @Mock private WebSearchTool webSearchTool;
    @Mock private FetchPageTool fetchPageTool;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        when(webSearchTool.definition()).thenReturn(mock(Tool.class));
        when(fetchPageTool.definition()).thenReturn(mock(Tool.class));
        agentService = new AgentService(
                anthropic, toolExecutor, webSearchTool, fetchPageTool,
                new AgentProperties(10, 4000),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));
    }

    @Test
    void run_singleIteration_noToolCalls_returnsAnswer() {
        when(anthropic.messages().create(any())).thenReturn(mockTextMessage("Paris."));

        AgentResponse result = agentService.run("Capital of France?");

        assertThat(result.answer()).isEqualTo("Paris.");
        assertThat(result.steps()).isEmpty();
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void run_multiIteration_executesToolThenReturnsAnswer() {
        Message toolCallResponse = mockToolUseMessage(
                "tool_abc", "web_search",
                JsonValue.from(Map.of("query", "quantum news", "num_results", 5)));
        Message finalResponse = mockTextMessage("Quantum computing advances rapidly.");

        when(anthropic.messages().create(any()))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);
        when(toolExecutor.execute(eq("web_search"), any()))
                .thenReturn("[{\"title\":\"Q News\",\"url\":\"http://q.com\",\"content\":\"...\"}]");

        AgentResponse result = agentService.run("Latest quantum computing news?");

        assertThat(result.answer()).isEqualTo("Quantum computing advances rapidly.");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).tool()).isEqualTo("web_search");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void run_truncatesWhenIterationCapReached() {
        Message loopingToolCall = mockToolUseMessage(
                "tool_loop", "web_search",
                JsonValue.from(Map.of("query", "test", "num_results", 5)));
        when(anthropic.messages().create(any())).thenReturn(loopingToolCall);
        when(toolExecutor.execute(any(), any())).thenReturn("[]");

        // Override with maxIterations = 2
        agentService = new AgentService(
                anthropic, toolExecutor, webSearchTool, fetchPageTool,
                new AgentProperties(2, 4000),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));

        AgentResponse result = agentService.run("Loop forever");

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
    }

    // --- helpers ---

    private Message mockTextMessage(String text) {
        TextBlock textBlock = mock(TextBlock.class);
        when(textBlock.text()).thenReturn(text);

        ContentBlock block = mock(ContentBlock.class);
        when(block.asText()).thenReturn(textBlock);
        when(block.asToolUse()).thenReturn(null);

        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of(block));
        return message;
    }

    private Message mockToolUseMessage(String id, String name, JsonValue input) {
        ToolUseBlock toolUse = mock(ToolUseBlock.class);
        when(toolUse.id()).thenReturn(id);
        when(toolUse.name()).thenReturn(name);
        when(toolUse.input()).thenReturn(input);

        ContentBlock block = mock(ContentBlock.class);
        when(block.asToolUse()).thenReturn(toolUse);
        when(block.asText()).thenReturn(null);

        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of(block));
        return message;
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=AgentServiceTest -q 2>&1 | tail -5
```
Expected: `COMPILATION ERROR` — `ToolExecutor` and `AgentService` not found

- [ ] **Step 3: Implement ToolExecutor.java**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/ToolExecutor.java`:
```java
package com.testingai.agent.tool;

import com.anthropic.core.JsonValue;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolExecutor {

    private final WebSearchTool webSearch;
    private final FetchPageTool fetchPage;

    public ToolExecutor(WebSearchTool webSearch, FetchPageTool fetchPage) {
        this.webSearch = webSearch;
        this.fetchPage = fetchPage;
    }

    public String execute(String toolName, JsonValue input) {
        Map<String, JsonValue> fields = input.asObject()
                .orElseThrow(() -> new IllegalArgumentException("Tool input must be a JSON object"));
        return switch (toolName) {
            case "web_search" -> {
                String query = fields.get("query").asString()
                        .orElseThrow(() -> new IllegalArgumentException("web_search: missing 'query'"));
                int numResults = fields.containsKey("num_results")
                        ? fields.get("num_results").asNumber().map(Number::intValue).orElse(5)
                        : 5;
                yield webSearch.search(query, numResults);
            }
            case "fetch_page" -> {
                String url = fields.get("url").asString()
                        .orElseThrow(() -> new IllegalArgumentException("fetch_page: missing 'url'"));
                yield fetchPage.fetch(url);
            }
            default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
        };
    }
}
```

> **SDK note:** `JsonValue.asObject()` returns `Optional<Map<String, JsonValue>>` and `JsonValue.asString()` returns `Optional<String>` in the Anthropic Java SDK. `asNumber()` returns `Optional<Number>`. If these methods have different names in the installed SDK version, run `javap -p $(find ~/.m2 -name 'anthropic-java-*.jar' | head -1) com.anthropic.core.JsonValue 2>/dev/null | head -30` to inspect the actual API.

- [ ] **Step 4: Implement AgentService.java**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/service/AgentService.java`:
```java
package com.testingai.agent.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.*;
import com.testingai.agent.config.AgentProperties;
import com.testingai.agent.config.AnthropicProperties;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.model.StepRecord;
import com.testingai.agent.tool.FetchPageTool;
import com.testingai.agent.tool.ToolExecutor;
import com.testingai.agent.tool.WebSearchTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private final AnthropicClient anthropic;
    private final ToolExecutor toolExecutor;
    private final WebSearchTool webSearchTool;
    private final FetchPageTool fetchPageTool;
    private final AgentProperties agentProps;
    private final AnthropicProperties anthropicProps;

    public AgentService(AnthropicClient anthropic,
                        ToolExecutor toolExecutor,
                        WebSearchTool webSearchTool,
                        FetchPageTool fetchPageTool,
                        AgentProperties agentProps,
                        AnthropicProperties anthropicProps) {
        this.anthropic = anthropic;
        this.toolExecutor = toolExecutor;
        this.webSearchTool = webSearchTool;
        this.fetchPageTool = fetchPageTool;
        this.agentProps = agentProps;
        this.anthropicProps = anthropicProps;
    }

    public AgentResponse run(String goal) {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.userMessage(goal));

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProps.maxIterations()) {
            Message response = anthropic.messages().create(
                    MessageCreateParams.builder()
                            .model(Model.of(anthropicProps.model()))
                            .maxTokens(4096L)
                            .addAllMessages(messages)
                            .addTool(webSearchTool.definition())
                            .addTool(fetchPageTool.definition())
                            .toolChoice(ToolChoice.AUTO)
                            .build());

            messages.add(MessageParam.assistantMessage(response.content()));
            iterations++;

            List<ToolUseBlock> toolCalls = response.content().stream()
                    .map(ContentBlock::asToolUse)
                    .filter(b -> b != null)
                    .toList();

            if (toolCalls.isEmpty()) {
                String answer = response.content().stream()
                        .map(ContentBlock::asText)
                        .filter(b -> b != null)
                        .map(TextBlock::text)
                        .collect(Collectors.joining(""));
                return new AgentResponse(answer, steps, iterations, false);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ToolUseBlock call : toolCalls) {
                String output = toolExecutor.execute(call.name(), call.input());
                steps.add(new StepRecord(call.name(), call.input().toString(), output));
                toolResults.add(ContentBlockParam.toolResultBlock(call.id(), output));
            }
            messages.add(MessageParam.userMessage(toolResults));
        }

        return new AgentResponse("", steps, iterations, true);
    }
}
```

> **SDK note:** `ToolChoice.AUTO` is the expected constant name based on SDK docs. If it does not compile, check for `ToolChoice.ofAuto(...)` or `ToolChoice.auto()`. Similarly, `Model.of(String)` is the string factory; if absent, use `Model.CLAUDE_SONNET_4_6`. Run `jar tf ~/.m2/repository/com/anthropic/anthropic-java/2.40.1/anthropic-java-2.40.1.jar | grep -E "ToolChoice|Model"` to confirm.

- [ ] **Step 5: Run tests to confirm they pass**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=AgentServiceTest -q
```
Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Commit**

```bash
git add ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/tool/ToolExecutor.java \
        ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/service/AgentService.java \
        ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/service/AgentServiceTest.java
git commit -m "feat(ai-agent): implement agentic loop with ToolExecutor dispatch"
```

---

### Task 6: AgentController — REST endpoint and MockMvc tests

**Files:**
- Create: `ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/controller/AgentController.java`
- Create: `ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/controller/AgentControllerTest.java`

**Interfaces:**
- Consumes: `AgentService.run(String): AgentResponse` (Task 5)
- Produces: `POST /api/agent/run` → `AgentResponse` JSON

- [ ] **Step 1: Write failing test**

`ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/controller/AgentControllerTest.java`:
```java
package com.testingai.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.agent.model.AgentRequest;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private AgentService agentService;

    @Test
    void run_returnsAgentResponseAsJson() throws Exception {
        AgentResponse expected = new AgentResponse("Paris.", List.of(), 1, false);
        when(agentService.run("Capital of France?")).thenReturn(expected);

        mockMvc.perform(post("/api/agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentRequest("Capital of France?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Paris."))
                .andExpect(jsonPath("$.iterations").value(1))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void run_returns400WhenGoalIsBlank() throws Exception {
        mockMvc.perform(post("/api/agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=AgentControllerTest -q 2>&1 | tail -5
```
Expected: `COMPILATION ERROR` — `AgentController cannot be resolved`

- [ ] **Step 3: Implement AgentController.java**

`ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/controller/AgentController.java`:
```java
package com.testingai.agent.controller;

import com.testingai.agent.model.AgentRequest;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public AgentResponse run(@RequestBody @Valid AgentRequest request) {
        return agentService.run(request.goal());
    }
}
```

- [ ] **Step 4: Run controller tests**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=AgentControllerTest -q
```
Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Run full unit test suite**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -q
```
Expected: `BUILD SUCCESS` — all tests pass, integration test excluded

- [ ] **Step 6: Commit**

```bash
git add ai/task-automation-agent/spring-demo/src/main/java/com/testingai/agent/controller/AgentController.java \
        ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/controller/AgentControllerTest.java
git commit -m "feat(ai-agent): add AgentController REST endpoint POST /api/agent/run"
```

---

### Task 7: Integration test + variant READMEs

**Files:**
- Create: `ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/integration/AgentIntegrationTest.java`
- Create: `ai/customer-support-bot/README.md`
- Create: `ai/code-review-agent/README.md`
- Create: `ai/data-pipeline-agent/README.md`

**Interfaces:**
- Integration test consumes the running app on a random port; requires real `ANTHROPIC_API_KEY` and `TAVILY_API_KEY`

- [ ] **Step 1: Create integration test**

`ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/integration/AgentIntegrationTest.java`:
```java
package com.testingai.agent.integration;

import com.testingai.agent.model.AgentResponse;
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
class AgentIntegrationTest {

    @LocalServerPort private int port;

    private final TestRestTemplate http = new TestRestTemplate();

    @Test
    void run_withRealApis_returnsNonEmptyAnswer() {
        var request = Map.of("goal", "In one sentence, what is the capital of France?");

        ResponseEntity<AgentResponse> response = http.postForEntity(
                "http://localhost:" + port + "/api/agent/run",
                request,
                AgentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isNotBlank();
    }
}
```

To run manually (set both keys first):
```bash
export ANTHROPIC_API_KEY=sk-ant-...
export TAVILY_API_KEY=tvly-...
cd ai/task-automation-agent/spring-demo
mvn test -Dtest=AgentIntegrationTest -Dgroups=integration
```

- [ ] **Step 2: Verify integration test is excluded from `mvn test`**

```bash
cd ai/task-automation-agent/spring-demo
mvn test -q 2>&1 | grep -E "integration|Tests run"
```
Expected: `AgentIntegrationTest` does not appear in output; all other tests still pass

- [ ] **Step 3: Create customer-support-bot README**

`ai/customer-support-bot/README.md`:
```markdown
# Customer Support Bot — Future Investigation

## Concept

An agentic chatbot that handles multi-turn customer conversations, looks up a product knowledge base, and resolves or escalates issues autonomously without human intervention until explicitly required.

## Key Capabilities to Explore

- **Multi-turn conversation memory** — maintain context across HTTP requests using Redis or an in-process session store
- **RAG knowledge base** — embed product docs into a vector store (pgvector on the existing Postgres from `docker-compose.yml`), retrieve relevant chunks per user turn
- **Escalation policy** — if Claude's confidence is low or user sentiment turns negative over N consecutive turns, hand off to a human queue and emit a structured escalation event
- **Outcome tracking** — each conversation produces a typed result (resolved / escalated / abandoned) written to the DB for downstream analytics

## Suggested Stack

- Spring Boot + Spring AI (`ChatClient` with `@Tool`-annotated methods)
- pgvector extension on the shared Postgres container for embeddings
- Claude with a system prompt defining the support persona and escalation threshold

## Starting Points

- Spring AI docs: https://docs.spring.io/spring-ai/reference/
- pgvector with Spring AI: search "VectorStore" in Spring AI docs
- Compare Spring AI vs LangChain4j for RAG pipeline complexity before committing
```

- [ ] **Step 4: Create code-review-agent README**

`ai/code-review-agent/README.md`:
```markdown
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
```

- [ ] **Step 5: Create data-pipeline-agent README**

`ai/data-pipeline-agent/README.md`:
```markdown
# Data Pipeline Agent — Future Investigation

## Concept

An agent that ingests raw unstructured data (CSV uploads, API responses, free-text reports), enriches each record by calling external APIs, validates the result against an inferred schema, and writes clean structured output to a database.

## Key Capabilities to Explore

- **Schema inference loop** — agent inspects a data sample, proposes a target schema, user confirms, then transformation begins
- **Enrichment tools** — per-row lookup tools: geocoding, currency conversion, entity resolution, taxonomy classification
- **Validation loop** — after transformation, Claude checks its own output against the agreed schema and retries failed rows before committing
- **Streaming for large files** — use the Anthropic streaming API (`MessageStreamParams`) to process large datasets record-by-record without hitting token limits
- **Spring Batch integration** — for files with millions of rows, delegate batching and retry to Spring Batch; Claude handles enrichment per chunk

## Suggested Stack

- Spring Boot + Anthropic Java SDK with streaming support
- Spring Batch for large-file ingestion and retry
- Existing Postgres from `docker-compose.yml` as the staging and output store
- Claude JSON mode (structured output) for schema-validated results

## Starting Points

- Anthropic streaming Java SDK: `MessageStreamParams` builder
- Spring Batch: https://spring.io/projects/spring-batch
- Claude structured output (tool use or response format) for typed JSON rows
```

- [ ] **Step 6: Commit**

```bash
git add ai/task-automation-agent/spring-demo/src/test/java/com/testingai/agent/integration/ \
        ai/customer-support-bot/README.md \
        ai/code-review-agent/README.md \
        ai/data-pipeline-agent/README.md
git commit -m "feat(ai): add integration test and future-variant READMEs"
```

---

## Final Verification

```bash
cd ai/task-automation-agent/spring-demo
mvn clean test -q
```
Expected: `BUILD SUCCESS` — all unit tests green, `AgentIntegrationTest` absent from output.

End-to-end live run (requires API keys):
```bash
export ANTHROPIC_API_KEY=sk-ant-...
export TAVILY_API_KEY=tvly-...
cd ai/task-automation-agent/spring-demo
mvn spring-boot:run &
sleep 10
curl -s -X POST http://localhost:8084/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"goal":"In two sentences, what are the latest breakthroughs in quantum computing?"}' | jq .
```

Expected response shape:
```json
{
  "answer": "...",
  "steps": [
    { "tool": "web_search", "input": "...", "output": "..." },
    { "tool": "fetch_page", "input": "...", "output": "..." }
  ],
  "iterations": 3,
  "truncated": false
}
```

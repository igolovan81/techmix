# Customer Support Bot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build two Spring Boot services — an Embedding Service (port 8087) that manages a pgvector knowledge base via OpenAI embeddings, and a Chat Service (port 8086) that runs multi-turn Claude conversations with RAG context, escalation, and outcome persistence.

**Architecture:** Two independent Spring Boot apps under `ai/customer-support-bot/`. Embedding Service owns pgvector (ingest + search). Chat Service holds in-memory sessions, calls the Embedding Service for context, calls Claude via the Anthropic SDK, evaluates rule-based escalation, and writes outcomes to Postgres.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Anthropic Java SDK 2.40.1, OpenAI embeddings REST API, pgvector on shared Postgres, Liquibase, Lombok, WireMock 3.5.4, Testcontainers (pgvector), Mockito (deep stubs).

## Global Constraints

- Java 21; Spring Boot parent `3.4.4`; `anthropic-java:2.40.1`; `wiremock-standalone:3.5.4`
- Package roots: `com.testingai.embedding.*` (embedding-service) and `com.testingai.chat.*` (chat-service)
- Maven group `com.testingai`; Lombok on every class with boilerplate
- Surefire config: `<excludedGroups>integration</excludedGroups>` — integration tests run only with `-Dgroups=integration`
- Google Java Format: run `mvn fmt:format` before every commit (`fmt-maven-plugin` wired into build)
- Shared Postgres from root `docker compose up -d`; embedding-service port 8087; chat-service port 8086
- `VECTOR(1536)` — dimension of `text-embedding-3-small`
- All instance fields assigned once must be `private final`; fields assigned in lifecycle methods must be `private`
- No `.toString()` on objects passed to SLF4J `{}` placeholders

---

## Phase 1: Embedding Service

---

### Task 1: Embedding Service — project scaffold

**Files:**
- Create: `ai/customer-support-bot/embedding-service/pom.xml`
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/EmbeddingApplication.java`
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/config/OpenAiProperties.java`
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/config/AppConfig.java`
- Create: `ai/customer-support-bot/embedding-service/src/main/resources/application.yml`

**Interfaces:**
- Produces: `RestClient` bean named `openAiRestClient`; `OpenAiProperties` record with fields `apiKey()`, `baseUrl()`, `model()`

- [ ] **Step 1: Create `pom.xml`**

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
    <artifactId>embedding-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>embedding-service</name>
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
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.liquibase</groupId>
            <artifactId>liquibase-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.38</version>
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
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
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
            <plugin>
                <groupId>com.spotify.fmt</groupId>
                <artifactId>fmt-maven-plugin</artifactId>
                <version>2.24</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `EmbeddingApplication.java`**

```java
package com.testingai.embedding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.testingai.embedding.config.OpenAiProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenAiProperties.class)
public class EmbeddingApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmbeddingApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `OpenAiProperties.java`**

```java
package com.testingai.embedding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(String apiKey, String baseUrl, String model) {}
```

- [ ] **Step 4: Create `AppConfig.java`**

```java
package com.testingai.embedding.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private final OpenAiProperties openAi;

    public AppConfig(OpenAiProperties openAi) {
        this.openAi = openAi;
    }

    @PostConstruct
    public void validateApiKey() {
        if (!StringUtils.hasText(openAi.apiKey())) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }
    }

    @Bean
    public RestClient openAiRestClient() {
        return RestClient.builder()
                .baseUrl(openAi.baseUrl())
                .defaultHeader("Authorization", "Bearer " + openAi.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
```

- [ ] **Step 5: Create `application.yml`**

```yaml
server:
  port: 8087

openai:
  api-key: ${OPENAI_API_KEY:}
  base-url: https://api.openai.com
  model: text-embedding-3-small

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

- [ ] **Step 6: Verify the project compiles**

Run from `ai/customer-support-bot/embedding-service/`:
```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add ai/customer-support-bot/embedding-service/
git commit -m "feat(embedding-service): scaffold project with config and OpenAI RestClient bean"
```

---

### Task 2: Embedding Service — DB schema and models

**Files:**
- Create: `ai/customer-support-bot/embedding-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- Create: `ai/customer-support-bot/embedding-service/src/main/resources/db/changelog/001-create-kb-chunk.sql`
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/model/IngestRequest.java`
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/model/SearchResult.java`

**Interfaces:**
- Produces: `IngestRequest(title: String, content: String)`, `SearchResult(title: String, content: String, score: double)`, `kb_chunk` table with `VECTOR(1536)` column

- [ ] **Step 1: Create Liquibase master changelog**

```yaml
# db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: db/changelog/001-create-kb-chunk.sql
```

- [ ] **Step 2: Create `001-create-kb-chunk.sql`**

```sql
--liquibase formatted sql
--changeset embedding:1
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_chunk (
    id        BIGSERIAL PRIMARY KEY,
    title     TEXT NOT NULL,
    content   TEXT NOT NULL,
    embedding VECTOR(1536)
);

CREATE INDEX IF NOT EXISTS kb_chunk_embedding_idx
    ON kb_chunk USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);
```

- [ ] **Step 3: Create `IngestRequest.java`**

```java
package com.testingai.embedding.model;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(@NotBlank String title, @NotBlank String content) {}
```

- [ ] **Step 4: Create `SearchResult.java`**

```java
package com.testingai.embedding.model;

public record SearchResult(String title, String content, double score) {}
```

- [ ] **Step 5: Commit**

```bash
git add ai/customer-support-bot/embedding-service/
git commit -m "feat(embedding-service): add Liquibase schema for kb_chunk table and model records"
```

---

### Task 3: Embedding Service — EmbeddingService and test

**Files:**
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/service/EmbeddingService.java`
- Create: `ai/customer-support-bot/embedding-service/src/test/java/com/testingai/embedding/service/EmbeddingServiceTest.java`

**Interfaces:**
- Consumes: `RestClient` bean `openAiRestClient`, `OpenAiProperties.model()`
- Produces: `EmbeddingService.embed(String text) → float[]`

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.embedding.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EmbeddingServiceTest {

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();
        registry.add("openai.api-key", () -> "test-key");
        registry.add("openai.base-url", () -> "http://localhost:" + wireMock.port());
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:embed-test;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void embed_callsOpenAiAndReturnsFloatArray() {
        wireMock.stubFor(post(urlEqualTo("/v1/embeddings"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data": [{"embedding": [0.1, 0.2, 0.3]}]}
                                """)));

        float[] result = embeddingService.embed("test text");

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
                .withHeader("Authorization", equalTo("Bearer test-key")));
    }

    @Test
    void embed_sendsModelAndInputInRequestBody() {
        wireMock.stubFor(post(urlEqualTo("/v1/embeddings"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data": [{"embedding": [0.5]}]}
                                """)));

        embeddingService.embed("hello world");

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
                .withRequestBody(matchingJsonPath("$.input", equalTo("hello world")))
                .withRequestBody(matchingJsonPath("$.model", equalTo("text-embedding-3-small"))));
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run from `ai/customer-support-bot/embedding-service/`:
```bash
mvn test -Dtest=EmbeddingServiceTest -q 2>&1 | tail -5
```
Expected: `EmbeddingService` not found / compilation error.

- [ ] **Step 3: Implement `EmbeddingService.java`**

```java
package com.testingai.embedding.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.testingai.embedding.config.OpenAiProperties;

import java.util.List;

@Service
public class EmbeddingService {

    private final RestClient restClient;
    private final OpenAiProperties props;

    public EmbeddingService(RestClient openAiRestClient, OpenAiProperties props) {
        this.restClient = openAiRestClient;
        this.props = props;
    }

    public float[] embed(String text) {
        var request = new EmbeddingRequest(text, props.model());
        var response = restClient.post()
                .uri("/v1/embeddings")
                .body(request)
                .retrieve()
                .body(EmbeddingResponse.class);
        List<Float> floats = response.data().getFirst().embedding();
        float[] result = new float[floats.size()];
        for (int i = 0; i < floats.size(); i++) result[i] = floats.get(i);
        return result;
    }

    private record EmbeddingRequest(String input, String model) {}

    private record EmbeddingResponse(List<EmbeddingData> data) {
        private record EmbeddingData(List<Float> embedding) {}
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=EmbeddingServiceTest -q
```
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add ai/customer-support-bot/embedding-service/
git commit -m "feat(embedding-service): add EmbeddingService with OpenAI embeddings API call"
```

---

### Task 4: Embedding Service — VectorStoreService and test

**Files:**
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/service/VectorStoreService.java`
- Create: `ai/customer-support-bot/embedding-service/src/test/java/com/testingai/embedding/service/VectorStoreServiceTest.java`

**Interfaces:**
- Consumes: `JdbcTemplate` (auto-configured), `kb_chunk` table (from Task 2 Liquibase)
- Produces: `VectorStoreService.upsert(String title, String content, float[] embedding)`, `VectorStoreService.search(float[] queryEmbedding, int limit) → List<SearchResult>`

- [ ] **Step 1: Write the failing test**

Requires Docker (for Testcontainers). The container image `pgvector/pgvector:pg16` includes the pgvector extension.

```java
package com.testingai.embedding.service;

import com.testingai.embedding.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class VectorStoreServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("testdb")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("openai.api-key", () -> "test");
        registry.add("openai.base-url", () -> "http://localhost:1");
    }

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void clearTable() {
        jdbc.update("DELETE FROM kb_chunk");
    }

    @Test
    void upsertAndSearch_returnsInsertedChunk() {
        float[] embedding = new float[1536];
        embedding[0] = 1.0f;

        vectorStoreService.upsert("Refund Policy", "Refunds within 30 days.", embedding);

        float[] query = new float[1536];
        query[0] = 1.0f;
        List<SearchResult> results = vectorStoreService.search(query, 3);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().title()).isEqualTo("Refund Policy");
        assertThat(results.getFirst().content()).isEqualTo("Refunds within 30 days.");
        assertThat(results.getFirst().score()).isGreaterThan(0.99);
    }

    @Test
    void search_limitsResults() {
        float[] v = new float[1536];
        v[0] = 1.0f;
        vectorStoreService.upsert("A", "content a", v);
        vectorStoreService.upsert("B", "content b", v);
        vectorStoreService.upsert("C", "content c", v);

        List<SearchResult> results = vectorStoreService.search(v, 2);

        assertThat(results).hasSize(2);
    }
}
```

- [ ] **Step 2: Run to confirm the test fails**

```bash
mvn test -Dtest=VectorStoreServiceTest -q 2>&1 | tail -5
```
Expected: compilation error — `VectorStoreService` not found.

- [ ] **Step 3: Implement `VectorStoreService.java`**

```java
package com.testingai.embedding.service;

import com.testingai.embedding.model.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorStoreService {

    private final JdbcTemplate jdbc;

    public VectorStoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String title, String content, float[] embedding) {
        jdbc.update(
                "INSERT INTO kb_chunk (title, content, embedding) VALUES (?, ?, ?::vector)",
                title, content, formatVector(embedding));
    }

    public List<SearchResult> search(float[] queryEmbedding, int limit) {
        String vec = formatVector(queryEmbedding);
        return jdbc.query(
                "SELECT title, content, 1 - (embedding <=> ?::vector) AS score "
                        + "FROM kb_chunk ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> new SearchResult(
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getDouble("score")),
                vec, vec, limit);
    }

    private static String formatVector(float[] v) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        return sb.append("]").toString();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=VectorStoreServiceTest -q
```
Expected: BUILD SUCCESS, 2 tests passed. (Testcontainers pulls `pgvector/pgvector:pg16` on first run — allow extra time.)

- [ ] **Step 5: Commit**

```bash
git add ai/customer-support-bot/embedding-service/
git commit -m "feat(embedding-service): add VectorStoreService with pgvector upsert and cosine search"
```

---

### Task 5: Embedding Service — KnowledgeBaseController and test

**Files:**
- Create: `ai/customer-support-bot/embedding-service/src/main/java/com/testingai/embedding/controller/KnowledgeBaseController.java`
- Create: `ai/customer-support-bot/embedding-service/src/test/java/com/testingai/embedding/controller/KnowledgeBaseControllerTest.java`

**Interfaces:**
- Consumes: `EmbeddingService.embed(String) → float[]`, `VectorStoreService.upsert(...)`, `VectorStoreService.search(...)`, `IngestRequest`, `SearchResult`
- Produces: `POST /api/kb/ingest` (201), `GET /api/kb/search?q=...&limit=3` (200 JSON array)

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.embedding.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.embedding.model.IngestRequest;
import com.testingai.embedding.model.SearchResult;
import com.testingai.embedding.service.EmbeddingService;
import com.testingai.embedding.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KnowledgeBaseController.class)
class KnowledgeBaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private EmbeddingService embeddingService;
    @MockBean  private VectorStoreService vectorStoreService;

    @Test
    void ingest_returns201AndCallsServices() throws Exception {
        float[] vector = {0.1f, 0.2f};
        when(embeddingService.embed("How to return items?")).thenReturn(vector);

        mockMvc.perform(post("/api/kb/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IngestRequest("Returns", "How to return items?"))))
                .andExpect(status().isCreated());

        verify(vectorStoreService).upsert("Returns", "How to return items?", vector);
    }

    @Test
    void ingest_returns400WhenTitleBlank() throws Exception {
        mockMvc.perform(post("/api/kb/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"some content\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_returnsChunks() throws Exception {
        float[] qVec = {0.5f};
        when(embeddingService.embed("refund")).thenReturn(qVec);
        when(vectorStoreService.search(qVec, 3))
                .thenReturn(List.of(new SearchResult("Refund Policy", "30-day policy", 0.95)));

        mockMvc.perform(get("/api/kb/search").param("q", "refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Refund Policy"))
                .andExpect(jsonPath("$[0].score").value(0.95));
    }
}
```

- [ ] **Step 2: Run to confirm the test fails**

```bash
mvn test -Dtest=KnowledgeBaseControllerTest -q 2>&1 | tail -5
```
Expected: compilation error.

- [ ] **Step 3: Implement `KnowledgeBaseController.java`**

```java
package com.testingai.embedding.controller;

import com.testingai.embedding.model.IngestRequest;
import com.testingai.embedding.model.SearchResult;
import com.testingai.embedding.service.EmbeddingService;
import com.testingai.embedding.service.VectorStoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public KnowledgeBaseController(EmbeddingService embeddingService,
                                   VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public void ingest(@RequestBody @Valid IngestRequest request) {
        float[] embedding = embeddingService.embed(request.content());
        vectorStoreService.upsert(request.title(), request.content(), embedding);
    }

    @GetMapping("/search")
    public List<SearchResult> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "3") int limit) {
        float[] embedding = embeddingService.embed(q);
        return vectorStoreService.search(embedding, limit);
    }
}
```

- [ ] **Step 4: Run all embedding-service tests**

```bash
mvn test -q
```
Expected: BUILD SUCCESS, all tests pass (Testcontainers tests require Docker).

- [ ] **Step 5: Commit**

```bash
git add ai/customer-support-bot/embedding-service/
git commit -m "feat(embedding-service): add KnowledgeBaseController with ingest and search endpoints"
```

---

## Phase 2: Chat Service

---

### Task 6: Chat Service — project scaffold

**Files:**
- Create: `ai/customer-support-bot/chat-service/pom.xml`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/ChatApplication.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/config/AnthropicProperties.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/config/ChatProperties.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/config/AppConfig.java`
- Create: `ai/customer-support-bot/chat-service/src/main/resources/application.yml`

**Interfaces:**
- Produces: `AnthropicClient` bean; `RestClient` bean named `embeddingServiceClient`; `AnthropicProperties(apiKey, model)`; `ChatProperties(maxTurns, escalationKeywords, resolutionPhrase, embeddingServiceUrl)`

- [ ] **Step 1: Create `pom.xml`**

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
    <artifactId>chat-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>chat-service</name>
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
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.liquibase</groupId>
            <artifactId>liquibase-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.anthropic</groupId>
            <artifactId>anthropic-java</artifactId>
            <version>2.40.1</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.38</version>
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
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
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
            <plugin>
                <groupId>com.spotify.fmt</groupId>
                <artifactId>fmt-maven-plugin</artifactId>
                <version>2.24</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create `ChatApplication.java`**

```java
package com.testingai.chat;

import com.testingai.chat.config.AnthropicProperties;
import com.testingai.chat.config.ChatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AnthropicProperties.class, ChatProperties.class})
public class ChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `AnthropicProperties.java`**

```java
package com.testingai.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(String apiKey, String model) {}
```

- [ ] **Step 4: Create `ChatProperties.java`**

```java
package com.testingai.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        int maxTurns,
        List<String> escalationKeywords,
        String resolutionPhrase,
        String embeddingServiceUrl) {}
```

- [ ] **Step 5: Create `AppConfig.java`**

```java
package com.testingai.chat.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private final AnthropicProperties anthropic;
    private final ChatProperties chat;

    public AppConfig(AnthropicProperties anthropic, ChatProperties chat) {
        this.anthropic = anthropic;
        this.chat = chat;
    }

    @PostConstruct
    public void validateApiKey() {
        if (!StringUtils.hasText(anthropic.apiKey())) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropic.apiKey())
                .build();
    }

    @Bean
    public RestClient embeddingServiceClient() {
        return RestClient.builder()
                .baseUrl(chat.embeddingServiceUrl())
                .build();
    }
}
```

- [ ] **Step 6: Create `application.yml`**

```yaml
server:
  port: 8086

anthropic:
  api-key: ${ANTHROPIC_API_KEY:}
  model: claude-sonnet-4-6

chat:
  max-turns: 10
  escalation-keywords:
    - angry
    - lawsuit
    - unacceptable
    - refund
    - fraud
  resolution-phrase: "is there anything else"
  embedding-service-url: http://localhost:8087

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

- [ ] **Step 7: Verify the project compiles**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add ai/customer-support-bot/chat-service/
git commit -m "feat(chat-service): scaffold project with config, AnthropicClient, and embedding RestClient bean"
```

---

### Task 7: Chat Service — DB schema and models

**Files:**
- Create: `ai/customer-support-bot/chat-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- Create: `ai/customer-support-bot/chat-service/src/main/resources/db/changelog/001-create-conversations.sql`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/model/ConversationOutcome.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/model/SessionState.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/model/StartResponse.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/model/MessageRequest.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/model/MessageResponse.java`
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/model/SearchResult.java`

**Interfaces:**
- Produces: all model types used by services and controllers in Tasks 8–12

- [ ] **Step 1: Create Liquibase master changelog**

```yaml
# db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: db/changelog/001-create-conversations.sql
```

- [ ] **Step 2: Create `001-create-conversations.sql`**

```sql
--liquibase formatted sql
--changeset chat:1
CREATE TABLE IF NOT EXISTS conversations (
    id          BIGSERIAL    PRIMARY KEY,
    session_id  TEXT         NOT NULL,
    outcome     TEXT         NOT NULL,
    turn_count  INT          NOT NULL,
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ  NOT NULL
);
```

- [ ] **Step 3: Create `ConversationOutcome.java`**

```java
package com.testingai.chat.model;

public enum ConversationOutcome {
    OPEN, RESOLVED, ESCALATED, ABANDONED
}
```

- [ ] **Step 4: Create `SessionState.java`**

`SessionState` is mutable (turn count and outcome change per turn), so it uses a class rather than a record.

```java
package com.testingai.chat.model;

import com.anthropic.models.messages.MessageParam;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
public class SessionState {

    private final String sessionId;
    private final List<MessageParam> history = new ArrayList<>();
    private final Instant startedAt = Instant.now();
    private int turnCount;
    private ConversationOutcome outcome = ConversationOutcome.OPEN;

    public SessionState(String sessionId) {
        this.sessionId = sessionId;
    }

    public void addMessage(MessageParam param) {
        history.add(param);
    }

    public void incrementTurnCount() {
        turnCount++;
    }

    public void setOutcome(ConversationOutcome outcome) {
        this.outcome = outcome;
    }
}
```

- [ ] **Step 5: Create remaining model records**

`StartResponse.java`:
```java
package com.testingai.chat.model;

public record StartResponse(String sessionId) {}
```

`MessageRequest.java`:
```java
package com.testingai.chat.model;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest(@NotBlank String text) {}
```

`MessageResponse.java`:
```java
package com.testingai.chat.model;

public record MessageResponse(String reply, ConversationOutcome outcome, boolean escalated) {}
```

`SearchResult.java`:
```java
package com.testingai.chat.model;

public record SearchResult(String title, String content, double score) {}
```

- [ ] **Step 6: Commit**

```bash
git add ai/customer-support-bot/chat-service/
git commit -m "feat(chat-service): add Liquibase schema for conversations table and all model types"
```

---

### Task 8: Chat Service — EscalationPolicy and test

**Files:**
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/service/EscalationPolicy.java`
- Create: `ai/customer-support-bot/chat-service/src/test/java/com/testingai/chat/service/EscalationPolicyTest.java`

**Interfaces:**
- Consumes: `ChatProperties(maxTurns, escalationKeywords)`, `SessionState.getTurnCount()`, `SessionState.getOutcome()`
- Produces: `EscalationPolicy.evaluate(SessionState session, String userText) → ConversationOutcome`

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.chat.service;

import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationPolicyTest {

    private EscalationPolicy policy;

    @BeforeEach
    void setUp() {
        ChatProperties props = new ChatProperties(
                3,
                List.of("angry", "lawsuit", "fraud"),
                "is there anything else",
                "http://localhost:8087");
        policy = new EscalationPolicy(props);
    }

    @Test
    void evaluate_returnsOpen_whenBelowThresholdAndNoKeyword() {
        SessionState session = new SessionState("s1");
        session.incrementTurnCount();

        ConversationOutcome result = policy.evaluate(session, "I need help with my order.");

        assertThat(result).isEqualTo(ConversationOutcome.OPEN);
    }

    @Test
    void evaluate_returnsEscalated_whenTurnCountReachesMax() {
        SessionState session = new SessionState("s1");
        session.incrementTurnCount();
        session.incrementTurnCount();
        session.incrementTurnCount(); // turnCount == 3 == maxTurns

        ConversationOutcome result = policy.evaluate(session, "still no answer");

        assertThat(result).isEqualTo(ConversationOutcome.ESCALATED);
    }

    @Test
    void evaluate_returnsEscalated_whenUserTextContainsKeyword() {
        SessionState session = new SessionState("s1");

        ConversationOutcome result = policy.evaluate(session, "This is FRAUD!");

        assertThat(result).isEqualTo(ConversationOutcome.ESCALATED);
    }

    @Test
    void evaluate_isCaseInsensitiveForKeyword() {
        SessionState session = new SessionState("s1");

        ConversationOutcome result = policy.evaluate(session, "I'm ANGRY about this.");

        assertThat(result).isEqualTo(ConversationOutcome.ESCALATED);
    }
}
```

- [ ] **Step 2: Run to confirm the test fails**

```bash
mvn test -Dtest=EscalationPolicyTest -q 2>&1 | tail -5
```
Expected: compilation error.

- [ ] **Step 3: Implement `EscalationPolicy.java`**

```java
package com.testingai.chat.service;

import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.SessionState;
import org.springframework.stereotype.Component;

@Component
public class EscalationPolicy {

    private final ChatProperties props;

    public EscalationPolicy(ChatProperties props) {
        this.props = props;
    }

    public ConversationOutcome evaluate(SessionState session, String userText) {
        if (session.getTurnCount() >= props.maxTurns()) {
            return ConversationOutcome.ESCALATED;
        }
        String lower = userText.toLowerCase();
        for (String keyword : props.escalationKeywords()) {
            if (lower.contains(keyword.toLowerCase())) {
                return ConversationOutcome.ESCALATED;
            }
        }
        return ConversationOutcome.OPEN;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=EscalationPolicyTest -q
```
Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add ai/customer-support-bot/chat-service/
git commit -m "feat(chat-service): add EscalationPolicy with turn-count and keyword-based rules"
```

---

### Task 9: Chat Service — KnowledgeBaseClient and test

**Files:**
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/service/KnowledgeBaseClient.java`
- Create: `ai/customer-support-bot/chat-service/src/test/java/com/testingai/chat/service/KnowledgeBaseClientTest.java`

**Interfaces:**
- Consumes: `RestClient` bean `embeddingServiceClient`
- Produces: `KnowledgeBaseClient.search(String query, int limit) → List<SearchResult>`; returns empty list (not throws) on HTTP error

- [ ] **Step 1: Write the failing test**

This test constructs `KnowledgeBaseClient` directly with a test-configured `RestClient` pointing at WireMock — no Spring context needed.

```java
package com.testingai.chat.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.testingai.chat.model.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseClientTest {

    private WireMockServer wireMock;
    private KnowledgeBaseClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .build();
        client = new KnowledgeBaseClient(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void search_returnsChunksFromEmbeddingService() {
        wireMock.stubFor(get(urlPathEqualTo("/api/kb/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"title":"Refund Policy","content":"30 days","score":0.92}]
                                """)));

        List<SearchResult> results = client.search("refund", 3);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().title()).isEqualTo("Refund Policy");
        assertThat(results.getFirst().score()).isEqualTo(0.92);
    }

    @Test
    void search_returnsEmptyList_whenServiceUnavailable() {
        wireMock.stubFor(get(urlPathEqualTo("/api/kb/search"))
                .willReturn(aResponse().withStatus(503)));

        List<SearchResult> results = client.search("anything", 3);

        assertThat(results).isEmpty();
    }
}
```

- [ ] **Step 2: Run to confirm the test fails**

```bash
mvn test -Dtest=KnowledgeBaseClientTest -q 2>&1 | tail -5
```
Expected: compilation error.

- [ ] **Step 3: Implement `KnowledgeBaseClient.java`**

```java
package com.testingai.chat.service;

import com.testingai.chat.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
public class KnowledgeBaseClient {

    private final RestClient restClient;

    public KnowledgeBaseClient(RestClient embeddingServiceClient) {
        this.restClient = embeddingServiceClient;
    }

    public List<SearchResult> search(String query, int limit) {
        try {
            return restClient.get()
                    .uri("/api/kb/search?q={q}&limit={limit}", query, limit)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException e) {
            log.warn("Embedding service unavailable: {}", e.getMessage());
            return List.of();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=KnowledgeBaseClientTest -q
```
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add ai/customer-support-bot/chat-service/
git commit -m "feat(chat-service): add KnowledgeBaseClient with graceful fallback on HTTP error"
```

---

### Task 10: Chat Service — OutcomeRepository and test

**Files:**
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/repository/OutcomeRepository.java`
- Create: `ai/customer-support-bot/chat-service/src/test/java/com/testingai/chat/repository/OutcomeRepositoryTest.java`
- Create: `ai/customer-support-bot/chat-service/src/test/resources/schema.sql`

**Interfaces:**
- Consumes: `JdbcTemplate`, `SessionState.getSessionId()`, `SessionState.getOutcome()`, `SessionState.getTurnCount()`, `SessionState.getStartedAt()`
- Produces: `OutcomeRepository.save(SessionState session, Instant endedAt)`

- [ ] **Step 1: Create test schema for H2**

`src/test/resources/schema.sql` (H2-compatible DDL used only in `@JdbcTest`):
```sql
CREATE TABLE IF NOT EXISTS conversations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    outcome    VARCHAR(50)  NOT NULL,
    turn_count INT          NOT NULL,
    started_at TIMESTAMP    NOT NULL,
    ended_at   TIMESTAMP    NOT NULL
);
```

- [ ] **Step 2: Write the failing test**

```java
package com.testingai.chat.repository;

import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.SessionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(OutcomeRepository.class)
@TestPropertySource(properties = "spring.liquibase.enabled=false")
class OutcomeRepositoryTest {

    @Autowired private OutcomeRepository outcomeRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void save_insertsRowWithCorrectValues() {
        SessionState session = new SessionState("sess-abc");
        session.incrementTurnCount();
        session.incrementTurnCount();
        session.setOutcome(ConversationOutcome.RESOLVED);

        Instant endedAt = Instant.now();
        outcomeRepository.save(session, endedAt);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM conversations WHERE session_id = ?", "sess-abc");

        assertThat(row.get("session_id")).isEqualTo("sess-abc");
        assertThat(row.get("outcome")).isEqualTo("RESOLVED");
        assertThat(((Number) row.get("turn_count")).intValue()).isEqualTo(2);
        assertThat(row.get("started_at")).isNotNull();
        assertThat(row.get("ended_at")).isNotNull();
    }
}
```

- [ ] **Step 3: Run to confirm the test fails**

```bash
mvn test -Dtest=OutcomeRepositoryTest -q 2>&1 | tail -5
```
Expected: compilation error.

- [ ] **Step 4: Implement `OutcomeRepository.java`**

```java
package com.testingai.chat.repository;

import com.testingai.chat.model.SessionState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class OutcomeRepository {

    private final JdbcTemplate jdbc;

    public OutcomeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(SessionState session, Instant endedAt) {
        jdbc.update(
                "INSERT INTO conversations (session_id, outcome, turn_count, started_at, ended_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                session.getSessionId(),
                session.getOutcome().name(),
                session.getTurnCount(),
                Timestamp.from(session.getStartedAt()),
                Timestamp.from(endedAt));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=OutcomeRepositoryTest -q
```
Expected: BUILD SUCCESS, 1 test passed.

- [ ] **Step 6: Commit**

```bash
git add ai/customer-support-bot/chat-service/
git commit -m "feat(chat-service): add OutcomeRepository writing conversation outcome to Postgres"
```

---

### Task 11: Chat Service — ChatService and test

**Files:**
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/service/ChatService.java`
- Create: `ai/customer-support-bot/chat-service/src/test/java/com/testingai/chat/service/ChatServiceTest.java`

**Interfaces:**
- Consumes: `AnthropicClient` (deep-stubbed), `KnowledgeBaseClient.search(String, int)`, `EscalationPolicy.evaluate(SessionState, String)`, `OutcomeRepository.save(SessionState, Instant)`, `ChatProperties`, `AnthropicProperties`
- Produces: `ChatService.startSession() → StartResponse`, `ChatService.sendMessage(String sessionId, String text) → MessageResponse`, `ChatService.closeSession(String sessionId)`; throws `ResponseStatusException(404)` for unknown session, `ResponseStatusException(409)` for closed session

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.chat.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.chat.config.AnthropicProperties;
import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.SearchResult;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.repository.OutcomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock private KnowledgeBaseClient kbClient;
    @Mock private OutcomeRepository outcomeRepository;

    private EscalationPolicy escalationPolicy;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        ChatProperties props = new ChatProperties(
                10,
                List.of("angry", "lawsuit"),
                "is there anything else",
                "http://localhost:8087");
        escalationPolicy = new EscalationPolicy(props);
        chatService = new ChatService(
                anthropic, kbClient, escalationPolicy, outcomeRepository,
                props,
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));
    }

    @Test
    void startSession_returnsUniqueSessionId() {
        StartResponse r1 = chatService.startSession();
        StartResponse r2 = chatService.startSession();

        assertThat(r1.sessionId()).isNotBlank();
        assertThat(r1.sessionId()).isNotEqualTo(r2.sessionId());
    }

    @Test
    void sendMessage_returnsReplyAndOpenOutcome() {
        when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("I can help with that."));

        String sessionId = chatService.startSession().sessionId();
        MessageResponse response = chatService.sendMessage(sessionId, "Where is my order?");

        assertThat(response.reply()).isEqualTo("I can help with that.");
        assertThat(response.outcome()).isEqualTo(ConversationOutcome.OPEN);
        assertThat(response.escalated()).isFalse();
    }

    @Test
    void sendMessage_marksResolved_whenReplyContainsResolutionPhrase() {
        when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("Your order ships tomorrow. Is there anything else I can help you with?"));

        String sessionId = chatService.startSession().sessionId();
        MessageResponse response = chatService.sendMessage(sessionId, "When does my order ship?");

        assertThat(response.outcome()).isEqualTo(ConversationOutcome.RESOLVED);
        verify(outcomeRepository).save(any(), any());
    }

    @Test
    void sendMessage_escalates_whenKeywordDetected() {
        when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("I understand your frustration."));

        String sessionId = chatService.startSession().sessionId();
        MessageResponse response = chatService.sendMessage(sessionId, "This is ANGRY customer complaint!");

        assertThat(response.outcome()).isEqualTo(ConversationOutcome.ESCALATED);
        assertThat(response.escalated()).isTrue();
        verify(outcomeRepository).save(any(), any());
    }

    @Test
    void sendMessage_injectsKbChunksIntoSystemPrompt() {
        when(kbClient.search(anyString(), anyInt()))
                .thenReturn(List.of(new SearchResult("Shipping", "Ships in 3 days", 0.9)));
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("Ships in 3 days."));

        String sessionId = chatService.startSession().sessionId();
        chatService.sendMessage(sessionId, "When does shipping happen?");

        verify(anthropic.messages()).create(argThat(params ->
                params.system().toString().contains("Ships in 3 days")));
    }

    @Test
    void sendMessage_throws404_forUnknownSession() {
        assertThatThrownBy(() -> chatService.sendMessage("nonexistent", "hello"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void sendMessage_throws409_forClosedSession() {
        when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("Is there anything else I can help you with?"));

        String sessionId = chatService.startSession().sessionId();
        chatService.sendMessage(sessionId, "done");

        assertThatThrownBy(() -> chatService.sendMessage(sessionId, "another message"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void closeSession_marksAbandoned_whenOpen() {
        String sessionId = chatService.startSession().sessionId();
        chatService.closeSession(sessionId);

        verify(outcomeRepository).save(any(), any());
    }

    // --- helpers (same pattern as AgentServiceTest) ---

    private Message buildTextMessage(String text) {
        TextBlock textBlock = TextBlock.builder()
                .citations(Optional.empty())
                .text(text)
                .build();
        return buildMessage(List.of(ContentBlock.ofText(textBlock)));
    }

    private Message buildMessage(List<ContentBlock> blocks) {
        Usage usage = Usage.builder()
                .cacheCreation(Optional.empty())
                .cacheCreationInputTokens(Optional.empty())
                .cacheReadInputTokens(Optional.empty())
                .inferenceGeo(Optional.empty())
                .inputTokens(0L)
                .outputTokens(0L)
                .outputTokensDetails(Optional.empty())
                .serverToolUse(Optional.empty())
                .serviceTier(Optional.empty())
                .build();
        return Message.builder()
                .id("msg_test")
                .content(blocks)
                .model("claude-sonnet-4-6")
                .stopDetails(Optional.empty())
                .stopReason(Optional.empty())
                .stopSequence(Optional.empty())
                .usage(usage)
                .build();
    }
}
```

- [ ] **Step 2: Run to confirm the test fails**

```bash
mvn test -Dtest=ChatServiceTest -q 2>&1 | tail -5
```
Expected: compilation error.

- [ ] **Step 3: Implement `ChatService.java`**

```java
package com.testingai.chat.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.testingai.chat.config.AnthropicProperties;
import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.SearchResult;
import com.testingai.chat.model.SessionState;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.repository.OutcomeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final String PERSONA = """
            You are a helpful customer support agent. Answer questions clearly and concisely.
            When you have fully resolved the customer's issue, end your response with \
            "Is there anything else I can help you with?"
            """;

    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    private final AnthropicClient anthropic;
    private final KnowledgeBaseClient kbClient;
    private final EscalationPolicy escalationPolicy;
    private final OutcomeRepository outcomeRepository;
    private final ChatProperties chatProps;
    private final AnthropicProperties anthropicProps;

    public ChatService(AnthropicClient anthropic,
                       KnowledgeBaseClient kbClient,
                       EscalationPolicy escalationPolicy,
                       OutcomeRepository outcomeRepository,
                       ChatProperties chatProps,
                       AnthropicProperties anthropicProps) {
        this.anthropic = anthropic;
        this.kbClient = kbClient;
        this.escalationPolicy = escalationPolicy;
        this.outcomeRepository = outcomeRepository;
        this.chatProps = chatProps;
        this.anthropicProps = anthropicProps;
    }

    public StartResponse startSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new SessionState(sessionId));
        return new StartResponse(sessionId);
    }

    public MessageResponse sendMessage(String sessionId, String userText) {
        SessionState session = requireOpenSession(sessionId);

        List<SearchResult> chunks = kbClient.search(userText, 3);
        String systemPrompt = buildSystemPrompt(chunks);

        session.addMessage(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(userText)
                .build());

        var response = anthropic.messages().create(
                MessageCreateParams.builder()
                        .model(anthropicProps.model())
                        .maxTokens(1024)
                        .system(systemPrompt)
                        .messages(session.getHistory())
                        .build());

        String reply = response.content().stream()
                .filter(ContentBlock::isText)
                .map(ContentBlock::asText)
                .map(TextBlock::text)
                .collect(Collectors.joining());

        List<ContentBlockParam> assistantBlocks = response.content().stream()
                .map(ContentBlock::toParam)
                .filter(Objects::nonNull)
                .toList();
        session.addMessage(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .contentOfBlockParams(assistantBlocks)
                .build());
        session.incrementTurnCount();

        boolean escalated = false;
        ConversationOutcome newOutcome = escalationPolicy.evaluate(session, userText);
        if (newOutcome != ConversationOutcome.OPEN) {
            session.setOutcome(newOutcome);
            escalated = (newOutcome == ConversationOutcome.ESCALATED);
            outcomeRepository.save(session, Instant.now());
        } else if (reply.toLowerCase().contains(chatProps.resolutionPhrase().toLowerCase())) {
            session.setOutcome(ConversationOutcome.RESOLVED);
            outcomeRepository.save(session, Instant.now());
        }

        return new MessageResponse(reply, session.getOutcome(), escalated);
    }

    public void closeSession(String sessionId) {
        SessionState session = requireSession(sessionId);
        if (session.getOutcome() == ConversationOutcome.OPEN) {
            session.setOutcome(ConversationOutcome.ABANDONED);
            outcomeRepository.save(session, Instant.now());
        }
    }

    private SessionState requireSession(String sessionId) {
        SessionState session = sessions.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }
        return session;
    }

    private SessionState requireOpenSession(String sessionId) {
        SessionState session = requireSession(sessionId);
        if (session.getOutcome() != ConversationOutcome.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is closed: " + sessionId);
        }
        return session;
    }

    private String buildSystemPrompt(List<SearchResult> chunks) {
        if (chunks.isEmpty()) return PERSONA;
        var context = new StringBuilder("Relevant product information:\n");
        for (int i = 0; i < chunks.size(); i++) {
            context.append(i + 1).append(". ")
                    .append(chunks.get(i).title()).append(": ")
                    .append(chunks.get(i).content()).append("\n");
        }
        return PERSONA + "\n" + context;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=ChatServiceTest -q
```
Expected: BUILD SUCCESS, 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add ai/customer-support-bot/chat-service/
git commit -m "feat(chat-service): add ChatService with multi-turn loop, escalation, and outcome persistence"
```

---

### Task 12: Chat Service — ChatController, controller test, and READMEs

**Files:**
- Create: `ai/customer-support-bot/chat-service/src/main/java/com/testingai/chat/controller/ChatController.java`
- Create: `ai/customer-support-bot/chat-service/src/test/java/com/testingai/chat/controller/ChatControllerTest.java`
- Create: `ai/customer-support-bot/embedding-service/README.md`
- Create: `ai/customer-support-bot/chat-service/README.md`

**Interfaces:**
- Consumes: `ChatService.startSession()`, `ChatService.sendMessage()`, `ChatService.closeSession()`
- Produces: `POST /api/chat/start` → 200 `{sessionId}`, `POST /api/chat/{sessionId}/message` → 200 `{reply, outcome, escalated}`, `POST /api/chat/{sessionId}/close` → 204; 404/409 propagated from service

- [ ] **Step 1: Write the failing controller test**

```java
package com.testingai.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.MessageRequest;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private ChatService chatService;

    @Test
    void start_returnsSessionId() throws Exception {
        when(chatService.startSession()).thenReturn(new StartResponse("sess-123"));

        mockMvc.perform(post("/api/chat/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-123"));
    }

    @Test
    void message_returnsReply() throws Exception {
        when(chatService.sendMessage("sess-123", "hello"))
                .thenReturn(new MessageResponse("Hi there!", ConversationOutcome.OPEN, false));

        mockMvc.perform(post("/api/chat/sess-123/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageRequest("hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi there!"))
                .andExpect(jsonPath("$.outcome").value("OPEN"))
                .andExpect(jsonPath("$.escalated").value(false));
    }

    @Test
    void message_returns400_whenTextBlank() throws Exception {
        mockMvc.perform(post("/api/chat/sess-123/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void message_returns404_whenSessionUnknown() throws Exception {
        when(chatService.sendMessage("bad-id", "hi"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

        mockMvc.perform(post("/api/chat/bad-id/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void message_returns409_whenSessionClosed() throws Exception {
        when(chatService.sendMessage("closed-id", "hi"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "closed"));

        mockMvc.perform(post("/api/chat/closed-id/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hi\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void close_returns204() throws Exception {
        mockMvc.perform(post("/api/chat/sess-123/close"))
                .andExpect(status().isNoContent());

        verify(chatService).closeSession("sess-123");
    }
}
```

- [ ] **Step 2: Run to confirm the test fails**

```bash
mvn test -Dtest=ChatControllerTest -q 2>&1 | tail -5
```
Expected: compilation error.

- [ ] **Step 3: Implement `ChatController.java`**

```java
package com.testingai.chat.controller;

import com.testingai.chat.model.MessageRequest;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/start")
    public StartResponse start() {
        return chatService.startSession();
    }

    @PostMapping("/{sessionId}/message")
    public MessageResponse message(
            @PathVariable String sessionId,
            @RequestBody @Valid MessageRequest request) {
        return chatService.sendMessage(sessionId, request.text());
    }

    @PostMapping("/{sessionId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable String sessionId) {
        chatService.closeSession(sessionId);
    }
}
```

- [ ] **Step 4: Run all chat-service tests**

```bash
mvn test -q
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Create `embedding-service/README.md`**

```markdown
# Embedding Service

Manages a pgvector knowledge base for the Customer Support Bot. Accepts product documentation, embeds it via the OpenAI `text-embedding-3-small` model, and serves semantic similarity search results to the Chat Service.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/kb/ingest` | Embed and store a document chunk |
| `GET`  | `/api/kb/search?q=...&limit=3` | Retrieve top-K chunks by cosine similarity |

## Prerequisites

| What | Where |
|------|-------|
| `OPENAI_API_KEY` | https://platform.openai.com/api-keys |
| Postgres with pgvector | `docker compose up -d` from repo root |

## Running

```bash
export OPENAI_API_KEY=sk-...
cd ai/customer-support-bot/embedding-service
mvn spring-boot:run
```

App starts on **port 8087**.

## Seed knowledge base

```bash
curl -s -X POST http://localhost:8087/api/kb/ingest \
  -H "Content-Type: application/json" \
  -d '{"title": "Refund Policy", "content": "Customers may request a refund within 30 days of purchase by contacting support."}'

curl -s -X POST http://localhost:8087/api/kb/ingest \
  -H "Content-Type: application/json" \
  -d '{"title": "Shipping Times", "content": "Standard shipping takes 3-5 business days. Express shipping takes 1-2 business days."}'
```

## Search

```bash
curl "http://localhost:8087/api/kb/search?q=how+do+I+get+a+refund&limit=2"
```

## Build & test

```bash
mvn clean package   # build
mvn test            # unit tests (requires Docker for Testcontainers pgvector tests)
```
```

- [ ] **Step 6: Create `chat-service/README.md`**

```markdown
# Chat Service

Agentic customer support chatbot. Maintains multi-turn conversations, retrieves relevant context from the Embedding Service via pgvector, calls Claude (Anthropic Java SDK), applies rule-based escalation, and persists conversation outcomes to Postgres.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/chat/start` | Start a new session; returns `{ sessionId }` |
| `POST` | `/api/chat/{sessionId}/message` | Send a turn; returns `{ reply, outcome, escalated }` |
| `POST` | `/api/chat/{sessionId}/close` | Close session as ABANDONED (204) |

**Outcomes:** `OPEN` → `RESOLVED` (Claude ends with resolution phrase) / `ESCALATED` (keyword or turn limit hit) / `ABANDONED` (explicit close).

## Prerequisites

| What | Where |
|------|-------|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| Embedding Service running | See `embedding-service/README.md` |
| Postgres | `docker compose up -d` from repo root |

## Running

Start the Embedding Service first, then:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/customer-support-bot/chat-service
mvn spring-boot:run
```

App starts on **port 8086**.

## Full conversation example

```bash
# Start a session
SESSION=$(curl -s -X POST http://localhost:8086/api/chat/start | jq -r .sessionId)

# Send a message
curl -s -X POST http://localhost:8086/api/chat/$SESSION/message \
  -H "Content-Type: application/json" \
  -d '{"text": "How do I request a refund?"}' | jq .

# Close the session when done
curl -s -X POST http://localhost:8086/api/chat/$SESSION/close
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `chat.max-turns` | `10` | Turns before escalation |
| `chat.escalation-keywords` | `angry,lawsuit,unacceptable,refund,fraud` | Escalation trigger words |
| `chat.resolution-phrase` | `is there anything else` | Phrase Claude says when issue is resolved |
| `chat.embedding-service-url` | `http://localhost:8087` | URL of the Embedding Service |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model |

## Build & test

```bash
mvn clean package   # build
mvn test            # unit tests (no API keys needed)
```
```

- [ ] **Step 7: Run full test suite for both services**

From embedding-service:
```bash
cd ai/customer-support-bot/embedding-service && mvn test -q
```
Expected: BUILD SUCCESS

From chat-service:
```bash
cd ../chat-service && mvn test -q
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add ai/customer-support-bot/
git commit -m "feat(customer-support-bot): add ChatController, READMEs — complete two-service implementation"
```

---

## Self-Review

**Spec coverage:**
- ✅ Multi-turn conversation memory — `SessionState.history` (in-memory `ConcurrentHashMap`)
- ✅ RAG knowledge base — pgvector + `text-embedding-3-small` in embedding-service
- ✅ Escalation policy — `EscalationPolicy` (turn count + keywords)
- ✅ Outcome tracking — `OutcomeRepository` writing to `conversations` table
- ✅ `POST /api/kb/ingest` and `GET /api/kb/search` — Task 5
- ✅ `POST /api/chat/start`, `/message`, `/close` — Task 12
- ✅ 404 / 409 error handling — `ResponseStatusException` in `ChatService`, tested in Task 12
- ✅ Embedding Service unavailable → empty chunk list, not failure — `KnowledgeBaseClient`
- ✅ `conversations` table schema — Task 7
- ✅ `kb_chunk` table with ivfflat index — Task 2
- ✅ Both services on correct ports (8087 / 8086)

**Placeholder scan:** None found.

**Type consistency check:**
- `SearchResult(title, content, score)` — same record definition in both services (not shared; each has its own copy as they're independent)
- `ConversationOutcome` enum values: `OPEN/RESOLVED/ESCALATED/ABANDONED` — consistent across `SessionState`, `ChatService`, `OutcomeRepository`, `ChatControllerTest`
- `ChatProperties` fields: `maxTurns`, `escalationKeywords`, `resolutionPhrase`, `embeddingServiceUrl` — used consistently in `AppConfig`, `EscalationPolicy`, `ChatService`
- `SessionState` methods: `addMessage()`, `incrementTurnCount()`, `setOutcome()`, `getHistory()`, `getTurnCount()`, `getOutcome()`, `getSessionId()`, `getStartedAt()` — all referenced methods exist on the class as defined in Task 7

# Code Review Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot code-review agent that runs Checkstyle and PMD (as Claude tools) against Java files extracted from a unified diff, filters findings to changed lines only, and returns typed findings with AI-generated suggestions — with a GitHub webhook that posts inline PR review comments.

**Architecture:** `DiffParser` extracts file contents and changed-line sets from a unified diff. `ToolExecutor` writes files to a temp dir, dispatches `CheckstyleTool`/`PmdTool` (Checkstyle/PMD Java APIs), normalises file paths, and filters to changed lines. `ReviewService` runs an Anthropic agentic loop: Claude calls the two tools then synthesises typed `Finding[]` JSON. `WebhookController` verifies HMAC-SHA256, fetches the PR diff from GitHub, and `GitHubClient` posts the result as an inline GitHub review.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Anthropic Java SDK 2.40.1, Checkstyle 10.21.0, PMD 7.12.0, WireMock standalone 3.5.4, Lombok 1.18.38

## Global Constraints

- Module root: `ai/code-review-agent/spring-demo/`
- Base package: `com.testingai.reviewer`
- Spring Boot parent: `3.4.4`
- Java: `21` (`<java.version>21</java.version>`)
- Lombok: `1.18.38` — override via `<lombok.version>1.18.38</lombok.version>` property
- Anthropic SDK: `com.anthropic:anthropic-java:2.40.1`
- Checkstyle: `com.puppycrawl.tools:checkstyle:10.21.0`
- PMD: `net.sourceforge.pmd:pmd-java:7.12.0`
- WireMock: `org.wiremock:wiremock-standalone:3.5.4` (test scope)
- App port: `8085`
- Surefire: `<excludedGroups>integration</excludedGroups>` — exclude integration tests from `mvn test`
- Mockito mock maker: ByteBuddy subclass (required for Java 25 IDE compatibility with Anthropic SDK final classes) — `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` containing `mock-maker-subclass`
- `@EnableConfigurationProperties` for all three config records goes on `ReviewApplication`
- All instance fields assigned once must be `private final`; lifecycle-assigned fields (e.g. temp dirs) must be `private`
- No `.toString()` calls inside SLF4J `{}` placeholders or string concatenation
- Records for all model and config types; no boilerplate POJOs
- `SequencedCollection` API: `getFirst()` / `getLast()` instead of `get(0)` / `get(size()-1)`

---

### Task 1: Scaffold — pom.xml, models, config, application, resources

**Files:**
- Create: `ai/code-review-agent/spring-demo/pom.xml`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/ReviewApplication.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/config/AppConfig.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/config/AnthropicProperties.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/config/ReviewerProperties.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/config/GitHubProperties.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/model/ParsedDiff.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/model/RawFinding.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/model/Finding.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/model/ReviewRequest.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/model/ReviewResponse.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/model/WebhookPayload.java`
- Create: `ai/code-review-agent/spring-demo/src/main/resources/application.yml`
- Create: `ai/code-review-agent/spring-demo/src/main/resources/checkstyle/checkstyle.xml`
- Create: `ai/code-review-agent/spring-demo/src/main/resources/pmd/pmd-ruleset.xml`
- Create: `ai/code-review-agent/spring-demo/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`

**Interfaces:**
- Produces: `ParsedDiff(Map<String,String> fileContents, Map<String,Set<Integer>> changedLines)` — used by Task 2 (DiffParser) and Task 5 (ToolExecutor)
- Produces: `RawFinding(String file, String tool, String rule, String message, int line)` — used by Tasks 3, 4, 5
- Produces: `Finding(String severity, String file, int line, String message, String suggestion)` — used by Tasks 6, 7
- Produces: `ReviewRequest(@NotBlank String diff)` — used by Task 6
- Produces: `ReviewResponse(List<Finding> findings, String summary)` — used by Tasks 6, 7
- Produces: `WebhookPayload(String action, PullRequest pullRequest)` with nested records — used by Task 7
- Produces: `AnthropicProperties(String apiKey, String model)` — `@ConfigurationProperties("anthropic")`
- Produces: `ReviewerProperties(int maxIterations, String tempDir)` — `@ConfigurationProperties("reviewer")`
- Produces: `GitHubProperties(String token, String webhookSecret)` — `@ConfigurationProperties("github")`
- Produces: `AppConfig` — provides `AnthropicClient` bean and `gitHubRestClient` `RestClient` bean

- [ ] **Step 1: Create the module directory structure**

```bash
mkdir -p ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/{config,controller,service,tool,model}
mkdir -p ai/code-review-agent/spring-demo/src/main/resources/{checkstyle,pmd}
mkdir -p ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer
mkdir -p ai/code-review-agent/spring-demo/src/test/resources/mockito-extensions
```

- [ ] **Step 2: Create `pom.xml`**

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
    <artifactId>reviewer-spring-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>code-review-agent</name>

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
            <groupId>com.puppycrawl.tools</groupId>
            <artifactId>checkstyle</artifactId>
            <version>10.21.0</version>
        </dependency>
        <dependency>
            <groupId>net.sourceforge.pmd</groupId>
            <artifactId>pmd-java</artifactId>
            <version>7.12.0</version>
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

- [ ] **Step 3: Create `ReviewApplication.java`**

```java
package com.testingai.reviewer;

import com.testingai.reviewer.config.AnthropicProperties;
import com.testingai.reviewer.config.GitHubProperties;
import com.testingai.reviewer.config.ReviewerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AnthropicProperties.class, ReviewerProperties.class, GitHubProperties.class})
public class ReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `config/AnthropicProperties.java`**

```java
package com.testingai.reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("anthropic")
public record AnthropicProperties(String apiKey, String model) {}
```

- [ ] **Step 5: Create `config/ReviewerProperties.java`**

```java
package com.testingai.reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("reviewer")
public record ReviewerProperties(int maxIterations, String tempDir) {}
```

- [ ] **Step 6: Create `config/GitHubProperties.java`**

```java
package com.testingai.reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("github")
public record GitHubProperties(String token, String webhookSecret) {}
```

- [ ] **Step 7: Create `config/AppConfig.java`**

```java
package com.testingai.reviewer.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private final AnthropicProperties anthropicProps;
    private final GitHubProperties githubProps;

    public AppConfig(AnthropicProperties anthropicProps, GitHubProperties githubProps) {
        this.anthropicProps = anthropicProps;
        this.githubProps = githubProps;
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropicProps.apiKey())
                .build();
    }

    @Bean
    public RestClient gitHubRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + githubProps.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @PostConstruct
    public void validateConfig() {
        if (anthropicProps.apiKey() == null || anthropicProps.apiKey().isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }
        if (githubProps.token() == null || githubProps.token().isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN is not set");
        }
    }
}
```

- [ ] **Step 8: Create model records**

`model/ParsedDiff.java`:
```java
package com.testingai.reviewer.model;

import java.util.Map;
import java.util.Set;

public record ParsedDiff(
        Map<String, String> fileContents,
        Map<String, Set<Integer>> changedLines
) {}
```

`model/RawFinding.java`:
```java
package com.testingai.reviewer.model;

public record RawFinding(String file, String tool, String rule, String message, int line) {}
```

`model/Finding.java`:
```java
package com.testingai.reviewer.model;

public record Finding(String severity, String file, int line, String message, String suggestion) {}
```

`model/ReviewRequest.java`:
```java
package com.testingai.reviewer.model;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(@NotBlank String diff) {}
```

`model/ReviewResponse.java`:
```java
package com.testingai.reviewer.model;

import java.util.List;

public record ReviewResponse(List<Finding> findings, String summary) {}
```

`model/WebhookPayload.java`:
```java
package com.testingai.reviewer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
        String action,
        @JsonProperty("pull_request") PullRequest pullRequest
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(int number, Base base) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Base(Repo repo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repo(String name, Owner owner) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {}
}
```

- [ ] **Step 9: Create `src/main/resources/application.yml`**

```yaml
server:
  port: 8085

reviewer:
  max-iterations: 5
  temp-dir: ${java.io.tmpdir}

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

github:
  token: ${GITHUB_TOKEN:}
  webhook-secret: ${GITHUB_WEBHOOK_SECRET:}
```

Note: `${GITHUB_TOKEN:}` and `${GITHUB_WEBHOOK_SECRET:}` default to empty string so the app starts without these set during testing (the `@PostConstruct` check only validates `ANTHROPIC_API_KEY` and `GITHUB_TOKEN` — if webhook is unused, empty is fine; adjust `validateConfig()` if needed for your environment).

- [ ] **Step 10: Create `src/main/resources/checkstyle/checkstyle.xml`**

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <property name="severity" value="warning"/>
    <module name="TreeWalker">
        <module name="MethodLength">
            <property name="max" value="30"/>
        </module>
        <module name="ParameterNumber">
            <property name="max" value="7"/>
        </module>
        <module name="MagicNumber"/>
        <module name="EmptyBlock"/>
        <module name="NeedBraces"/>
        <module name="EqualsHashCode"/>
        <module name="UnusedImports"/>
    </module>
    <module name="FileLength">
        <property name="max" value="300"/>
    </module>
</module>
```

- [ ] **Step 11: Create `src/main/resources/pmd/pmd-ruleset.xml`**

```xml
<?xml version="1.0"?>
<ruleset name="Code Review Rules"
         xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0
             https://pmd.sourceforge.io/ruleset_2_0_0.xsd">
    <description>AI code review ruleset</description>
    <rule ref="category/java/bestpractices.xml/UnusedLocalVariable"/>
    <rule ref="category/java/bestpractices.xml/UnusedPrivateField"/>
    <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
    <rule ref="category/java/errorprone.xml/EqualsNull"/>
    <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
</ruleset>
```

- [ ] **Step 12: Create ByteBuddy mock maker file**

`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`:
```
mock-maker-subclass
```

- [ ] **Step 13: Verify the project compiles**

```bash
cd ai/code-review-agent/spring-demo
mvn compile
```

Expected: `BUILD SUCCESS`. If dependency conflicts arise (PMD or Checkstyle pulling in incompatible versions of shared libs like ASM or antlr), use `<exclusions>` in pom.xml to exclude the conflicting transitive dependency and let Spring Boot's managed version win.

- [ ] **Step 14: Commit**

```bash
git add ai/code-review-agent/spring-demo/
git commit -m "feat(reviewer): scaffold module — models, config, rulesets"
```

---

### Task 2: DiffParser

**Files:**
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/service/DiffParser.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/DiffParserTest.java`

**Interfaces:**
- Consumes: `ParsedDiff` from Task 1
- Produces: `DiffParser.parse(String diff) -> ParsedDiff` — used by Task 5 (ToolExecutor)

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.reviewer;

import com.testingai.reviewer.model.ParsedDiff;
import com.testingai.reviewer.service.DiffParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffParserTest {

    private final DiffParser parser = new DiffParser();

    private static final String DIFF = """
            diff --git a/src/main/java/com/example/Foo.java b/src/main/java/com/example/Foo.java
            index abc1234..def5678 100644
            --- a/src/main/java/com/example/Foo.java
            +++ b/src/main/java/com/example/Foo.java
            @@ -1,4 +1,6 @@
             package com.example;
            \s
            +import java.util.List;
            +
             public class Foo {
                 public void bar() {
            """;

    @Test
    void parsesChangedLines() {
        ParsedDiff result = parser.parse(DIFF);

        assertThat(result.changedLines()).containsKey("src/main/java/com/example/Foo.java");
        assertThat(result.changedLines().get("src/main/java/com/example/Foo.java"))
                .containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void parsesFileContents() {
        ParsedDiff result = parser.parse(DIFF);

        String content = result.fileContents().get("src/main/java/com/example/Foo.java");
        assertThat(content).contains("package com.example;");
        assertThat(content).contains("import java.util.List;");
        assertThat(content).contains("public class Foo {");
    }

    @Test
    void skipsNonJavaFiles() {
        String diff = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,2 @@
                 # Title
                +New line
                """;

        ParsedDiff result = parser.parse(diff);

        assertThat(result.fileContents()).isEmpty();
        assertThat(result.changedLines()).isEmpty();
    }

    @Test
    void handlesMultipleFiles() {
        String diff = """
                diff --git a/Foo.java b/Foo.java
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,1 +1,2 @@
                 class Foo {}
                +// added
                diff --git a/Bar.java b/Bar.java
                --- a/Bar.java
                +++ b/Bar.java
                @@ -1,1 +1,2 @@
                 class Bar {}
                +// added
                """;

        ParsedDiff result = parser.parse(diff);

        assertThat(result.fileContents()).containsKeys("Foo.java", "Bar.java");
        assertThat(result.changedLines().get("Foo.java")).contains(2);
        assertThat(result.changedLines().get("Bar.java")).contains(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd ai/code-review-agent/spring-demo
mvn test -Dtest=DiffParserTest
```

Expected: FAIL — `DiffParser` does not exist.

- [ ] **Step 3: Implement `DiffParser`**

```java
package com.testingai.reviewer.service;

import com.testingai.reviewer.model.ParsedDiff;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiffParser {

    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    public ParsedDiff parse(String diff) {
        Map<String, StringBuilder> contents = new HashMap<>();
        Map<String, Set<Integer>> changedLines = new HashMap<>();
        String currentFile = null;
        int currentLine = 0;

        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("+++ b/")) {
                String filename = line.substring(6);
                if (!filename.endsWith(".java")) {
                    currentFile = null;
                    continue;
                }
                currentFile = filename;
                contents.put(currentFile, new StringBuilder());
                changedLines.put(currentFile, new HashSet<>());
                currentLine = 0;
                continue;
            }
            if (line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ")) {
                continue;
            }
            if (currentFile == null) continue;

            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.find()) {
                currentLine = Integer.parseInt(hunkMatcher.group(1));
                continue;
            }

            if (line.startsWith("+") && !line.startsWith("+++")) {
                String content = line.substring(1);
                contents.get(currentFile).append(content).append('\n');
                changedLines.get(currentFile).add(currentLine);
                currentLine++;
            } else if (line.startsWith(" ")) {
                String content = line.substring(1);
                contents.get(currentFile).append(content).append('\n');
                currentLine++;
            }
            // lines starting with '-' are deleted — skip, don't advance currentLine
        }

        Map<String, String> fileContents = new HashMap<>();
        contents.forEach((file, sb) -> fileContents.put(file, sb.toString()));
        return new ParsedDiff(fileContents, changedLines);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=DiffParserTest
```

Expected: 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add ai/code-review-agent/spring-demo/src/
git commit -m "feat(reviewer): add DiffParser with tests"
```

---

### Task 3: CheckstyleTool

**Files:**
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/tool/CheckstyleTool.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/CheckstyleToolTest.java`

**Interfaces:**
- Consumes: `RawFinding` from Task 1
- Produces:
  - `CheckstyleTool.analyse(Path tempDir) -> List<RawFinding>` — used by Task 5
  - `CheckstyleTool.definition() -> Tool` — used by Tasks 5, 6

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.reviewer;

import com.testingai.reviewer.model.RawFinding;
import com.testingai.reviewer.tool.CheckstyleTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckstyleToolTest {

    private final CheckstyleTool tool = new CheckstyleTool();

    @Test
    void detectsMethodLengthViolation(@TempDir Path tempDir) throws IOException {
        // Build a method that exceeds 30 lines
        StringBuilder sb = new StringBuilder("public class LongMethod {\n    public void tooLong() {\n");
        for (int i = 1; i <= 31; i++) {
            sb.append("        int v").append(i).append(" = ").append(i).append(";\n");
        }
        sb.append("    }\n}\n");
        Files.writeString(tempDir.resolve("LongMethod.java"), sb.toString(), StandardCharsets.UTF_8);

        List<RawFinding> findings = tool.analyse(tempDir);

        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f ->
                f.tool().equals("checkstyle") && f.rule().contains("MethodLength"));
    }

    @Test
    void returnsEmptyForCleanCode(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Clean.java"), """
                public class Clean {
                    public void ok() {
                        System.out.println("hello");
                    }
                }
                """, StandardCharsets.UTF_8);

        List<RawFinding> findings = tool.analyse(tempDir);

        assertThat(findings).isEmpty();
    }

    @Test
    void toolDefinitionHasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("run_checkstyle");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd ai/code-review-agent/spring-demo
mvn test -Dtest=CheckstyleToolTest
```

Expected: FAIL — `CheckstyleTool` does not exist.

- [ ] **Step 3: Implement `CheckstyleTool`**

```java
package com.testingai.reviewer.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.testingai.reviewer.model.RawFinding;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

@Component
public class CheckstyleTool {

    public Tool definition() {
        return Tool.builder()
                .name("run_checkstyle")
                .description("Run Checkstyle static analysis on Java files extracted from the diff. Returns JSON array of findings on changed lines.")
                .inputSchema(Tool.InputSchema.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                "diff", Map.of("type", "string",
                                        "description", "The unified diff of the pull request"))))
                        .putAdditionalProperty("required", JsonValue.from(List.of("diff")))
                        .build())
                .build();
    }

    public List<RawFinding> analyse(Path tempDir) {
        List<File> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> javaFiles.add(p.toFile()));
        } catch (IOException e) {
            return List.of();
        }
        if (javaFiles.isEmpty()) return List.of();

        try (InputStream stream = getClass().getResourceAsStream("/checkstyle/checkstyle.xml")) {
            Configuration config = ConfigurationLoader.loadConfiguration(
                    new InputSource(stream),
                    new PropertiesExpander(new Properties()));

            List<AuditEvent> events = new ArrayList<>();
            Checker checker = new Checker();
            checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
            checker.configure(config);
            checker.addListener(new AuditListener() {
                @Override public void auditStarted(AuditEvent e) {}
                @Override public void auditFinished(AuditEvent e) {}
                @Override public void fileStarted(AuditEvent e) {}
                @Override public void fileFinished(AuditEvent e) {}
                @Override public void addError(AuditEvent e) { events.add(e); }
                @Override public void addException(AuditEvent e, Throwable t) {}
            });
            checker.process(javaFiles);
            checker.destroy();

            return events.stream()
                    .map(e -> new RawFinding(e.getFileName(), "checkstyle",
                            e.getSourceName(), e.getMessage(), e.getLine()))
                    .toList();
        } catch (CheckstyleException | IOException e) {
            return List.of();
        }
    }
}
```

Add the missing `java.io.File` import — `File` is used for `javaFiles` list. Update the list type:

```java
import java.io.File;
// ...
List<File> javaFiles = new ArrayList<>();
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=CheckstyleToolTest
```

Expected: 3 tests passing. If `ConfigurationLoader` signature differs in Checkstyle 10.21.0, check Checkstyle JavaDoc for the exact `loadConfiguration` overload that accepts `InputSource` and `PropertyResolver`.

- [ ] **Step 5: Commit**

```bash
git add ai/code-review-agent/spring-demo/src/
git commit -m "feat(reviewer): add CheckstyleTool with tests"
```

---

### Task 4: PmdTool

**Files:**
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/tool/PmdTool.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/PmdToolTest.java`

**Interfaces:**
- Consumes: `RawFinding` from Task 1
- Produces:
  - `PmdTool.analyse(Path tempDir) -> List<RawFinding>` — used by Task 5
  - `PmdTool.definition() -> Tool` — used by Tasks 5, 6

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.reviewer;

import com.testingai.reviewer.model.RawFinding;
import com.testingai.reviewer.tool.PmdTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PmdToolTest {

    private final PmdTool tool = new PmdTool();

    @Test
    void detectsUnusedLocalVariable(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Unused.java"), """
                public class Unused {
                    public void foo() {
                        String unused = "hello";
                    }
                }
                """, StandardCharsets.UTF_8);

        List<RawFinding> findings = tool.analyse(tempDir);

        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f ->
                f.tool().equals("pmd") && f.rule().contains("UnusedLocalVariable"));
    }

    @Test
    void returnsEmptyForCleanCode(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Clean.java"), """
                public class Clean {
                    public void ok() {
                        System.out.println("hello");
                    }
                }
                """, StandardCharsets.UTF_8);

        List<RawFinding> findings = tool.analyse(tempDir);

        assertThat(findings).isEmpty();
    }

    @Test
    void toolDefinitionHasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("run_pmd");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd ai/code-review-agent/spring-demo
mvn test -Dtest=PmdToolTest
```

Expected: FAIL — `PmdTool` does not exist.

- [ ] **Step 3: Implement `PmdTool`**

```java
package com.testingai.reviewer.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.testingai.reviewer.model.RawFinding;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.reporting.Report;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Component
public class PmdTool {

    public Tool definition() {
        return Tool.builder()
                .name("run_pmd")
                .description("Run PMD static analysis on Java files extracted from the diff. Returns JSON array of findings on changed lines.")
                .inputSchema(Tool.InputSchema.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                "diff", Map.of("type", "string",
                                        "description", "The unified diff of the pull request"))))
                        .putAdditionalProperty("required", JsonValue.from(List.of("diff")))
                        .build())
                .build();
    }

    public List<RawFinding> analyse(Path tempDir) {
        try {
            Path rulesetFile = extractRuleset();
            PMDConfiguration config = new PMDConfiguration();
            config.addRuleSet(rulesetFile.toAbsolutePath().toString());

            try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
                pmd.files().addDirectory(tempDir);
                Report report = pmd.performAnalysisAndCollectReport();
                return report.getViolations().stream()
                        .map(v -> new RawFinding(
                                v.getFileId().getAbsolutePath(),
                                "pmd",
                                v.getRule().getName(),
                                v.getDescription(),
                                v.getBeginLine()))
                        .toList();
            } finally {
                Files.deleteIfExists(rulesetFile);
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    private Path extractRuleset() throws IOException {
        Path tmp = Files.createTempFile("pmd-ruleset-", ".xml");
        try (InputStream is = getClass().getResourceAsStream("/pmd/pmd-ruleset.xml")) {
            Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }
}
```

Note: `FileId.getAbsolutePath()` is the PMD 7.x API method — if the build shows a different method name on `FileId`, check the PMD 7.x JavaDoc. Common alternatives: `getFileName()`, `getOriginalPath()`.

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=PmdToolTest
```

Expected: 3 tests passing.

- [ ] **Step 5: Commit**

```bash
git add ai/code-review-agent/spring-demo/src/
git commit -m "feat(reviewer): add PmdTool with tests"
```

---

### Task 5: ToolExecutor

**Files:**
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/tool/ToolExecutor.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/ToolExecutorTest.java`

**Interfaces:**
- Consumes: `CheckstyleTool.analyse(Path)`, `PmdTool.analyse(Path)`, `DiffParser.parse(String)`, all model records from Task 1
- Produces: `ToolExecutor.execute(String toolName, JsonValue input) -> String` (JSON of `List<RawFinding>` or error JSON) — used by Task 6

- [ ] **Step 1: Write the failing test**

Key design notes for this test:
- **Do NOT use `@InjectMocks`** — `ToolExecutor` requires `ObjectMapper` which must be a real instance, not a mock. Use manual construction in `@BeforeEach`.
- **Use `thenAnswer` for path-dependent mocks** — the tool mocks must return findings whose `file` field is an absolute path *under the actual temp dir* that `ToolExecutor` creates at runtime. Use `InvocationOnMock.getArgument(0)` to capture the real `Path` and build a correct absolute path from it. Without this, the normalization step (`tempDir.relativize(finding.file)`) produces the wrong relative path and the diff-aware filter drops all findings.

```java
package com.testingai.reviewer;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.reviewer.model.ParsedDiff;
import com.testingai.reviewer.model.RawFinding;
import com.testingai.reviewer.service.DiffParser;
import com.testingai.reviewer.tool.CheckstyleTool;
import com.testingai.reviewer.tool.PmdTool;
import com.testingai.reviewer.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock private CheckstyleTool checkstyleTool;
    @Mock private PmdTool pmdTool;
    @Mock private DiffParser diffParser;

    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor(checkstyleTool, pmdTool, diffParser, new ObjectMapper());
    }

    @Test
    void executeCheckstyleReturnsFindingsJson() {
        String diff = "diff content";
        ParsedDiff parsed = new ParsedDiff(
                Map.of("Foo.java", "class Foo {}"),
                Map.of("Foo.java", Set.of(1)));
        when(diffParser.parse(diff)).thenReturn(parsed);
        // Return a finding whose absolute path is under the actual temp dir passed to analyse()
        when(checkstyleTool.analyse(any(Path.class))).thenAnswer(inv -> {
            Path dir = inv.getArgument(0);
            return List.of(new RawFinding(dir.resolve("Foo.java").toAbsolutePath().toString(),
                    "checkstyle", "MethodLength", "Too long", 1));
        });

        JsonValue input = JsonValue.from(Map.of("diff", diff));
        String result = executor.execute("run_checkstyle", input);

        assertThat(result).contains("checkstyle");
        assertThat(result).contains("MethodLength");
        assertThat(result).doesNotContain("\"error\"");
    }

    @Test
    void filtersToChangedLinesOnly() {
        String diff = "diff";
        ParsedDiff parsed = new ParsedDiff(
                Map.of("Foo.java", "class Foo {}"),
                Map.of("Foo.java", Set.of(5)));  // only line 5 is changed
        when(diffParser.parse(diff)).thenReturn(parsed);
        when(checkstyleTool.analyse(any(Path.class))).thenAnswer(inv -> {
            Path dir = inv.getArgument(0);
            String path = dir.resolve("Foo.java").toAbsolutePath().toString();
            return List.of(
                    new RawFinding(path, "checkstyle", "Rule", "msg", 3),  // not changed
                    new RawFinding(path, "checkstyle", "Rule", "msg", 5)); // changed
        });

        JsonValue input = JsonValue.from(Map.of("diff", diff));
        String result = executor.execute("run_checkstyle", input);

        // line 3 is filtered; line 5 survives — exactly one finding in the result
        assertThat(result).startsWith("[");
        assertThat(result).containsOnlyOnce("\"line\":5");
        assertThat(result).doesNotContain("\"line\":3");
    }

    @Test
    void returnsErrorJsonForUnknownTool() {
        when(diffParser.parse(any())).thenReturn(
                new ParsedDiff(Map.of(), Map.of()));
        JsonValue input = JsonValue.from(Map.of("diff", "x"));
        String result = executor.execute("unknown_tool", input);
        assertThat(result).contains("\"error\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd ai/code-review-agent/spring-demo
mvn test -Dtest=ToolExecutorTest
```

Expected: FAIL — `ToolExecutor` does not exist.

- [ ] **Step 3: Implement `ToolExecutor`**

```java
package com.testingai.reviewer.tool;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.reviewer.model.ParsedDiff;
import com.testingai.reviewer.model.RawFinding;
import com.testingai.reviewer.service.DiffParser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class ToolExecutor {

    private final CheckstyleTool checkstyleTool;
    private final PmdTool pmdTool;
    private final DiffParser diffParser;
    private final ObjectMapper objectMapper;

    public ToolExecutor(CheckstyleTool checkstyleTool, PmdTool pmdTool,
                        DiffParser diffParser, ObjectMapper objectMapper) {
        this.checkstyleTool = checkstyleTool;
        this.pmdTool = pmdTool;
        this.diffParser = diffParser;
        this.objectMapper = objectMapper;
    }

    public String execute(String toolName, JsonValue input) {
        try {
            String diff = extractDiff(input);
            ParsedDiff parsed = diffParser.parse(diff);
            return switch (toolName) {
                case "run_checkstyle" -> runAndFilter(parsed, checkstyleTool::analyse);
                case "run_pmd" -> runAndFilter(parsed, pmdTool::analyse);
                default -> errorJson("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String runAndFilter(ParsedDiff parsed,
                                AnalyserFunction analyser) throws Exception {
        Path tempDir = Files.createTempDirectory("review-");
        try {
            writeTempFiles(tempDir, parsed.fileContents());
            List<RawFinding> raw = analyser.apply(tempDir);
            List<RawFinding> filtered = raw.stream()
                    .filter(f -> {
                        String relativePath = normalize(tempDir, f.file());
                        Set<Integer> lines = parsed.changedLines()
                                .getOrDefault(relativePath, Set.of());
                        return lines.contains(f.line());
                    })
                    .map(f -> new RawFinding(
                            normalize(tempDir, f.file()),
                            f.tool(), f.rule(), f.message(), f.line()))
                    .toList();
            return objectMapper.writeValueAsString(filtered);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void writeTempFiles(Path tempDir, Map<String, String> fileContents) throws IOException {
        for (var entry : fileContents.entrySet()) {
            Path filePath = tempDir.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    private String normalize(Path tempDir, String absolutePath) {
        try {
            return tempDir.relativize(Path.of(absolutePath)).toString()
                    .replace(File.separatorChar, '/');
        } catch (IllegalArgumentException e) {
            return absolutePath;
        }
    }

    private String extractDiff(JsonValue input) throws IOException {
        JsonNode node = objectMapper.readTree(input.toString());
        return node.get("diff").asText();
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialisation failure\"}";
        }
    }

    @FunctionalInterface
    private interface AnalyserFunction {
        List<RawFinding> apply(Path tempDir) throws Exception;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=ToolExecutorTest
```

Expected: 3 tests passing.

- [ ] **Step 5: Run all tests so far**

```bash
mvn test
```

Expected: all tests passing.

- [ ] **Step 6: Commit**

```bash
git add ai/code-review-agent/spring-demo/src/
git commit -m "feat(reviewer): add ToolExecutor with diff-aware filtering and tests"
```

---

### Task 6: ReviewService + ReviewController

**Files:**
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/service/ReviewService.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/controller/ReviewController.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/ReviewServiceTest.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/ReviewControllerTest.java`

**Interfaces:**
- Consumes: `ToolExecutor.execute(String, JsonValue)`, `CheckstyleTool.definition()`, `PmdTool.definition()`, all model records, `AnthropicClient`
- Produces: `ReviewService.analyse(String diff) -> ReviewResponse` — used by Task 7

- [ ] **Step 1: Write the failing tests**

`ReviewServiceTest.java`:
```java
package com.testingai.reviewer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.Messages;
import com.testingai.reviewer.config.AnthropicProperties;
import com.testingai.reviewer.config.ReviewerProperties;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.ReviewService;
import com.testingai.reviewer.tool.CheckstyleTool;
import com.testingai.reviewer.tool.PmdTool;
import com.testingai.reviewer.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private AnthropicClient anthropic;
    private ToolExecutor toolExecutor;
    private CheckstyleTool checkstyleTool;
    private PmdTool pmdTool;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        anthropic = Mockito.mock(AnthropicClient.class);
        toolExecutor = Mockito.mock(ToolExecutor.class);
        checkstyleTool = Mockito.mock(CheckstyleTool.class);
        pmdTool = Mockito.mock(PmdTool.class);

        AnthropicProperties anthropicProps = new AnthropicProperties("test-key", "claude-sonnet-4-6");
        ReviewerProperties reviewerProps = new ReviewerProperties(5, System.getProperty("java.io.tmpdir"));

        service = new ReviewService(anthropic, toolExecutor, checkstyleTool, pmdTool,
                anthropicProps, reviewerProps);
    }

    @Test
    void returnsEmptyFindingsWhenClaudeReturnsNoToolCalls() {
        Messages messages = Mockito.mock(Messages.class);
        Message response = Mockito.mock(Message.class);
        when(anthropic.messages()).thenReturn(messages);
        when(messages.create(any())).thenReturn(response);
        when(response.content()).thenReturn(List.of());

        ReviewResponse result = service.analyse("diff content");

        assertThat(result).isNotNull();
        assertThat(result.findings()).isEmpty();
    }
}
```

`ReviewControllerTest.java`:
```java
package com.testingai.reviewer;

import com.testingai.reviewer.controller.ReviewController;
import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ReviewService reviewService;

    @Test
    void analyseReturnsFindings() throws Exception {
        when(reviewService.analyse(anyString())).thenReturn(
                new ReviewResponse(
                        List.of(new Finding("WARNING", "Foo.java", 3, "Too long", "Split the method.")),
                        "1 warning found."));

        mockMvc.perform(post("/api/review/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diff\": \"diff content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("1 warning found."))
                .andExpect(jsonPath("$.findings[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.findings[0].file").value("Foo.java"));
    }

    @Test
    void returnsValidationErrorForMissingDiff() throws Exception {
        mockMvc.perform(post("/api/review/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diff\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd ai/code-review-agent/spring-demo
mvn test -Dtest=ReviewServiceTest,ReviewControllerTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `ReviewService`**

```java
package com.testingai.reviewer.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.reviewer.config.AnthropicProperties;
import com.testingai.reviewer.config.ReviewerProperties;
import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.tool.CheckstyleTool;
import com.testingai.reviewer.tool.PmdTool;
import com.testingai.reviewer.tool.ToolExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final AnthropicClient anthropic;
    private final ToolExecutor toolExecutor;
    private final CheckstyleTool checkstyleTool;
    private final PmdTool pmdTool;
    private final AnthropicProperties anthropicProps;
    private final ReviewerProperties reviewerProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewService(AnthropicClient anthropic, ToolExecutor toolExecutor,
                         CheckstyleTool checkstyleTool, PmdTool pmdTool,
                         AnthropicProperties anthropicProps, ReviewerProperties reviewerProps) {
        this.anthropic = anthropic;
        this.toolExecutor = toolExecutor;
        this.checkstyleTool = checkstyleTool;
        this.pmdTool = pmdTool;
        this.anthropicProps = anthropicProps;
        this.reviewerProps = reviewerProps;
    }

    public ReviewResponse analyse(String diff) {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("""
                        You are a Java code reviewer. The unified diff below shows only the changed lines of a pull request.
                        Call run_checkstyle and run_pmd with the diff to get static analysis findings on those changed lines.
                        Then synthesise ALL findings (deduplicated) into a JSON array with this exact structure:
                        [{"severity":"ERROR|WARNING|INFO","file":"...","line":N,"message":"...","suggestion":"..."}]
                        Add a concrete, actionable fix suggestion for each finding.
                        After the JSON array, write a one-sentence summary on a new line.
                        <diff>
                        """ + diff + """
                        </diff>
                        """)
                .build());

        int iterations = 0;
        while (iterations < reviewerProps.maxIterations()) {
            Message response = anthropic.messages().create(
                    MessageCreateParams.builder()
                            .model(anthropicProps.model())
                            .maxTokens(4096)
                            .messages(messages)
                            .addTool(checkstyleTool.definition())
                            .addTool(pmdTool.definition())
                            .toolChoice(ToolChoiceAuto.builder().build())
                            .build());

            List<ContentBlockParam> assistantBlocks = response.content().stream()
                    .map(ContentBlock::toParam)
                    .filter(Objects::nonNull)
                    .toList();
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantBlocks)
                    .build());

            iterations++;

            List<ToolUseBlock> toolCalls = response.content().stream()
                    .filter(ContentBlock::isToolUse)
                    .map(ContentBlock::asToolUse)
                    .toList();

            if (toolCalls.isEmpty()) {
                String text = response.content().stream()
                        .filter(ContentBlock::isText)
                        .map(ContentBlock::asText)
                        .map(TextBlock::text)
                        .collect(Collectors.joining(""));
                return parseResponse(text);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ToolUseBlock call : toolCalls) {
                String output = toolExecutor.execute(call.name(), call._input());
                toolResults.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(call.id())
                                .content(output)
                                .build()));
            }
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }

        return new ReviewResponse(List.of(), "Iteration cap reached without synthesis.");
    }

    private ReviewResponse parseResponse(String text) {
        try {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) {
                return new ReviewResponse(List.of(), text.trim());
            }
            String json = text.substring(start, end + 1);
            String summary = text.substring(end + 1).trim();
            List<Finding> findings = objectMapper.readValue(json, new TypeReference<>() {});
            return new ReviewResponse(findings, summary.isEmpty() ? "Analysis complete." : summary);
        } catch (Exception e) {
            return new ReviewResponse(List.of(), text.trim());
        }
    }
}
```

- [ ] **Step 4: Implement `ReviewController`**

```java
package com.testingai.reviewer.controller;

import com.testingai.reviewer.model.ReviewRequest;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/analyse")
    public ReviewResponse analyse(@RequestBody @Valid ReviewRequest request) {
        return reviewService.analyse(request.diff());
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=ReviewServiceTest,ReviewControllerTest
```

Expected: tests passing.

- [ ] **Step 6: Run full test suite**

```bash
mvn test
```

Expected: all tests passing.

- [ ] **Step 7: Commit**

```bash
git add ai/code-review-agent/spring-demo/src/
git commit -m "feat(reviewer): add ReviewService agentic loop and ReviewController"
```

---

### Task 7: WebhookController + GitHubClient

**Files:**
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/service/GitHubClient.java`
- Create: `ai/code-review-agent/spring-demo/src/main/java/com/testingai/reviewer/controller/WebhookController.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/GitHubClientTest.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/WebhookControllerTest.java`
- Create: `ai/code-review-agent/spring-demo/src/test/java/com/testingai/reviewer/ReviewIntegrationTest.java`

**Interfaces:**
- Consumes: `ReviewService.analyse(String)`, `ReviewResponse`, `Finding`, `WebhookPayload`, `GitHubProperties`
- Produces: complete application — no further tasks depend on this

- [ ] **Step 1: Write the failing tests**

`GitHubClientTest.java`:
```java
package com.testingai.reviewer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.GitHubClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubClientTest {

    private WireMockServer wireMock;
    private GitHubClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Authorization", "Bearer test-token")
                .build();
        client = new GitHubClient(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetchPrDiffSendsCorrectAcceptHeader() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/owner/repo/pulls/42"))
                .withHeader("Accept", equalTo("application/vnd.github.diff"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("diff --git a/Foo.java b/Foo.java\n")));

        String diff = client.fetchPrDiff("owner", "repo", 42);

        assertThat(diff).contains("diff --git");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42"))
                .withHeader("Accept", equalTo("application/vnd.github.diff")));
    }

    @Test
    void postReviewSendsCommentEvent() {
        wireMock.stubFor(post(urlPathEqualTo("/repos/owner/repo/pulls/42/reviews"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        ReviewResponse review = new ReviewResponse(
                List.of(new Finding("WARNING", "Foo.java", 3, "Too long", "Split it.")),
                "1 warning.");

        client.postReview("owner", "repo", 42, review);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/reviews"))
                .withRequestBody(matchingJsonPath("$.event", equalTo("COMMENT")))
                .withRequestBody(matchingJsonPath("$.comments[0].path", equalTo("Foo.java")))
                .withRequestBody(matchingJsonPath("$.comments[0].line", equalTo("3"))));
    }

    @Test
    void postReviewSendsRequestChangesForErrors() {
        wireMock.stubFor(post(urlPathEqualTo("/repos/owner/repo/pulls/1/reviews"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        ReviewResponse review = new ReviewResponse(
                List.of(new Finding("ERROR", "Bar.java", 5, "Null check missing", "Add null guard.")),
                "1 error.");

        client.postReview("owner", "repo", 1, review);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/1/reviews"))
                .withRequestBody(matchingJsonPath("$.event", equalTo("REQUEST_CHANGES"))));
    }
}
```

`WebhookControllerTest.java`:

**Important:** `@WebMvcTest` does NOT load `@ConfigurationProperties` beans. `WebhookController` needs `GitHubProperties` for HMAC verification. Fix: add an inner `@TestConfiguration` class that explicitly enables `GitHubProperties`, combined with `@TestPropertySource` to supply the value. This is the standard Spring Boot pattern for `@ConfigurationProperties` in `@WebMvcTest`.

```java
package com.testingai.reviewer;

import com.testingai.reviewer.config.GitHubProperties;
import com.testingai.reviewer.controller.WebhookController;
import com.testingai.reviewer.service.GitHubClient;
import com.testingai.reviewer.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import(WebhookControllerTest.TestConfig.class)
@TestPropertySource(properties = {
        "github.webhook-secret=test-webhook-secret",
        "github.token=test-token"
})
class WebhookControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(GitHubProperties.class)
    static class TestConfig {}

    @Autowired private MockMvc mockMvc;
    @MockBean private ReviewService reviewService;
    @MockBean private GitHubClient gitHubClient;

    private static final String SECRET = "test-webhook-secret";
    private static final String PAYLOAD = """
            {"action":"opened","pull_request":{"number":1,"base":{"repo":{"name":"repo","owner":{"login":"owner"}}}}}
            """;

    @Test
    void acceptsValidSignature() throws Exception {
        String sig = "sha256=" + computeHmac(SECRET, PAYLOAD);
        mockMvc.perform(post("/api/review/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sig)
                        .header("X-GitHub-Event", "pull_request")
                        .content(PAYLOAD))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingSignature() throws Exception {
        mockMvc.perform(post("/api/review/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWrongSignature() throws Exception {
        mockMvc.perform(post("/api/review/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=wrongsig")
                        .content(PAYLOAD))
                .andExpect(status().isForbidden());
    }

    private static String computeHmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
```

`ReviewIntegrationTest.java`:
```java
package com.testingai.reviewer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ReviewIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void analyseReturnsFindings() throws Exception {
        String diff = """
                diff --git a/Foo.java b/Foo.java
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,6 @@
                 public class Foo {
                +    public void bar() {
                +        String unused = "hello";
                +    }
                 }
                """;

        mockMvc.perform(post("/api/review/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diff\": \"" + diff.replace("\"", "\\\"").replace("\n", "\\n") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings").isArray())
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd ai/code-review-agent/spring-demo
mvn test -Dtest=GitHubClientTest,WebhookControllerTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `GitHubClient`**

```java
package com.testingai.reviewer.service;

import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {

    private final RestClient restClient;

    public GitHubClient(@Qualifier("gitHubRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String fetchPrDiff(String owner, String repo, int prNumber) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                .header("Accept", "application/vnd.github.diff")
                .retrieve()
                .body(String.class);
    }

    public void postReview(String owner, String repo, int prNumber, ReviewResponse review) {
        boolean hasErrors = review.findings().stream()
                .anyMatch(f -> "ERROR".equals(f.severity()));

        List<Map<String, Object>> comments = review.findings().stream()
                .map(f -> Map.<String, Object>of(
                        "path", f.file(),
                        "line", f.line(),
                        "body", "**%s**: %s\n\n**Suggestion**: %s"
                                .formatted(f.severity(), f.message(), f.suggestion())))
                .toList();

        Map<String, Object> body = Map.of(
                "event", hasErrors ? "REQUEST_CHANGES" : "COMMENT",
                "body", review.summary(),
                "comments", comments);

        restClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{number}/reviews", owner, repo, prNumber)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
```

- [ ] **Step 4: Implement `WebhookController`**

The `WebhookController` reads the raw request body as bytes before Spring touches it. Inject `GitHubProperties` via the constructor (not `@Value`) so it follows the config-record pattern.

```java
package com.testingai.reviewer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.reviewer.config.GitHubProperties;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.model.WebhookPayload;
import com.testingai.reviewer.service.GitHubClient;
import com.testingai.reviewer.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/review")
public class WebhookController {

    private final ReviewService reviewService;
    private final GitHubClient gitHubClient;
    private final GitHubProperties githubProps;
    private final ObjectMapper objectMapper;

    public WebhookController(ReviewService reviewService, GitHubClient gitHubClient,
                              GitHubProperties githubProps, ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.gitHubClient = gitHubClient;
        this.githubProps = githubProps;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (!verifySignature(body, signature)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            WebhookPayload payload = objectMapper.readValue(body, WebhookPayload.class);
            if (payload.pullRequest() == null) return ResponseEntity.ok().build();
            if (!"opened".equals(payload.action()) && !"synchronize".equals(payload.action())) {
                return ResponseEntity.ok().build();
            }

            String owner = payload.pullRequest().base().repo().owner().login();
            String repo = payload.pullRequest().base().repo().name();
            int prNumber = payload.pullRequest().number();

            String diff = gitHubClient.fetchPrDiff(owner, repo, prNumber);
            ReviewResponse review = reviewService.analyse(diff);
            gitHubClient.postReview(owner, repo, prNumber, review);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean verifySignature(byte[] body, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) return false;
        String secret = githubProps.webhookSecret();
        if (secret == null || secret.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }
}
```

The `WebhookControllerTest` in Step 1 already includes `@TestConfiguration` + `@EnableConfigurationProperties(GitHubProperties.class)` and `@TestPropertySource` — this is what makes `GitHubProperties` available in the `@WebMvcTest` context. No additional annotation setup is needed.
```

Add this annotation to `WebhookControllerTest` before the class declaration.

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=GitHubClientTest,WebhookControllerTest
```

Expected: all tests passing.

- [ ] **Step 6: Run full test suite**

```bash
mvn test
```

Expected: all tests passing.

- [ ] **Step 7: Commit**

```bash
git add ai/code-review-agent/spring-demo/src/
git commit -m "feat(reviewer): add WebhookController, GitHubClient, and integration test stub"
```

---

### Task 8: README

**Files:**
- Modify: `ai/code-review-agent/README.md` (replace the "Future Investigation" stub with the real implementation guide)

**Interfaces:**
- Consumes: nothing — documentation only

- [ ] **Step 1: Replace `ai/code-review-agent/README.md` with the implementation README**

```markdown
# Code Review Agent

## Concept

An agent that accepts a unified diff via REST (or GitHub PR webhook), runs Checkstyle and PMD as Claude tools against the changed Java files, filters findings to only lines touched by the PR, and returns typed findings — each with an AI-generated fix suggestion.

## Key Capabilities

- **Diff-aware analysis** — only findings on lines actually changed by the PR are surfaced; pre-existing issues on untouched lines are silently dropped
- **Tool-wrapped static analysis** — Checkstyle and PMD are Claude tool calls; Claude controls invocation and synthesises their raw output into typed `[{severity, file, line, message, suggestion}]` JSON
- **AI-generated suggestions** — neither Checkstyle nor PMD produces fix suggestions; Claude adds a concrete, actionable suggestion for every finding
- **GitHub webhook** — triggers on PR open/update, fetches the diff from GitHub, posts the review as inline comments; `REQUEST_CHANGES` if any `ERROR` severity finding is present, otherwise `COMMENT`
- **HMAC-SHA256 verification** — webhook requests are verified with constant-time signature comparison before any processing

---

A Spring Boot demo showing how to combine deterministic static analysis tools with an LLM synthesis step inside an agentic loop.

## How it works

```
POST /api/review/analyse  {"diff": "..."}
        │
        ▼
  ReviewService — agentic loop
        │
        ├─ 1. Send diff + tool definitions to Claude
        │
        ├─ 2. Claude calls run_checkstyle and run_pmd with the diff
        │         │
        │    ToolExecutor:
        │    ├─ DiffParser.parse(diff) → fileContents, changedLines
        │    ├─ Write Java files to temp dir
        │    ├─ CheckstyleTool / PmdTool → raw findings (absolute paths)
        │    ├─ Filter: keep only findings where line ∈ changedLines[file]
        │    └─ Normalise file paths → relative; return JSON
        │
        ├─ 3. Tool results fed back to Claude
        │
        └─ 4. Claude synthesises: deduplicates, adds suggestions, returns JSON array + summary
        │
        ▼
  ReviewResponse { findings[], summary }
```

GitHub webhook path:
```
POST /api/review/webhook  (X-Hub-Signature-256: sha256=...)
        │
  WebhookController verifies HMAC-SHA256
        │
  GitHubClient.fetchPrDiff(owner, repo, prNumber) → diff
        │
  Same ReviewService pipeline
        │
  GitHubClient.postReview(...) → POST /pulls/{n}/reviews
```

## Prerequisites

| What | Where to get it |
|---|---|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `GITHUB_TOKEN` | GitHub → Settings → Developer settings → Personal access tokens |
| `GITHUB_WEBHOOK_SECRET` | Set when registering the webhook in GitHub repo settings |

The webhook secret is only needed if you register a GitHub webhook. The `/api/review/analyse` endpoint works without it.

## Running

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export GITHUB_TOKEN=ghp_...
export GITHUB_WEBHOOK_SECRET=my-secret   # optional

cd spring-demo
mvn spring-boot:run
```

App starts on **port 8085**.

## Try it

```bash
curl -s -X POST http://localhost:8085/api/review/analyse \
  -H "Content-Type: application/json" \
  -d '{
    "diff": "diff --git a/Foo.java b/Foo.java\n--- a/Foo.java\n+++ b/Foo.java\n@@ -1,3 +1,6 @@\n public class Foo {\n+    public void bar() {\n+        String unused = \"hello\";\n+    }\n }\n"
  }' | jq .
```

Example response:

```json
{
  "findings": [
    {
      "severity": "WARNING",
      "file": "Foo.java",
      "line": 3,
      "message": "Unused local variable 'unused'",
      "suggestion": "Remove the variable declaration or use the value — assign it to a field or pass it to a method."
    }
  ],
  "summary": "1 warning found on changed lines."
}
```

## GitHub webhook setup

1. In your repo: Settings → Webhooks → Add webhook
2. Payload URL: `https://your-host/api/review/webhook`
3. Content type: `application/json`
4. Secret: same value as `GITHUB_WEBHOOK_SECRET`
5. Events: select "Pull requests"

## Build & test

```bash
cd spring-demo

mvn clean package          # build
mvn test                   # unit tests (no API keys needed)
```

To run the integration test (requires `ANTHROPIC_API_KEY` and the app running):

```bash
mvn test -Dtest=ReviewIntegrationTest -Dgroups=integration
```

## Configuration

All defaults are in `spring-demo/src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `reviewer.max-iterations` | `5` | Maximum agentic loop iterations |
| `reviewer.temp-dir` | `${java.io.tmpdir}` | Base directory for per-request temp dirs |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model to use |

## Module layout

```
spring-demo/src/main/java/com/testingai/reviewer/
├── ReviewApplication.java
├── config/
│   ├── AppConfig.java               AnthropicClient bean, gitHubRestClient bean
│   ├── AnthropicProperties.java     anthropic.* config
│   ├── ReviewerProperties.java      reviewer.* config
│   └── GitHubProperties.java        github.* config
├── controller/
│   ├── ReviewController.java        POST /api/review/analyse
│   └── WebhookController.java       POST /api/review/webhook (HMAC-SHA256 verified)
├── service/
│   ├── ReviewService.java           agentic loop + response parsing
│   ├── DiffParser.java             unified diff → fileContents + changedLines
│   └── GitHubClient.java           fetch PR diff, post review comments
├── tool/
│   ├── CheckstyleTool.java          Checkstyle 10.x Java API wrapper
│   ├── PmdTool.java                 PMD 7.x Java API wrapper
│   └── ToolExecutor.java            dispatch, temp dir lifecycle, diff-aware filtering
└── model/
    ├── ParsedDiff.java              { fileContents, changedLines }
    ├── RawFinding.java             { file, tool, rule, message, line }
    ├── Finding.java                { severity, file, line, message, suggestion }
    ├── ReviewRequest.java          { diff }
    ├── ReviewResponse.java         { findings, summary }
    └── WebhookPayload.java         GitHub PR event (nested records)
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.40.1
- [Checkstyle](https://checkstyle.org) 10.21.0 — programmatic Java API
- [PMD](https://pmd.github.io) 7.12.0 — `PmdAnalysis` programmatic API
- GitHub REST API — diff fetch + inline review comments
```

- [ ] **Step 2: Verify the README renders correctly (optional visual check)**

Open the file in your editor or run `cat ai/code-review-agent/README.md` and check that all code blocks and tables are properly closed.

- [ ] **Step 3: Commit**

```bash
git add ai/code-review-agent/README.md
git commit -m "docs(reviewer): write code-review-agent README"
```

---

## Post-Implementation Checklist

After all 8 tasks are complete:

```bash
cd ai/code-review-agent/spring-demo
mvn test
```

Expected: all unit tests passing (integration test excluded). Confirm no test is annotated `@Tag("integration")` is running unless explicitly requested.

Final test count target: ≥ 15 unit tests across `DiffParserTest` (4), `CheckstyleToolTest` (3), `PmdToolTest` (3), `ToolExecutorTest` (3), `ReviewServiceTest` (1), `ReviewControllerTest` (2), `WebhookControllerTest` (3), `GitHubClientTest` (3).

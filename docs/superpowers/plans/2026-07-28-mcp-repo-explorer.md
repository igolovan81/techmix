# MCP Repo Explorer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `ai/mcp-repo-explorer/`, a pair of Spring Boot apps that demonstrate the real Model Context Protocol: `mcp-server` exposes repo-introspection tools over MCP's Streamable HTTP transport, and `mcp-client-agent` is a Claude-powered agent that discovers and calls those tools purely through the protocol (`tools/list` / `tools/call`), unlike this repo's other `ai/` agents which hand-roll their own tool dispatch.

**Architecture:** Two independent, standalone Maven modules (no parent reactor, matching every other `ai/` module) under `ai/mcp-repo-explorer/`. `mcp-server` uses the official `io.modelcontextprotocol.sdk` Java SDK's `WebMvcStreamableServerTransportProvider` to expose three tools (`list_modules`, `read_readme`, `search_readmes`) that read this repository's own filesystem, rooted at a directory resolved by walking up from the working directory to find `CLAUDE.md`. `mcp-client-agent` holds a single `McpSyncClient` connected to `mcp-server`, and on each `POST /api/mcp-agent/run` request lists the server's tools, converts their JSON schemas into Anthropic tool definitions, and runs the same agentic-loop shape as `ai/task-automation-agent`, except every tool call goes over MCP's `tools/call` instead of a local `ToolExecutor`.

**Tech Stack:** Java 21, Spring Boot 3.4.4, `io.modelcontextprotocol.sdk:mcp-bom:0.17.2` (`mcp` + `mcp-spring-webmvc` artifacts), Anthropic Java SDK 2.40.1, Jackson (transitively via `spring-boot-starter-web`), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Java 21, `spring-boot-starter-parent` 3.4.4, `groupId` `com.testingai` — matches every sibling module under `ai/`.
- MCP Java SDK pinned to `io.modelcontextprotocol.sdk:mcp-bom:0.17.2`, imported via `dependencyManagement`. This exact version was resolved and its API (`McpSchema.Tool`, `McpSchema.JsonSchema`, `WebMvcStreamableServerTransportProvider`, `McpClient`/`McpSyncClient`, `HttpClientStreamableHttpTransport`) was verified by decompiling the actual jars — do not bump this version without re-verifying the API surface, since `inputSchema` has changed shape across releases of this SDK.
- Anthropic Java SDK `2.40.1` — matches every other `ai/` module.
- Ports: `mcp-server` → `8092`, `mcp-client-agent` → `8093` (next free ports in the repo; `8090` is taken by `spring-boot-starters/request-logging`, `8091` by `communication-protocols/grpc/client-demo`).
- No parent/reactor `pom.xml` — each module is built standalone via `cd ai/mcp-repo-explorer/<module> && mvn clean package`, matching every other module in this repo.
- Every `pom.xml` declares the `org.projectlombok:lombok` optional dependency (excluded from the Spring Boot repackage) even though it is unused in code, matching the convention in every sibling `ai/*/spring-demo/pom.xml`.
- Maven Surefire excludes `@Tag("integration")` tests via `<excludedGroups>integration</excludedGroups>`, matching every sibling module — `mvn test` never needs live API keys or a running peer service; `mvn test -Dtest=ClassName -Dgroups=integration` does.
- Follow `.claude/rules/code-review.md`: `private final` on fields assigned once; pattern-matching `instanceof` instead of cast-after-check; no explicit `.toString()` before an SLF4J placeholder or string concat; `list.getFirst()` instead of `list.get(0)`; no redundant `throws`.

---

### Task 1: Scaffold `mcp-server` + `RepoRootResolver`

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-server/pom.xml`
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/McpServerApplication.java`
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/resources/application.yml`
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/RepoRootResolver.java`
- Test: `ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/RepoRootResolverTest.java`

**Interfaces:**
- Produces: `RepoRootResolver.resolve()` → `Path` (walks up from `user.dir`), and package-visible `RepoRootResolver.resolveFrom(Path startDir)` → `Path`, throwing `IllegalStateException` if `CLAUDE.md` isn't found within 10 levels. Later tasks (Task 6) call `RepoRootResolver.resolve()` from a `@Bean` method.

- [ ] **Step 1: Create the module scaffold**

`ai/mcp-repo-explorer/mcp-server/pom.xml`:
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
    <artifactId>mcp-repo-explorer-server</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>mcp-server</name>

    <properties>
        <java.version>21</java.version>
        <lombok.version>1.18.38</lombok.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.modelcontextprotocol.sdk</groupId>
                <artifactId>mcp-bom</artifactId>
                <version>0.17.2</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp</artifactId>
        </dependency>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-spring-webmvc</artifactId>
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

`ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/McpServerApplication.java`:
```java
package com.testingai.mcpexplorer.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
```

`ai/mcp-repo-explorer/mcp-server/src/main/resources/application.yml`:
```yaml
server:
  port: 8092
```

- [ ] **Step 2: Write the failing test for `RepoRootResolver`**

`ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/RepoRootResolverTest.java`:
```java
package com.testingai.mcpexplorer.server.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoRootResolverTest {

    @Test
    void resolveFrom_findsMarkerInAncestor(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("CLAUDE.md"));
        Path nested = tempDir.resolve("a/b/c");
        Files.createDirectories(nested);

        Path result = RepoRootResolver.resolveFrom(nested);

        assertThat(result).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    @Test
    void resolveFrom_throwsWhenMarkerNotFoundWithinCap(@TempDir Path tempDir) throws IOException {
        Path deep = tempDir;
        for (int i = 0; i < 12; i++) {
            deep = deep.resolve("level" + i);
        }
        Files.createDirectories(deep);
        Path finalDeep = deep;

        assertThatThrownBy(() -> RepoRootResolver.resolveFrom(finalDeep))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=RepoRootResolverTest`
Expected: compilation failure — `RepoRootResolver` does not exist yet.

- [ ] **Step 4: Implement `RepoRootResolver`**

`ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/RepoRootResolver.java`:
```java
package com.testingai.mcpexplorer.server.tool;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RepoRootResolver {

    private static final String MARKER_FILE = "CLAUDE.md";
    private static final int MAX_LEVELS = 10;

    private RepoRootResolver() {
    }

    public static Path resolve() {
        return resolveFrom(Path.of(System.getProperty("user.dir")));
    }

    static Path resolveFrom(Path startDir) {
        Path current = startDir.toAbsolutePath().normalize();
        for (int i = 0; i <= MAX_LEVELS; i++) {
            if (Files.exists(current.resolve(MARKER_FILE))) {
                return current;
            }
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }
        throw new IllegalStateException(
                "Could not locate repo root (" + MARKER_FILE + ") within " + MAX_LEVELS
                        + " levels above " + startDir);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=RepoRootResolverTest`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-server/pom.xml \
        ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/McpServerApplication.java \
        ai/mcp-repo-explorer/mcp-server/src/main/resources/application.yml \
        ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/RepoRootResolver.java \
        ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/RepoRootResolverTest.java
git commit -m "feat(mcp-repo-explorer): scaffold mcp-server module and add RepoRootResolver"
```

---

### Task 2: `RepoPathGuard`

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/RepoPathGuard.java`
- Test: `ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/RepoPathGuardTest.java`

**Interfaces:**
- Consumes: none.
- Produces: `RepoPathGuard.resolve(Path repoRoot, String relativePath)` → `Path`, throws `IllegalArgumentException` if the resolved path escapes `repoRoot`. Used by `ReadReadmeTool` in Task 4.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.mcpexplorer.server.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoPathGuardTest {

    @Test
    void resolve_allowsPathWithinRoot(@TempDir Path repoRoot) {
        Path resolved = RepoPathGuard.resolve(repoRoot, "ai/code-review-agent");

        assertThat(resolved).isEqualTo(repoRoot.toAbsolutePath().normalize().resolve("ai/code-review-agent"));
    }

    @Test
    void resolve_rejectsPathTraversal(@TempDir Path repoRoot) {
        assertThatThrownBy(() -> RepoPathGuard.resolve(repoRoot, "../../../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=RepoPathGuardTest`
Expected: compilation failure — `RepoPathGuard` does not exist yet.

- [ ] **Step 3: Implement `RepoPathGuard`**

```java
package com.testingai.mcpexplorer.server.tool;

import java.nio.file.Path;

public final class RepoPathGuard {

    private RepoPathGuard() {
    }

    public static Path resolve(Path repoRoot, String relativePath) {
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes repo root: " + relativePath);
        }
        return resolved;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=RepoPathGuardTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/RepoPathGuard.java \
        ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/RepoPathGuardTest.java
git commit -m "feat(mcp-repo-explorer): add RepoPathGuard for path-traversal-safe resolution"
```

---

### Task 3: `list_modules` tool

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/RepoScanDirs.java`
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/ListModulesTool.java`
- Test: `ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/ListModulesToolTest.java`

**Interfaces:**
- Consumes: none (constructed with a `Path repoRoot`, supplied by the `repoRoot` bean from Task 6).
- Produces: `RepoScanDirs.SKIP` → `Set<String>` of directory names to exclude when scanning (reused by `SearchReadmesTool` in Task 5). `ListModulesTool.ModuleEntry(String category, String module)` record. `ListModulesTool.definition()` → `McpSchema.Tool`; `ListModulesTool.call(Map<String, Object> arguments)` → `McpSchema.CallToolResult`. Both are wired into the MCP server in Task 6.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.mcpexplorer.server.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListModulesToolTest {

    @Test
    void call_listsOnlyDirectoriesWithReadmeOrPomDirectlyOrOneLevelDown(@TempDir Path repoRoot) throws IOException {
        Files.createDirectories(repoRoot.resolve("message-brokers/kafka"));
        Files.createFile(repoRoot.resolve("message-brokers/kafka/README.md"));

        Files.createDirectories(repoRoot.resolve("backend/hackerrank"));
        Files.createFile(repoRoot.resolve("backend/hackerrank/pom.xml"));

        Files.createDirectories(repoRoot.resolve("spring-boot-starters/request-logging/spring-demo"));
        Files.createFile(repoRoot.resolve("spring-boot-starters/request-logging/spring-demo/pom.xml"));

        Files.createDirectories(repoRoot.resolve("empty-category/empty-module"));
        Files.createDirectories(repoRoot.resolve("message-brokers/target"));

        ListModulesTool tool = new ListModulesTool(repoRoot);

        McpSchema.CallToolResult result = tool.call(Map.of());

        String json = ((McpSchema.TextContent) result.content().getFirst()).text();
        List<ListModulesTool.ModuleEntry> modules = new ObjectMapper()
                .readValue(json, new TypeReference<List<ListModulesTool.ModuleEntry>>() {});

        assertThat(modules).containsExactlyInAnyOrder(
                new ListModulesTool.ModuleEntry("message-brokers", "kafka"),
                new ListModulesTool.ModuleEntry("backend", "hackerrank"),
                new ListModulesTool.ModuleEntry("spring-boot-starters", "request-logging"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=ListModulesToolTest`
Expected: compilation failure — `ListModulesTool` does not exist yet.

- [ ] **Step 3: Implement `RepoScanDirs` and `ListModulesTool`**

```java
package com.testingai.mcpexplorer.server.tool;

import java.util.Set;

final class RepoScanDirs {
    static final Set<String> SKIP = Set.of(".git", "target", "node_modules", ".claude", "docs");

    private RepoScanDirs() {
    }
}
```

```java
package com.testingai.mcpexplorer.server.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class ListModulesTool {

    private final Path repoRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ListModulesTool(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public record ModuleEntry(String category, String module) {
    }

    public McpSchema.Tool definition() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null);
        return McpSchema.Tool.builder()
                .name("list_modules")
                .description("List this repository's category/module directories: any level-2 directory "
                        + "(category/module) that itself, or one of its direct children, contains a README.md "
                        + "or pom.xml.")
                .inputSchema(schema)
                .build();
    }

    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            List<ModuleEntry> modules = new ArrayList<>();
            for (Path category : listDirectories(repoRoot)) {
                for (Path module : listDirectories(category)) {
                    if (hasMarker(module)) {
                        modules.add(new ModuleEntry(
                                category.getFileName().toString(),
                                module.getFileName().toString()));
                    }
                }
            }
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(modules))
                    .build();
        } catch (IOException e) {
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .addTextContent("list_modules failed: " + e.getMessage())
                    .build();
        }
    }

    private List<Path> listDirectories(Path dir) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(p -> !RepoScanDirs.SKIP.contains(p.getFileName().toString()))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .toList();
        }
    }

    private boolean hasMarker(Path dir) throws IOException {
        if (Files.exists(dir.resolve("README.md")) || Files.exists(dir.resolve("pom.xml"))) {
            return true;
        }
        for (Path child : listDirectories(dir)) {
            if (Files.exists(child.resolve("README.md")) || Files.exists(child.resolve("pom.xml"))) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=ListModulesToolTest`
Expected: PASS (1 test)

- [ ] **Step 5: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/RepoScanDirs.java \
        ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/ListModulesTool.java \
        ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/ListModulesToolTest.java
git commit -m "feat(mcp-repo-explorer): add list_modules tool"
```

---

### Task 4: `read_readme` tool

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/ReadReadmeTool.java`
- Test: `ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/ReadReadmeToolTest.java`

**Interfaces:**
- Consumes: `RepoPathGuard.resolve(Path, String)` from Task 2.
- Produces: `ReadReadmeTool.definition()` → `McpSchema.Tool`; `ReadReadmeTool.call(Map<String, Object> arguments)` → `McpSchema.CallToolResult`. Wired into the server in Task 6.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.mcpexplorer.server.tool;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadReadmeToolTest {

    @Test
    void call_readsReadmeFromModuleDirectory(@TempDir Path repoRoot) throws IOException {
        Files.createDirectories(repoRoot.resolve("ai/code-review-agent"));
        Files.writeString(repoRoot.resolve("ai/code-review-agent/README.md"), "# Code Review Agent");

        ReadReadmeTool tool = new ReadReadmeTool(repoRoot);
        McpSchema.CallToolResult result = tool.call(Map.of("path", "ai/code-review-agent"));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertThat(text).isEqualTo("# Code Review Agent");
    }

    @Test
    void call_returnsErrorForPathTraversal(@TempDir Path repoRoot) {
        ReadReadmeTool tool = new ReadReadmeTool(repoRoot);

        McpSchema.CallToolResult result = tool.call(Map.of("path", "../../../../etc/passwd"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    void call_returnsErrorWhenReadmeMissing(@TempDir Path repoRoot) throws IOException {
        Files.createDirectories(repoRoot.resolve("ai/nothing-here"));
        ReadReadmeTool tool = new ReadReadmeTool(repoRoot);

        McpSchema.CallToolResult result = tool.call(Map.of("path", "ai/nothing-here"));

        assertThat(result.isError()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=ReadReadmeToolTest`
Expected: compilation failure — `ReadReadmeTool` does not exist yet.

- [ ] **Step 3: Implement `ReadReadmeTool`**

```java
package com.testingai.mcpexplorer.server.tool;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class ReadReadmeTool {

    private static final int MAX_CHARS = 8000;

    private final Path repoRoot;

    public ReadReadmeTool(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public McpSchema.Tool definition() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object",
                Map.of("path", Map.of(
                        "type", "string",
                        "description", "Path relative to the repo root, e.g. 'ai/code-review-agent'. May point "
                                + "at a module directory (its README.md is read) or directly at a README.md file.")),
                List.of("path"),
                false, null, null);
        return McpSchema.Tool.builder()
                .name("read_readme")
                .description("Read a README.md file from this repository, given a path relative to the repo root.")
                .inputSchema(schema)
                .build();
    }

    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        Object rawPath = arguments.get("path");
        if (rawPath == null) {
            return error("read_readme: missing required field 'path'");
        }

        Path resolved;
        try {
            resolved = RepoPathGuard.resolve(repoRoot, rawPath.toString());
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }

        if (Files.isDirectory(resolved)) {
            resolved = resolved.resolve("README.md");
        }
        if (!Files.isRegularFile(resolved)) {
            return error("read_readme: no README.md found at " + rawPath);
        }

        try {
            String content = Files.readString(resolved);
            if (content.length() > MAX_CHARS) {
                content = content.substring(0, MAX_CHARS);
            }
            return McpSchema.CallToolResult.builder().addTextContent(content).build();
        } catch (IOException e) {
            return error("read_readme: failed to read " + rawPath + ": " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=ReadReadmeToolTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/ReadReadmeTool.java \
        ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/ReadReadmeToolTest.java
git commit -m "feat(mcp-repo-explorer): add read_readme tool"
```

---

### Task 5: `search_readmes` tool

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/SearchReadmesTool.java`
- Test: `ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/SearchReadmesToolTest.java`

**Interfaces:**
- Consumes: `RepoScanDirs.SKIP` from Task 3.
- Produces: `SearchReadmesTool.Match(String path, String line)` record; `SearchReadmesTool.definition()` → `McpSchema.Tool`; `SearchReadmesTool.call(Map<String, Object> arguments)` → `McpSchema.CallToolResult`. Wired into the server in Task 6.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.mcpexplorer.server.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchReadmesToolTest {

    @Test
    void call_findsCaseInsensitiveMatchesAndSkipsExcludedDirs(@TempDir Path repoRoot) throws IOException {
        Files.createDirectories(repoRoot.resolve("message-brokers/kafka"));
        Files.writeString(repoRoot.resolve("message-brokers/kafka/README.md"),
                "# Kafka demo\nUses a 3-node KRaft cluster.\n");

        Files.createDirectories(repoRoot.resolve("ai/code-review-agent"));
        Files.writeString(repoRoot.resolve("ai/code-review-agent/README.md"),
                "# Code Review Agent\nNo Kafka here.\n");

        Files.createDirectories(repoRoot.resolve("message-brokers/kafka/target"));
        Files.writeString(repoRoot.resolve("message-brokers/kafka/target/README.md"), "kafka build output, ignore me");

        SearchReadmesTool tool = new SearchReadmesTool(repoRoot);

        McpSchema.CallToolResult result = tool.call(Map.of("keyword", "kafka"));

        String json = ((McpSchema.TextContent) result.content().getFirst()).text();
        List<SearchReadmesTool.Match> matches = new ObjectMapper()
                .readValue(json, new TypeReference<List<SearchReadmesTool.Match>>() {});

        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(SearchReadmesTool.Match::path)
                .containsExactlyInAnyOrder(
                        "message-brokers/kafka/README.md",
                        "ai/code-review-agent/README.md");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=SearchReadmesToolTest`
Expected: compilation failure — `SearchReadmesTool` does not exist yet.

- [ ] **Step 3: Implement `SearchReadmesTool`**

```java
package com.testingai.mcpexplorer.server.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class SearchReadmesTool {

    private static final int MAX_RESULTS = 20;

    private final Path repoRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SearchReadmesTool(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public record Match(String path, String line) {
    }

    public McpSchema.Tool definition() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object",
                Map.of("keyword", Map.of(
                        "type", "string",
                        "description", "Case-insensitive keyword to search for across every README.md in the repo.")),
                List.of("keyword"),
                false, null, null);
        return McpSchema.Tool.builder()
                .name("search_readmes")
                .description("Case-insensitive search for a keyword across every README.md file in this "
                        + "repository. Returns up to 20 matching lines with their file path.")
                .inputSchema(schema)
                .build();
    }

    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        Object rawKeyword = arguments.get("keyword");
        if (rawKeyword == null) {
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .addTextContent("search_readmes: missing required field 'keyword'")
                    .build();
        }
        String keyword = rawKeyword.toString().toLowerCase();

        try {
            List<Path> readmes = new ArrayList<>();
            collectReadmes(repoRoot, readmes);

            List<Match> matches = new ArrayList<>();
            outer:
            for (Path readme : readmes) {
                for (String line : Files.readAllLines(readme)) {
                    if (line.toLowerCase().contains(keyword)) {
                        matches.add(new Match(repoRoot.relativize(readme).toString(), line.trim()));
                        if (matches.size() >= MAX_RESULTS) {
                            break outer;
                        }
                    }
                }
            }
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(matches))
                    .build();
        } catch (IOException e) {
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .addTextContent("search_readmes failed: " + e.getMessage())
                    .build();
        }
    }

    private void collectReadmes(Path dir, List<Path> out) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (Files.isDirectory(child)) {
                    if (!RepoScanDirs.SKIP.contains(name) && !name.startsWith(".")) {
                        collectReadmes(child, out);
                    }
                } else if (name.equals("README.md")) {
                    out.add(child);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=SearchReadmesToolTest`
Expected: PASS (1 test)

- [ ] **Step 5: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/tool/SearchReadmesTool.java \
        ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/tool/SearchReadmesToolTest.java
git commit -m "feat(mcp-repo-explorer): add search_readmes tool"
```

---

### Task 6: Wire the MCP server (transport + tool registration)

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/config/RepoRootConfig.java`
- Create: `ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/config/McpServerConfig.java`
- Test: `ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/McpServerWiringTest.java`

**Interfaces:**
- Consumes: `RepoRootResolver.resolve()` (Task 1), `ListModulesTool`/`ReadReadmeTool`/`SearchReadmesTool` (Tasks 3–5).
- Produces: a running MCP endpoint at `POST/GET/DELETE /mcp`, fully exercised end-to-end by this task's test — no later task depends on new symbols from this one.

- [ ] **Step 1: Write the failing test**

This test starts the real Spring context on a random port and drives the server with a real MCP client over HTTP — no mocking of the protocol.

```java
package com.testingai.mcpexplorer.server;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerWiringTest {

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    @BeforeEach
    void setUp() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void tearDown() {
        client.closeGracefully();
    }

    @Test
    void listTools_returnsAllThreeTools() {
        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();

        assertThat(names).containsExactlyInAnyOrder("list_modules", "read_readme", "search_readmes");
    }

    @Test
    void callTool_listModules_returnsNonEmptyResult() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("list_modules", Map.of()));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void callTool_readReadme_withPathTraversal_returnsError() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("read_readme", Map.of("path", "../../../../etc/passwd")));

        assertThat(result.isError()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=McpServerWiringTest`
Expected: application context fails to start — no `McpSyncServer`/transport bean exists yet, and no `repoRoot` bean exists for `ListModulesTool`/`ReadReadmeTool`/`SearchReadmesTool` to be constructed with.

- [ ] **Step 3: Implement the config classes**

```java
package com.testingai.mcpexplorer.server.config;

import com.testingai.mcpexplorer.server.tool.RepoRootResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class RepoRootConfig {

    @Bean
    public Path repoRoot() {
        return RepoRootResolver.resolve();
    }
}
```

```java
package com.testingai.mcpexplorer.server.config;

import com.testingai.mcpexplorer.server.tool.ListModulesTool;
import com.testingai.mcpexplorer.server.tool.ReadReadmeTool;
import com.testingai.mcpexplorer.server.tool.SearchReadmesTool;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class McpServerConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider transportProvider() {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpSyncServer(WebMvcStreamableServerTransportProvider transportProvider,
                                        ListModulesTool listModulesTool,
                                        ReadReadmeTool readReadmeTool,
                                        SearchReadmesTool searchReadmesTool) {
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("mcp-repo-explorer", "0.0.1")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();

        server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                .tool(listModulesTool.definition())
                .callHandler((exchange, request) -> listModulesTool.call(request.arguments()))
                .build());
        server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                .tool(readReadmeTool.definition())
                .callHandler((exchange, request) -> readReadmeTool.call(request.arguments()))
                .build());
        server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                .tool(searchReadmesTool.definition())
                .callHandler((exchange, request) -> searchReadmesTool.call(request.arguments()))
                .build());

        return server;
    }
}
```

Note: no `@EnableWebMvc` on `McpServerConfig` — this is a Spring Boot app, and Boot's `WebMvcAutoConfiguration` already detects `RouterFunction` beans; adding `@EnableWebMvc` would switch off Boot's MVC auto-configuration.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test -Dtest=McpServerWiringTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Run the full test suite for this module**

Run: `cd ai/mcp-repo-explorer/mcp-server && mvn test`
Expected: PASS (all tests across Tasks 1–6)

- [ ] **Step 6: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/config/RepoRootConfig.java \
        ai/mcp-repo-explorer/mcp-server/src/main/java/com/testingai/mcpexplorer/server/config/McpServerConfig.java \
        ai/mcp-repo-explorer/mcp-server/src/test/java/com/testingai/mcpexplorer/server/McpServerWiringTest.java
git commit -m "feat(mcp-repo-explorer): wire MCP server transport and register the three tools"
```

---

### Task 7: `mcp-server` README

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-server/README.md`

**Interfaces:**
- Consumes: nothing new (documents Tasks 1–6).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the README**

```markdown
# MCP Server (Repo Explorer)

A real [Model Context Protocol](https://modelcontextprotocol.io) server exposing three read-only tools over
the Streamable HTTP transport, using the official `io.modelcontextprotocol.sdk` Java SDK. The tools introspect
this repository's own structure — no external API keys or infrastructure required.

## Tools

| Tool | Input | Description |
|------|-------|-------------|
| `list_modules` | `{}` | Lists `{category, module}` pairs for every level-2 directory that (or a direct child of which) contains a `README.md` or `pom.xml` |
| `read_readme` | `{ "path": string }` | Reads a `README.md` given a path relative to the repo root, truncated to 8 000 characters |
| `search_readmes` | `{ "keyword": string }` | Case-insensitive search across every `README.md` in the repo, up to 20 matches |

## Repo root resolution

The server walks up from its working directory looking for `CLAUDE.md`, capped at 10 levels, and fails to
start if it isn't found. This means `mvn spring-boot:run` just works from this directory — no path
configuration needed.

## Running

```bash
cd ai/mcp-repo-explorer/mcp-server
mvn spring-boot:run
```

App starts on **port 8092**, MCP endpoint at `http://localhost:8092/mcp`.

## Try it

Any MCP client that speaks Streamable HTTP can connect to `http://localhost:8092/mcp`. See
`ai/mcp-repo-explorer/mcp-client-agent/README.md` for a Claude-powered client built specifically for this server.

## Build & test

```bash
cd ai/mcp-repo-explorer/mcp-server

mvn clean package   # build
mvn test            # unit tests + a full end-to-end wiring test against a real MCP client (no API keys needed)
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) 0.17.2 (`mcp` + `mcp-spring-webmvc`) — Streamable HTTP transport via `WebMvcStreamableServerTransportProvider`
```

- [ ] **Step 2: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-server/README.md
git commit -m "docs(mcp-repo-explorer): add mcp-server README"
```

---

### Task 8: Scaffold `mcp-client-agent`

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-client-agent/pom.xml`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/McpAgentApplication.java`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/resources/application.yml`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/AnthropicProperties.java`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/AgentProperties.java`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/McpClientProperties.java`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/AppConfig.java`

**Interfaces:**
- Consumes: none.
- Produces: Spring beans `AnthropicClient` and `McpSyncClient` (already `.initialize()`d and connected to `mcp-server`), and `@ConfigurationProperties` records `AnthropicProperties(String apiKey, String model)`, `AgentProperties(int maxIterations)`, `McpClientProperties(String serverUrl)` — consumed by `McpAgentService` in Task 9.

This task has no isolated business logic to unit-test (it's wiring only); its correctness is verified by compilation and exercised end-to-end by Task 11's integration test. Verify with `mvn compile` instead of a JUnit test.

- [ ] **Step 1: Create the module scaffold**

`ai/mcp-repo-explorer/mcp-client-agent/pom.xml`:
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
    <artifactId>mcp-repo-explorer-client-agent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>mcp-client-agent</name>

    <properties>
        <java.version>21</java.version>
        <lombok.version>1.18.38</lombok.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.modelcontextprotocol.sdk</groupId>
                <artifactId>mcp-bom</artifactId>
                <version>0.17.2</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

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
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp</artifactId>
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

`ai/mcp-repo-explorer/mcp-client-agent/src/main/resources/application.yml`:
```yaml
server:
  port: 8093

agent:
  max-iterations: 10

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  model: claude-sonnet-4-6

mcp:
  server-url: http://localhost:8092
```

- [ ] **Step 2: Add the configuration properties records**

`ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/AnthropicProperties.java`:
```java
package com.testingai.mcpexplorer.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(String apiKey, String model) {
}
```

`ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/AgentProperties.java`:
```java
package com.testingai.mcpexplorer.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(int maxIterations) {
}
```

`ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/McpClientProperties.java`:
```java
package com.testingai.mcpexplorer.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp")
public record McpClientProperties(String serverUrl) {
}
```

- [ ] **Step 3: Add `AppConfig`**

```java
package com.testingai.mcpexplorer.client.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AppConfig {

    private final AnthropicProperties anthropic;
    private final McpClientProperties mcpClientProperties;

    public AppConfig(AnthropicProperties anthropic, McpClientProperties mcpClientProperties) {
        this.anthropic = anthropic;
        this.mcpClientProperties = mcpClientProperties;
    }

    @PostConstruct
    public void validateApiKeys() {
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

    @Bean(destroyMethod = "closeGracefully")
    public McpSyncClient mcpSyncClient() {
        var transport = HttpClientStreamableHttpTransport.builder(mcpClientProperties.serverUrl())
                .endpoint("/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport).build();
        client.initialize();
        return client;
    }
}
```

- [ ] **Step 4: Add the application entry point**

```java
package com.testingai.mcpexplorer.client;

import com.testingai.mcpexplorer.client.config.AgentProperties;
import com.testingai.mcpexplorer.client.config.AnthropicProperties;
import com.testingai.mcpexplorer.client.config.McpClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, AnthropicProperties.class, McpClientProperties.class})
public class McpAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpAgentApplication.class, args);
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `cd ai/mcp-repo-explorer/mcp-client-agent && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-client-agent/pom.xml \
        ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/McpAgentApplication.java \
        ai/mcp-repo-explorer/mcp-client-agent/src/main/resources/application.yml \
        ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/config/
git commit -m "feat(mcp-repo-explorer): scaffold mcp-client-agent module"
```

---

### Task 9: Models + `McpAgentService`

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/model/AgentRequest.java`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/model/AgentResponse.java`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/model/StepRecord.java`
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/service/McpAgentService.java`
- Test: `ai/mcp-repo-explorer/mcp-client-agent/src/test/java/com/testingai/mcpexplorer/client/service/McpAgentServiceTest.java`

**Interfaces:**
- Consumes: `AnthropicClient` and `McpSyncClient` beans + `AgentProperties`/`AnthropicProperties` (Task 8).
- Produces: `AgentRequest(String goal)`, `AgentResponse(String answer, List<StepRecord> steps, int iterations, boolean truncated)`, `StepRecord(String tool, String input, String output)` records; `McpAgentService.run(String goal)` → `AgentResponse`. Consumed by `AgentController` in Task 10.

- [ ] **Step 1: Add the model records**

`ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/model/AgentRequest.java`:
```java
package com.testingai.mcpexplorer.client.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(@NotBlank String goal) {
}
```

`ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/model/StepRecord.java`:
```java
package com.testingai.mcpexplorer.client.model;

public record StepRecord(String tool, String input, String output) {
}
```

`ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/model/AgentResponse.java`:
```java
package com.testingai.mcpexplorer.client.model;

import java.util.List;

public record AgentResponse(String answer, List<StepRecord> steps, int iterations, boolean truncated) {
}
```

- [ ] **Step 2: Write the failing test for `McpAgentService`**

```java
package com.testingai.mcpexplorer.client.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.mcpexplorer.client.config.AgentProperties;
import com.testingai.mcpexplorer.client.config.AnthropicProperties;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpAgentServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock
    private McpSyncClient mcpClient;

    private McpAgentService agentService;

    @BeforeEach
    void setUp() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null);
        McpSchema.Tool stubTool = McpSchema.Tool.builder()
                .name("list_modules")
                .description("stub")
                .inputSchema(schema)
                .build();
        when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(stubTool), null));

        agentService = new McpAgentService(
                anthropic, mcpClient,
                new AgentProperties(10),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));
    }

    @Test
    void run_singleIteration_noToolCalls_returnsAnswer() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("Paris."));

        AgentResponse result = agentService.run("Capital of France?");

        assertThat(result.answer()).isEqualTo("Paris.");
        assertThat(result.steps()).isEmpty();
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void run_multiIteration_callsMcpToolThenReturnsAnswer() {
        Message toolCallResponse = buildToolUseMessage("tool_abc", "list_modules", JsonValue.from(Map.of()));
        Message finalResponse = buildTextMessage("There are 3 modules.");

        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);
        when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(McpSchema.CallToolResult.builder()
                        .addTextContent("[{\"category\":\"ai\",\"module\":\"code-review-agent\"}]")
                        .build());

        AgentResponse result = agentService.run("How many modules are there?");

        assertThat(result.answer()).isEqualTo("There are 3 modules.");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().tool()).isEqualTo("list_modules");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void run_truncatesWhenIterationCapReached() {
        Message loopingToolCall = buildToolUseMessage("tool_loop", "list_modules", JsonValue.from(Map.of()));
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(loopingToolCall);
        when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(McpSchema.CallToolResult.builder().addTextContent("[]").build());

        agentService = new McpAgentService(
                anthropic, mcpClient,
                new AgentProperties(2),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));

        AgentResponse result = agentService.run("Loop forever");

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
    }

    private Message buildTextMessage(String text) {
        TextBlock textBlock = TextBlock.builder().citations(Optional.empty()).text(text).build();
        return buildMessage(List.of(ContentBlock.ofText(textBlock)));
    }

    private Message buildToolUseMessage(String id, String name, JsonValue input) {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id(id)
                .caller(DirectCaller.builder().build())
                .input(input)
                .name(name)
                .build();
        return buildMessage(List.of(ContentBlock.ofToolUse(toolUse)));
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

- [ ] **Step 3: Run test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-client-agent && mvn test -Dtest=McpAgentServiceTest`
Expected: compilation failure — `McpAgentService` does not exist yet.

- [ ] **Step 4: Implement `McpAgentService`**

```java
package com.testingai.mcpexplorer.client.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.testingai.mcpexplorer.client.config.AgentProperties;
import com.testingai.mcpexplorer.client.config.AnthropicProperties;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import com.testingai.mcpexplorer.client.model.StepRecord;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class McpAgentService {

    private final AnthropicClient anthropic;
    private final McpSyncClient mcpClient;
    private final AgentProperties agentProps;
    private final AnthropicProperties anthropicProps;

    public McpAgentService(AnthropicClient anthropic,
                            McpSyncClient mcpClient,
                            AgentProperties agentProps,
                            AnthropicProperties anthropicProps) {
        this.anthropic = anthropic;
        this.mcpClient = mcpClient;
        this.agentProps = agentProps;
        this.anthropicProps = anthropicProps;
    }

    public AgentResponse run(String goal) {
        List<Tool> anthropicTools = mcpClient.listTools().tools().stream()
                .map(this::toAnthropicTool)
                .toList();

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(goal).build());

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProps.maxIterations()) {
            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .model(anthropicProps.model())
                    .maxTokens(4096)
                    .messages(messages)
                    .toolChoice(ToolChoiceAuto.builder().build());
            anthropicTools.forEach(paramsBuilder::addTool);

            Message response = anthropic.messages().create(paramsBuilder.build());

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
                String answer = response.content().stream()
                        .filter(ContentBlock::isText)
                        .map(ContentBlock::asText)
                        .map(TextBlock::text)
                        .collect(Collectors.joining(""));
                return new AgentResponse(answer, steps, iterations, false);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ToolUseBlock call : toolCalls) {
                Map<String, Object> args = call._input().convert(new TypeReference<Map<String, Object>>() {});
                if (args == null) {
                    args = Map.of();
                }
                McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(call.name(), args));
                String output = extractText(result);
                steps.add(new StepRecord(call.name(), args.toString(), output));
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

        return new AgentResponse("", steps, iterations, true);
    }

    private Tool toAnthropicTool(McpSchema.Tool mcpTool) {
        McpSchema.JsonSchema schema = mcpTool.inputSchema();
        Tool.InputSchema.Properties.Builder propsBuilder = Tool.InputSchema.Properties.builder();
        if (schema.properties() != null) {
            schema.properties().forEach((name, def) -> propsBuilder.putAdditionalProperty(name, JsonValue.from(def)));
        }
        Tool.InputSchema.Builder inputSchemaBuilder = Tool.InputSchema.builder().properties(propsBuilder.build());
        if (schema.required() != null) {
            inputSchemaBuilder.required(schema.required());
        }
        return Tool.builder()
                .name(mcpTool.name())
                .description(mcpTool.description() == null ? "" : mcpTool.description())
                .inputSchema(inputSchemaBuilder.build())
                .build();
    }

    private String extractText(McpSchema.CallToolResult result) {
        List<String> texts = new ArrayList<>();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                texts.add(textContent.text());
            }
        }
        return String.join("\n", texts);
    }
}
```

Note: `run()` calls `mcpClient.listTools()` fresh on every invocation rather than caching — this keeps the tool list live if `mcp-server` changes, and is simple to reason about for a demo. `_input()` is the raw `JsonValue` accessor on `ToolUseBlock`, the same one used by `ai/task-automation-agent`'s `ToolExecutor.execute(String, JsonValue)`.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-client-agent && mvn test -Dtest=McpAgentServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/model/ \
        ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/service/McpAgentService.java \
        ai/mcp-repo-explorer/mcp-client-agent/src/test/java/com/testingai/mcpexplorer/client/service/McpAgentServiceTest.java
git commit -m "feat(mcp-repo-explorer): add McpAgentService, discovering and calling tools over MCP"
```

---

### Task 10: `AgentController`

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/controller/AgentController.java`
- Test: `ai/mcp-repo-explorer/mcp-client-agent/src/test/java/com/testingai/mcpexplorer/client/controller/AgentControllerTest.java`

**Interfaces:**
- Consumes: `McpAgentService.run(String)` (Task 9), `AgentRequest`/`AgentResponse` (Task 9).
- Produces: `POST /api/mcp-agent/run` REST endpoint.

- [ ] **Step 1: Write the failing test**

```java
package com.testingai.mcpexplorer.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.mcpexplorer.client.model.AgentRequest;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import com.testingai.mcpexplorer.client.service.McpAgentService;
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

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private McpAgentService agentService;

    @Test
    void run_returnsAgentResponseAsJson() throws Exception {
        AgentResponse expected = new AgentResponse("There are 3 modules.", List.of(), 1, false);
        when(agentService.run("How many modules are there?")).thenReturn(expected);

        mockMvc.perform(post("/api/mcp-agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentRequest("How many modules are there?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("There are 3 modules."))
                .andExpect(jsonPath("$.iterations").value(1))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void run_returns400WhenGoalIsBlank() throws Exception {
        mockMvc.perform(post("/api/mcp-agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai/mcp-repo-explorer/mcp-client-agent && mvn test -Dtest=AgentControllerTest`
Expected: compilation failure — `AgentController` does not exist yet.

- [ ] **Step 3: Implement `AgentController`**

```java
package com.testingai.mcpexplorer.client.controller;

import com.testingai.mcpexplorer.client.model.AgentRequest;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import com.testingai.mcpexplorer.client.service.McpAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp-agent")
public class AgentController {

    private final McpAgentService agentService;

    public AgentController(McpAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public AgentResponse run(@RequestBody @Valid AgentRequest request) {
        return agentService.run(request.goal());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ai/mcp-repo-explorer/mcp-client-agent && mvn test -Dtest=AgentControllerTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Run the full test suite for this module**

Run: `cd ai/mcp-repo-explorer/mcp-client-agent && mvn test`
Expected: PASS (all tests from Tasks 9–10)

- [ ] **Step 6: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-client-agent/src/main/java/com/testingai/mcpexplorer/client/controller/AgentController.java \
        ai/mcp-repo-explorer/mcp-client-agent/src/test/java/com/testingai/mcpexplorer/client/controller/AgentControllerTest.java
git commit -m "feat(mcp-repo-explorer): add POST /api/mcp-agent/run endpoint"
```

---

### Task 11: End-to-end integration test

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-client-agent/src/test/java/com/testingai/mcpexplorer/client/integration/McpAgentIntegrationTest.java`

**Interfaces:**
- Consumes: the full running `mcp-client-agent` app (Tasks 8–10), which in turn requires `mcp-server` (Task 6) to be running separately on port 8092.

This test requires a real `ANTHROPIC_API_KEY` and a separately running `mcp-server`; it is tagged `@Tag("integration")` and excluded from the default `mvn test` run by the surefire config in Task 8's `pom.xml`, matching every other `ai/` module's integration test.

- [ ] **Step 1: Write the test**

```java
package com.testingai.mcpexplorer.client.integration;

import com.testingai.mcpexplorer.client.model.AgentResponse;
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
class McpAgentIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate http = new TestRestTemplate();

    @Test
    void run_withRealMcpServerAndClaude_returnsNonEmptyAnswer() {
        var request = Map.of("goal", "Using the available tools, name one module under the message-brokers category.");

        ResponseEntity<AgentResponse> response = http.postForEntity(
                "http://localhost:" + port + "/api/mcp-agent/run",
                request,
                AgentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isNotBlank();
        assertThat(response.getBody().steps()).isNotEmpty();
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd ai/mcp-repo-explorer/mcp-client-agent && mvn test-compile`
Expected: BUILD SUCCESS

This test is intentionally not run now — it needs `mcp-server` running on `:8092` and `ANTHROPIC_API_KEY` set. To actually exercise it once both modules exist:

```bash
# terminal 1
cd ai/mcp-repo-explorer/mcp-server && mvn spring-boot:run

# terminal 2
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/mcp-repo-explorer/mcp-client-agent
mvn test -Dtest=McpAgentIntegrationTest -Dgroups=integration
```

- [ ] **Step 3: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-client-agent/src/test/java/com/testingai/mcpexplorer/client/integration/McpAgentIntegrationTest.java
git commit -m "test(mcp-repo-explorer): add end-to-end integration test for mcp-client-agent"
```

---

### Task 12: `mcp-client-agent` README

**Files:**
- Create: `ai/mcp-repo-explorer/mcp-client-agent/README.md`

- [ ] **Step 1: Write the README**

```markdown
# MCP Client Agent (Repo Explorer)

A Claude-powered agent that connects to `ai/mcp-repo-explorer/mcp-server` as a real MCP client. Unlike this
repo's other `ai/` agents (`task-automation-agent`, `code-review-agent`), this one never hardcodes what tools
exist — it calls `tools/list` over the protocol on every request and converts whatever it gets back into
Anthropic tool definitions before handing them to Claude.

## Endpoint

| Method | Path | Description |
|--------|------|--------------|
| `POST` | `/api/mcp-agent/run` | Runs the agentic loop for a natural-language goal; returns `{ answer, steps, iterations, truncated }` |

## Prerequisites

| What | Where |
|------|-------|
| `ANTHROPIC_API_KEY` | https://console.anthropic.com |
| `mcp-server` running on `:8092` | See `ai/mcp-repo-explorer/mcp-server/README.md` |

## Running

Start `mcp-server` first, then:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/mcp-repo-explorer/mcp-client-agent
mvn spring-boot:run
```

App starts on **port 8093**.

## Try it

```bash
curl -s -X POST http://localhost:8093/api/mcp-agent/run \
  -H "Content-Type: application/json" \
  -d '{"goal": "Which modules under message-brokers mention Kafka?"}' | jq .
```

Example response:

```json
{
  "answer": "message-brokers/kafka is the Kafka demo module.",
  "steps": [
    { "tool": "list_modules", "input": "{}", "output": "[{\"category\":\"message-brokers\",\"module\":\"kafka\"}, ...]" },
    { "tool": "search_readmes", "input": "{keyword=kafka}", "output": "[{\"path\":\"message-brokers/kafka/README.md\",\"line\":\"...\"}]" }
  ],
  "iterations": 2,
  "truncated": false
}
```

## Configuration

| Property | Default | Description |
|----------|---------|--------------|
| `agent.max-iterations` | `10` | Turns before truncation |
| `anthropic.model` | `claude-sonnet-4-6` | Claude model |
| `mcp.server-url` | `http://localhost:8092` | Base URL of `mcp-server` |

## Build & test

```bash
cd ai/mcp-repo-explorer/mcp-client-agent

mvn clean package   # build
mvn test            # unit tests (no API keys needed)
```

To run the integration test (requires `ANTHROPIC_API_KEY` and both apps running):

```bash
mvn test -Dtest=McpAgentIntegrationTest -Dgroups=integration
```

## Tech stack

- Java 21, Spring Boot 3.4.4
- [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) 2.40.1
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) 0.17.2 — `HttpClientStreamableHttpTransport` + `McpSyncClient`
```

- [ ] **Step 2: Commit**

```bash
git add ai/mcp-repo-explorer/mcp-client-agent/README.md
git commit -m "docs(mcp-repo-explorer): add mcp-client-agent README"
```

---

### Task 13: Top-level `ai/mcp-repo-explorer` README

**Files:**
- Create: `ai/mcp-repo-explorer/README.md`

- [ ] **Step 1: Write the README**

```markdown
# MCP Repo Explorer

A pair of Spring Boot demos showing the **Model Context Protocol** end to end, as a network boundary between
two independent processes — as opposed to the hand-rolled tool dispatch used by `ai/task-automation-agent`
and `ai/code-review-agent`.

- **[`mcp-server`](mcp-server/README.md)** — a real MCP server (Streamable HTTP, official Java SDK) exposing
  three tools that introspect this repository's own structure: `list_modules`, `read_readme`, `search_readmes`.
- **[`mcp-client-agent`](mcp-client-agent/README.md)** — a Claude-powered agent that connects to `mcp-server`
  as an MCP client, discovering tools at runtime via `tools/list` rather than hardcoding them.

## Quickstart

```bash
# terminal 1
cd ai/mcp-repo-explorer/mcp-server
mvn spring-boot:run          # :8092

# terminal 2
export ANTHROPIC_API_KEY=sk-ant-...
cd ai/mcp-repo-explorer/mcp-client-agent
mvn spring-boot:run           # :8093

# terminal 3
curl -s -X POST http://localhost:8093/api/mcp-agent/run \
  -H "Content-Type: application/json" \
  -d '{"goal": "Which modules under message-brokers mention Kafka?"}' | jq .
```

No external API keys or infrastructure are needed beyond `ANTHROPIC_API_KEY` for `mcp-client-agent` —
`mcp-server` reads this repo's own filesystem, rooted at the directory containing `CLAUDE.md`.
```

- [ ] **Step 2: Commit**

```bash
git add ai/mcp-repo-explorer/README.md
git commit -m "docs(mcp-repo-explorer): add top-level overview README"
```

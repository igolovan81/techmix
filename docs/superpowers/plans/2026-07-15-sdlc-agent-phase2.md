# SDLC Agent Phase 2 (Fix) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/sdlc/fix` to the existing `sdlc-agent-demo` module: an agent that takes Phase 1's `RootCauseHypothesis` and proposes a real code fix via `read_file`/`list_files`/`write_file`/`git_commit_branch` tools, scoped to a disposable sandbox git repo created fresh per request and seeded with the exact `checkout-service` bug Phase 1's Splunk logs already point to.

**Architecture:** `FixService` reuses a newly-extracted `InvestigationLoop` (pulled out of `InvestigateService`) to get a `RootCauseHypothesis`, then runs a second agentic loop — structurally identical to `InvestigateService`'s existing loop — against a `SandboxRepo` (real JGit repo in a temp dir) via `FixToolExecutor`. All four tools are guarded by `SandboxPathGuard` against path traversal. `write_file` replaces the concept doc's `write_patch(diff)` for reliability; a real unified diff is computed afterward via JGit's `DiffFormatter`.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Anthropic Java SDK 2.40.1 (existing), `org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r` (new — pure-Java git, no shelling out), JUnit 5 + Mockito + AssertJ (existing).

## Global Constraints

- All Global Constraints from `docs/superpowers/plans/2026-07-13-sdlc-agent-phase1.md` still apply (standalone module, plain constructors, `@MockBean`, no Lombok annotations, `excludedGroups=integration` for the integration test).
- No push, merge, or PR/MR creation anywhere in this phase — `git_commit_branch` only ever commits locally within the disposable sandbox.
- No file deletion or rename tools — `write_file` (create/overwrite) is the only mutation.
- The sandbox is a fresh `Files.createTempDirectory` per request, always cleaned up in a `finally` block (gated by `sandbox.cleanup`, default `true`).
- Tool classes that operate on a per-request `SandboxRepo` (`ReadFileTool`, `ListFilesTool`, `WriteFileTool`, `GitCommitBranchTool`, `FixToolExecutor`) are plain classes/static utilities, **not** Spring beans — each request gets its own sandbox instance, incompatible with singleton bean scope.
- JGit commits use a fixed `PersonIdent("SDLC Agent", "sdlc-agent@example.com")` for both author and committer — never relies on the host machine's `~/.gitconfig` being present.
- Prefer records, pattern matching, switch expressions, text blocks over pre-Java-21 idioms on any line this plan adds.

---

### Task 1: Add JGit dependency and sandbox repo template resources

**Files:**
- Modify: `ai/sdlc-agent/spring-demo/pom.xml`
- Create: `ai/sdlc-agent/spring-demo/src/main/resources/sandbox-repo-template/src/main/java/com/example/checkout/DiscountService.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/resources/sandbox-repo-template/src/main/java/com/example/checkout/CheckoutController.java`

**Interfaces:**
- Produces: `org.eclipse.jgit.api.Git` and related JGit classes on the classpath; two classpath resource files at `sandbox-repo-template/src/main/java/com/example/checkout/{DiscountService,CheckoutController}.java`, consumed by `SandboxRepo.create()` (Task 3).

No dedicated test — these are static resources and a dependency addition; verified indirectly by `SandboxRepoTest` (Task 3), which fails to compile/run without them.

- [ ] **Step 1: Add the JGit dependency**

In `ai/sdlc-agent/spring-demo/pom.xml`, add after the `httpclient5` dependency:

```xml
        <dependency>
            <groupId>org.eclipse.jgit</groupId>
            <artifactId>org.eclipse.jgit</artifactId>
            <version>7.3.0.202506031305-r</version>
        </dependency>
```

- [ ] **Step 2: Create the seeded-bug template files**

`ai/sdlc-agent/spring-demo/src/main/resources/sandbox-repo-template/src/main/java/com/example/checkout/DiscountService.java`:

```java
package com.example.checkout;

import java.math.BigDecimal;

public class DiscountService {

    public BigDecimal apply(BigDecimal price, String discountCode) {
        if (discountCode.length() > 0) {
            return price.multiply(BigDecimal.valueOf(0.9));
        }
        return price;
    }
}
```

`ai/sdlc-agent/spring-demo/src/main/resources/sandbox-repo-template/src/main/java/com/example/checkout/CheckoutController.java`:

```java
package com.example.checkout;

import java.math.BigDecimal;

public class CheckoutController {

    private final DiscountService discountService;

    public CheckoutController(DiscountService discountService) {
        this.discountService = discountService;
    }

    public BigDecimal checkout(BigDecimal price, String discountCode) {
        return discountService.apply(price, discountCode);
    }
}
```

This is deliberately the exact bug already seeded into Splunk by Phase 1's `ai/sdlc-agent/docker/seed-logs.sh` (`DiscountService.apply` calls `discountCode.length()` without a null check).

- [ ] **Step 3: Compile**

Run: `cd ai/sdlc-agent/spring-demo && mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add ai/sdlc-agent/spring-demo/pom.xml ai/sdlc-agent/spring-demo/src/main/resources/sandbox-repo-template/
git commit -m "feat(sdlc-agent): add JGit dependency and sandbox repo template"
```

---

### Task 2: `SandboxPathGuard`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/sandbox/SandboxPathGuard.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/sandbox/SandboxPathGuardTest.java`

**Interfaces:**
- Produces: `SandboxPathGuard.resolve(Path sandboxRoot, String relativePath): Path`, throws `IllegalArgumentException` if the resolved path escapes `sandboxRoot`. Consumed by all four tool classes (Tasks 4–6).

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/sandbox/SandboxPathGuardTest.java`:

```java
package com.testingai.sdlc.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPathGuardTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void resolve_shouldReturnPathWithinSandbox() {
        Path resolved = SandboxPathGuard.resolve(sandboxRoot, "src/Foo.java");

        assertThat(resolved).isEqualTo(sandboxRoot.toAbsolutePath().normalize().resolve("src/Foo.java"));
    }

    @Test
    void resolve_shouldRejectParentTraversal() {
        assertThatThrownBy(() -> SandboxPathGuard.resolve(sandboxRoot, "../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_shouldRejectAbsolutePathOutsideSandbox() {
        assertThatThrownBy(() -> SandboxPathGuard.resolve(sandboxRoot, "/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd ai/sdlc-agent/spring-demo && mvn test -Dtest=SandboxPathGuardTest`
Expected: COMPILATION FAILURE — `SandboxPathGuard` does not exist yet.

- [ ] **Step 3: Implement `SandboxPathGuard`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/sandbox/SandboxPathGuard.java`:

```java
package com.testingai.sdlc.sandbox;

import java.nio.file.Path;

public final class SandboxPathGuard {

    private SandboxPathGuard() {
    }

    public static Path resolve(Path sandboxRoot, String relativePath) {
        Path normalizedRoot = sandboxRoot.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes sandbox root: " + relativePath);
        }
        return resolved;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=SandboxPathGuardTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/sandbox/SandboxPathGuard.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/sandbox/SandboxPathGuardTest.java
git commit -m "feat(sdlc-agent): add SandboxPathGuard"
```

---

### Task 3: `SandboxRepo`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/sandbox/SandboxRepo.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/sandbox/SandboxRepoTest.java`

**Interfaces:**
- Consumes: the template resources (Task 1).
- Produces: `SandboxRepo.create(): SandboxRepo` (static factory); instance methods `root(): Path`, `git(): Git`, `diffAgainstInitialCommit(): String`, `currentBranch(): String`, `currentCommitSha(): String`, `hasCommitted(): boolean`, `cleanup(): void`. Consumed by `FixToolExecutor`/individual tool classes (Tasks 4–7) and `FixService` (Task 10).

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/sandbox/SandboxRepoTest.java`:

```java
package com.testingai.sdlc.sandbox;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxRepoTest {

    @Test
    void create_shouldInitializeGitRepoWithTemplateFilesAndInitialCommit() {
        SandboxRepo sandbox = SandboxRepo.create();
        try {
            assertThat(sandbox.root().resolve("src/main/java/com/example/checkout/DiscountService.java")).exists();
            assertThat(sandbox.root().resolve("src/main/java/com/example/checkout/CheckoutController.java")).exists();
            assertThat(sandbox.currentCommitSha()).isNotBlank();
            assertThat(sandbox.hasCommitted()).isFalse();
        } finally {
            sandbox.cleanup();
        }
    }

    @Test
    void diffAgainstInitialCommit_shouldBeEmptyBeforeAnyChange() {
        SandboxRepo sandbox = SandboxRepo.create();
        try {
            assertThat(sandbox.diffAgainstInitialCommit()).isEmpty();
        } finally {
            sandbox.cleanup();
        }
    }

    @Test
    void diffAgainstInitialCommit_shouldReflectChangesAfterCommit() throws Exception {
        SandboxRepo sandbox = SandboxRepo.create();
        try {
            Path file = sandbox.root().resolve("src/main/java/com/example/checkout/DiscountService.java");
            Files.writeString(file, "public class DiscountService { }");
            sandbox.git().add().addFilepattern(".").call();
            sandbox.git().commit().setMessage("test change").setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com").call();

            String diff = sandbox.diffAgainstInitialCommit();

            assertThat(diff).contains("DiscountService.java");
            assertThat(sandbox.hasCommitted()).isTrue();
        } finally {
            sandbox.cleanup();
        }
    }

    @Test
    void cleanup_shouldDeleteTempDirectory() {
        SandboxRepo sandbox = SandboxRepo.create();
        Path root = sandbox.root();

        sandbox.cleanup();

        assertThat(Files.exists(root)).isFalse();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=SandboxRepoTest`
Expected: COMPILATION FAILURE — `SandboxRepo` does not exist yet.

- [ ] **Step 3: Implement `SandboxRepo`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/sandbox/SandboxRepo.java`:

```java
package com.testingai.sdlc.sandbox;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

public class SandboxRepo {

    private static final PersonIdent AGENT_IDENT = new PersonIdent("SDLC Agent", "sdlc-agent@example.com");
    private static final List<String> TEMPLATE_FILES = List.of(
            "src/main/java/com/example/checkout/DiscountService.java",
            "src/main/java/com/example/checkout/CheckoutController.java");

    private final Path root;
    private final Git git;
    private final ObjectId initialCommitId;

    private SandboxRepo(Path root, Git git, ObjectId initialCommitId) {
        this.root = root;
        this.git = git;
        this.initialCommitId = initialCommitId;
    }

    public static SandboxRepo create() {
        try {
            Path root = Files.createTempDirectory("sdlc-sandbox-");
            copyTemplateFiles(root);
            Git git = Git.init().setDirectory(root.toFile()).setInitialBranch("main").call();
            git.add().addFilepattern(".").call();
            RevCommit commit = git.commit().setMessage("Initial commit").setAuthor(AGENT_IDENT)
                    .setCommitter(AGENT_IDENT).call();
            return new SandboxRepo(root, git, commit.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create sandbox repo", e);
        }
    }

    private static void copyTemplateFiles(Path root) throws IOException {
        for (String relativePath : TEMPLATE_FILES) {
            Path target = root.resolve(relativePath);
            Files.createDirectories(target.getParent());
            try (InputStream in = SandboxRepo.class.getClassLoader()
                    .getResourceAsStream("sandbox-repo-template/" + relativePath)) {
                if (in == null) {
                    throw new IOException("Template resource not found: " + relativePath);
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public Path root() {
        return root;
    }

    public Git git() {
        return git;
    }

    public String diffAgainstInitialCommit() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(git.getRepository());
            ObjectId head = git.getRepository().resolve("HEAD");
            formatter.format(initialCommitId, head);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String currentBranch() {
        try {
            return git.getRepository().getBranch();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String currentCommitSha() {
        try {
            ObjectId head = git.getRepository().resolve("HEAD");
            return head != null ? head.getName() : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean hasCommitted() {
        return !initialCommitId.getName().equals(currentCommitSha());
    }

    public void cleanup() {
        git.close();
        deleteRecursively(root);
    }

    private static void deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=SandboxRepoTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/sandbox/SandboxRepo.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/sandbox/SandboxRepoTest.java
git commit -m "feat(sdlc-agent): add SandboxRepo (JGit-backed disposable repo)"
```

---

### Task 4: `ReadFileTool` and `ListFilesTool`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ReadFileTool.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ListFilesTool.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ReadFileToolTest.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ListFilesToolTest.java`

**Interfaces:**
- Consumes: `SandboxPathGuard` (Task 2).
- Produces: `ReadFileTool.read(Path sandboxRoot, String path): String` (file content or `{"error": ...}` JSON); `ListFilesTool.list(Path sandboxRoot, String dir): String` (JSON array of file names or `{"error": ...}`). Consumed by `FixToolExecutor` (Task 7).

- [ ] **Step 1: Write the failing tests**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ReadFileToolTest.java`:

```java
package com.testingai.sdlc.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void read_shouldReturnFileContent() throws Exception {
        Files.writeString(sandboxRoot.resolve("Foo.java"), "class Foo {}");

        String result = ReadFileTool.read(sandboxRoot, "Foo.java");

        assertThat(result).isEqualTo("class Foo {}");
    }

    @Test
    void read_shouldReturnErrorWhenFileMissing() {
        String result = ReadFileTool.read(sandboxRoot, "Missing.java");

        assertThat(result).contains("error");
    }

    @Test
    void read_shouldReturnErrorWhenPathEscapesSandbox() {
        String result = ReadFileTool.read(sandboxRoot, "../../etc/passwd");

        assertThat(result).contains("error");
    }
}
```

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ListFilesToolTest.java`:

```java
package com.testingai.sdlc.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ListFilesToolTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void list_shouldReturnFileNamesInDirectory() throws Exception {
        Files.createDirectories(sandboxRoot.resolve("src"));
        Files.writeString(sandboxRoot.resolve("src/A.java"), "");
        Files.writeString(sandboxRoot.resolve("src/B.java"), "");

        String result = ListFilesTool.list(sandboxRoot, "src");

        assertThat(result).contains("A.java").contains("B.java");
    }

    @Test
    void list_shouldReturnErrorWhenNotADirectory() throws Exception {
        Files.writeString(sandboxRoot.resolve("file.txt"), "content");

        String result = ListFilesTool.list(sandboxRoot, "file.txt");

        assertThat(result).contains("error");
    }

    @Test
    void list_shouldReturnErrorWhenPathEscapesSandbox() {
        String result = ListFilesTool.list(sandboxRoot, "../../etc");

        assertThat(result).contains("error");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn test -Dtest=ReadFileToolTest,ListFilesToolTest`
Expected: COMPILATION FAILURE — neither class exists yet.

- [ ] **Step 3: Implement `ReadFileTool`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ReadFileTool.java`:

```java
package com.testingai.sdlc.tool;

import com.testingai.sdlc.sandbox.SandboxPathGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReadFileTool {

    private ReadFileTool() {
    }

    public static String read(Path sandboxRoot, String path) {
        try {
            Path resolved = SandboxPathGuard.resolve(sandboxRoot, path);
            if (!Files.exists(resolved)) {
                return errorJson("File not found: " + path);
            }
            return Files.readString(resolved);
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (IOException e) {
            return errorJson("Failed to read " + path + ": " + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"error\": \"" + message.replace("\"", "'") + "\"}";
    }
}
```

- [ ] **Step 4: Implement `ListFilesTool`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ListFilesTool.java`:

```java
package com.testingai.sdlc.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.sandbox.SandboxPathGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class ListFilesTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ListFilesTool() {
    }

    public static String list(Path sandboxRoot, String dir) {
        try {
            Path resolved = SandboxPathGuard.resolve(sandboxRoot, dir);
            if (!Files.isDirectory(resolved)) {
                return errorJson("Not a directory: " + dir);
            }
            try (Stream<Path> stream = Files.list(resolved)) {
                List<String> names = stream.map(p -> p.getFileName().toString()).sorted().toList();
                return OBJECT_MAPPER.writeValueAsString(names);
            }
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (IOException e) {
            return errorJson("Failed to list " + dir + ": " + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"error\": \"" + message.replace("\"", "'") + "\"}";
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest=ReadFileToolTest,ListFilesToolTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ReadFileTool.java \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/ListFilesTool.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ReadFileToolTest.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/ListFilesToolTest.java
git commit -m "feat(sdlc-agent): add ReadFileTool and ListFilesTool"
```

---

### Task 5: `WriteFileTool`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/WriteFileTool.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/WriteFileToolTest.java`

**Interfaces:**
- Consumes: `SandboxPathGuard` (Task 2).
- Produces: `WriteFileTool.write(Path sandboxRoot, String path, String content): String` (`{"status": "written", ...}` or `{"error": ...}`). Consumed by `FixToolExecutor` (Task 7).

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/WriteFileToolTest.java`:

```java
package com.testingai.sdlc.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WriteFileToolTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void write_shouldCreateFileWithContent() throws Exception {
        String result = WriteFileTool.write(sandboxRoot, "src/Foo.java", "class Foo {}");

        assertThat(result).contains("written");
        assertThat(Files.readString(sandboxRoot.resolve("src/Foo.java"))).isEqualTo("class Foo {}");
    }

    @Test
    void write_shouldOverwriteExistingFile() throws Exception {
        Files.writeString(sandboxRoot.resolve("Foo.java"), "old");

        WriteFileTool.write(sandboxRoot, "Foo.java", "new");

        assertThat(Files.readString(sandboxRoot.resolve("Foo.java"))).isEqualTo("new");
    }

    @Test
    void write_shouldReturnErrorWhenPathEscapesSandbox() {
        String result = WriteFileTool.write(sandboxRoot, "../../etc/passwd", "malicious");

        assertThat(result).contains("error");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=WriteFileToolTest`
Expected: COMPILATION FAILURE — `WriteFileTool` does not exist yet.

- [ ] **Step 3: Implement `WriteFileTool`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/WriteFileTool.java`:

```java
package com.testingai.sdlc.tool;

import com.testingai.sdlc.sandbox.SandboxPathGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WriteFileTool {

    private WriteFileTool() {
    }

    public static String write(Path sandboxRoot, String path, String content) {
        try {
            Path resolved = SandboxPathGuard.resolve(sandboxRoot, path);
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);
            return "{\"status\": \"written\", \"path\": \"" + path.replace("\"", "'") + "\"}";
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (IOException e) {
            return errorJson("Failed to write " + path + ": " + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"error\": \"" + message.replace("\"", "'") + "\"}";
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=WriteFileToolTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/WriteFileTool.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/WriteFileToolTest.java
git commit -m "feat(sdlc-agent): add WriteFileTool"
```

---

### Task 6: `GitCommitBranchTool`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/GitCommitBranchTool.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/GitCommitBranchToolTest.java`

**Interfaces:**
- Consumes: `SandboxRepo` (Task 3).
- Produces: `GitCommitBranchTool.commitBranch(SandboxRepo sandbox, String branchName, String message): String` (`{"branch": ..., "commitSha": ...}` or `{"error": ...}`). Consumed by `FixToolExecutor` (Task 7).

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/GitCommitBranchToolTest.java`:

```java
package com.testingai.sdlc.tool;

import com.testingai.sdlc.sandbox.SandboxRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class GitCommitBranchToolTest {

    private SandboxRepo sandbox;

    @BeforeEach
    void setUp() {
        sandbox = SandboxRepo.create();
    }

    @AfterEach
    void tearDown() {
        sandbox.cleanup();
    }

    @Test
    void commitBranch_shouldCreateBranchAndCommitChanges() throws Exception {
        Files.writeString(sandbox.root().resolve("src/main/java/com/example/checkout/DiscountService.java"),
                "changed content");

        String result = GitCommitBranchTool.commitBranch(sandbox, "hotfix/DEMO-101", "Fix the bug");

        assertThat(result).contains("hotfix/DEMO-101").contains("commitSha");
        assertThat(sandbox.currentBranch()).isEqualTo("hotfix/DEMO-101");
        assertThat(sandbox.hasCommitted()).isTrue();
    }

    @Test
    void commitBranch_shouldReturnErrorOnSecondCallWithSameBranchName() {
        GitCommitBranchTool.commitBranch(sandbox, "hotfix/DEMO-101", "first");

        String result = GitCommitBranchTool.commitBranch(sandbox, "hotfix/DEMO-101", "second");

        assertThat(result).contains("error");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=GitCommitBranchToolTest`
Expected: COMPILATION FAILURE — `GitCommitBranchTool` does not exist yet.

- [ ] **Step 3: Implement `GitCommitBranchTool`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/GitCommitBranchTool.java`:

```java
package com.testingai.sdlc.tool;

import com.testingai.sdlc.sandbox.SandboxRepo;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

public final class GitCommitBranchTool {

    private static final PersonIdent AGENT_IDENT = new PersonIdent("SDLC Agent", "sdlc-agent@example.com");

    private GitCommitBranchTool() {
    }

    public static String commitBranch(SandboxRepo sandbox, String branchName, String message) {
        try {
            sandbox.git().checkout().setCreateBranch(true).setName(branchName).call();
            sandbox.git().add().addFilepattern(".").call();
            RevCommit commit = sandbox.git().commit().setMessage(message).setAuthor(AGENT_IDENT)
                    .setCommitter(AGENT_IDENT).call();
            return "{\"branch\": \"" + branchName.replace("\"", "'") + "\", \"commitSha\": \"" + commit.getName()
                    + "\"}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=GitCommitBranchToolTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/GitCommitBranchTool.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/GitCommitBranchToolTest.java
git commit -m "feat(sdlc-agent): add GitCommitBranchTool"
```

---

### Task 7: `FixToolDefinitions` and `FixToolExecutor`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/FixToolDefinitions.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/FixToolExecutor.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/FixToolExecutorTest.java`

**Interfaces:**
- Consumes: `ReadFileTool`, `ListFilesTool`, `WriteFileTool`, `GitCommitBranchTool` (Tasks 4–6), `SandboxRepo` (Task 3).
- Produces: `FixToolDefinitions.all(): List<Tool>` (the 4 Claude tool schemas); `FixToolExecutor(SandboxRepo sandbox)` constructor, `execute(String toolName, JsonValue input): String`. Consumed by `FixService` (Task 10).

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/FixToolExecutorTest.java`:

```java
package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.testingai.sdlc.sandbox.SandboxRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FixToolExecutorTest {

    private SandboxRepo sandbox;
    private FixToolExecutor executor;

    @BeforeEach
    void setUp() {
        sandbox = SandboxRepo.create();
        executor = new FixToolExecutor(sandbox);
    }

    @AfterEach
    void tearDown() {
        sandbox.cleanup();
    }

    @Test
    void execute_shouldDispatchReadFile() {
        String result = executor.execute("read_file",
                JsonValue.from(Map.of("path", "src/main/java/com/example/checkout/DiscountService.java")));

        assertThat(result).contains("class DiscountService");
    }

    @Test
    void execute_shouldDispatchListFiles() {
        String result = executor.execute("list_files",
                JsonValue.from(Map.of("dir", "src/main/java/com/example/checkout")));

        assertThat(result).contains("DiscountService.java").contains("CheckoutController.java");
    }

    @Test
    void execute_shouldDispatchWriteFile() {
        String result = executor.execute("write_file", JsonValue.from(Map.of("path",
                "src/main/java/com/example/checkout/DiscountService.java", "content", "changed")));

        assertThat(result).contains("written");
    }

    @Test
    void execute_shouldDispatchGitCommitBranch() {
        String result = executor.execute("git_commit_branch",
                JsonValue.from(Map.of("branchName", "hotfix/DEMO-101", "message", "test commit")));

        assertThat(result).contains("hotfix/DEMO-101").contains("commitSha");
    }

    @Test
    void execute_shouldReturnErrorForUnknownTool() {
        String result = executor.execute("unknown_tool", JsonValue.from(Map.of()));

        assertThat(result).contains("error").contains("unknown_tool");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=FixToolExecutorTest`
Expected: COMPILATION FAILURE — `FixToolExecutor` does not exist yet.

- [ ] **Step 3: Implement `FixToolDefinitions`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/FixToolDefinitions.java`:

```java
package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.util.List;
import java.util.Map;

public final class FixToolDefinitions {

    private FixToolDefinitions() {
    }

    public static List<Tool> all() {
        return List.of(readFile(), listFiles(), writeFile(), gitCommitBranch());
    }

    public static Tool readFile() {
        return Tool.builder().name("read_file")
                .description("Read the full content of a file in the sandbox repository, given a path relative "
                        + "to the repo root.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("path", JsonValue.from(Map.of("type", "string",
                                        "description", "File path relative to the sandbox repo root")))
                                .build())
                        .required(List.of("path")).putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    public static Tool listFiles() {
        return Tool.builder().name("list_files")
                .description("List file names in a directory of the sandbox repository, given a path relative "
                        + "to the repo root.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("dir", JsonValue.from(Map.of("type", "string", "description",
                                        "Directory path relative to the sandbox repo root")))
                                .build())
                        .required(List.of("dir")).putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    public static Tool writeFile() {
        return Tool.builder().name("write_file")
                .description("Overwrite (or create) a file in the sandbox repository with new content, given a "
                        + "path relative to the repo root.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("path", JsonValue.from(Map.of("type", "string", "description",
                                        "File path relative to the sandbox repo root")))
                                .putAdditionalProperty("content", JsonValue.from(Map.of("type", "string",
                                        "description", "The complete new content of the file")))
                                .build())
                        .required(List.of("path", "content"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build())
                .build();
    }

    public static Tool gitCommitBranch() {
        return Tool.builder().name("git_commit_branch")
                .description("Create a new branch off the current commit and commit all pending changes to it. "
                        + "Call this exactly once, after all fixes have been written.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("branchName", JsonValue.from(Map.of("type", "string",
                                        "description", "The branch name to create, e.g. hotfix/DEMO-101")))
                                .putAdditionalProperty("message", JsonValue.from(Map.of("type", "string",
                                        "description", "The commit message")))
                                .build())
                        .required(List.of("branchName", "message"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build())
                .build();
    }
}
```

- [ ] **Step 4: Implement `FixToolExecutor`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/FixToolExecutor.java`:

```java
package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.testingai.sdlc.sandbox.SandboxRepo;

import java.util.Map;

public class FixToolExecutor {

    private final SandboxRepo sandbox;

    public FixToolExecutor(SandboxRepo sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(String toolName, JsonValue input) {
        try {
            Map<String, Object> fields = input.convert(new TypeReference<Map<String, Object>>() {
            });
            if (fields == null) {
                return "{\"error\": \"Tool input must be a JSON object\"}";
            }
            return switch (toolName) {
                case "read_file" -> ReadFileTool.read(sandbox.root(), requireString(toolName, fields, "path"));
                case "list_files" -> ListFilesTool.list(sandbox.root(), requireString(toolName, fields, "dir"));
                case "write_file" -> WriteFileTool.write(sandbox.root(), requireString(toolName, fields, "path"),
                        requireString(toolName, fields, "content"));
                case "git_commit_branch" -> GitCommitBranchTool.commitBranch(sandbox,
                        requireString(toolName, fields, "branchName"), requireString(toolName, fields, "message"));
                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (MissingFieldException e) {
            return "{\"error\": \"" + e.toolName + ": missing required field '" + e.field + "'\"}";
        } catch (Exception e) {
            return "{\"error\": \"FixToolExecutor error: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String requireString(String toolName, Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            throw new MissingFieldException(toolName, key);
        }
        return value.toString();
    }

    private static final class MissingFieldException extends RuntimeException {
        private final String toolName;
        private final String field;

        MissingFieldException(String toolName, String field) {
            this.toolName = toolName;
            this.field = field;
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest=FixToolExecutorTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/FixToolDefinitions.java \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/tool/FixToolExecutor.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/tool/FixToolExecutorTest.java
git commit -m "feat(sdlc-agent): add FixToolDefinitions and FixToolExecutor"
```

---

### Task 8: Extract `InvestigationLoop` from `InvestigateService`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigationLoop.java`
- Create: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigationLoopTest.java`
- Modify: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigateService.java`
- Modify: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigateServiceTest.java`

**Interfaces:**
- Produces: `InvestigationLoop.investigate(Ticket ticket): InvestigateResponse` — the full agentic loop, unchanged in behavior from the current `InvestigateService.investigate(String)` minus the `ticketSource.fetch` call. `InvestigateService.investigate(String ticketId)` becomes a thin wrapper: fetch, delegate, return. Consumed by `FixService` (Task 10, via `.rootCause()` on the result) and unchanged by `InvestigateController`.
- This task must not change `InvestigateService`'s external behavior or its REST contract — `InvestigateControllerTest` (unmodified) must still pass.

- [ ] **Step 1: Write the failing test for `InvestigationLoop`**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigationLoopTest.java` — this is `InvestigateServiceTest`'s current test bodies, adapted to call `investigationLoop.investigate(TICKET)` directly instead of `investigateService.investigate("DEMO-101")`, with `ticketSource`/its stubbing removed entirely:

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationLoopTest {

    private static final Ticket TICKET = new Ticket("DEMO-101", "Checkout fails with 500 error for some orders",
            "Intermittent failures reported.", "High", "checkout-service", Instant.parse("2026-07-10T10:00:00Z"));

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock
    private ToolExecutor toolExecutor;
    @Mock
    private QueryLogsTool queryLogsTool;

    private InvestigationLoop investigationLoop;

    @BeforeEach
    void setUp() {
        Tool stubTool = Tool.builder().name("query_logs").inputSchema(Tool.InputSchema.builder().build()).build();
        when(queryLogsTool.definition()).thenReturn(stubTool);
        investigationLoop = new InvestigationLoop(anthropic, toolExecutor, queryLogsTool, new AgentProperties(10),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));
    }

    @Test
    void investigate_singleIteration_returnsParsedHypothesis() {
        String json = """
                {"summary": "NPE in DiscountService", "evidence": ["line1"], "confidence": "high", "suspectedFiles": ["DiscountService.java"]}
                """;
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(buildTextMessage(json));

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.rootCause().summary()).isEqualTo("NPE in DiscountService");
        assertThat(result.rootCause().confidence()).isEqualTo("high");
        assertThat(result.rootCause().suspectedFiles()).containsExactly("DiscountService.java");
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
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

        InvestigateResponse result = investigationLoop.investigate(TICKET);

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
        investigationLoop = new InvestigationLoop(anthropic, toolExecutor, queryLogsTool, new AgentProperties(2),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.rootCause().confidence()).isEqualTo("low");
    }

    @Test
    void investigate_fallsBackToLowConfidenceWhenFinalTextIsNotValidJson() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("I couldn't determine a root cause."));

        InvestigateResponse result = investigationLoop.investigate(TICKET);

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

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.rootCause().summary()).isEqualTo("NPE");
        assertThat(result.rootCause().confidence()).isEqualTo("high");
    }

    // --- helpers (unchanged from the old InvestigateServiceTest) ---

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

Run: `mvn test -Dtest=InvestigationLoopTest`
Expected: COMPILATION FAILURE — `InvestigationLoop` does not exist yet.

- [ ] **Step 3: Implement `InvestigationLoop`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigationLoop.java` — the loop body moved verbatim from the current `InvestigateService`, taking a `Ticket` instead of a `ticketId` and dropping the `TicketSource` dependency:

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
import com.testingai.sdlc.tool.QueryLogsTool;
import com.testingai.sdlc.tool.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class InvestigationLoop {

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
    private final ToolExecutor toolExecutor;
    private final QueryLogsTool queryLogsTool;
    private final AgentProperties agentProperties;
    private final AnthropicProperties anthropicProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvestigationLoop(AnthropicClient anthropic, ToolExecutor toolExecutor, QueryLogsTool queryLogsTool,
            AgentProperties agentProperties, AnthropicProperties anthropicProperties) {
        this.anthropic = anthropic;
        this.toolExecutor = toolExecutor;
        this.queryLogsTool = queryLogsTool;
        this.agentProperties = agentProperties;
        this.anthropicProperties = anthropicProperties;
    }

    public InvestigateResponse investigate(Ticket ticket) {
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

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=InvestigationLoopTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Rewrite `InvestigateService` as a thin wrapper**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigateService.java`:

```java
package com.testingai.sdlc.service;

import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import org.springframework.stereotype.Service;

@Service
public class InvestigateService {

    private final TicketSource ticketSource;
    private final InvestigationLoop investigationLoop;

    public InvestigateService(TicketSource ticketSource, InvestigationLoop investigationLoop) {
        this.ticketSource = ticketSource;
        this.investigationLoop = investigationLoop;
    }

    public InvestigateResponse investigate(String ticketId) {
        Ticket ticket = ticketSource.fetch(ticketId);
        return investigationLoop.investigate(ticket);
    }
}
```

- [ ] **Step 6: Rewrite `InvestigateServiceTest` as a thin delegation test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigateServiceTest.java`:

```java
package com.testingai.sdlc.service;

import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigateServiceTest {

    private static final Ticket TICKET = new Ticket("DEMO-101", "title", "description", "High", "checkout-service",
            Instant.parse("2026-07-10T10:00:00Z"));

    @Mock
    private TicketSource ticketSource;
    @Mock
    private InvestigationLoop investigationLoop;

    private InvestigateService investigateService;

    @BeforeEach
    void setUp() {
        investigateService = new InvestigateService(ticketSource, investigationLoop);
    }

    @Test
    void investigate_shouldFetchTicketThenDelegateToInvestigationLoop() {
        when(ticketSource.fetch("DEMO-101")).thenReturn(TICKET);
        InvestigateResponse expected = new InvestigateResponse(
                new RootCauseHypothesis("summary", List.of(), "high", List.of()), 2, List.of(), false);
        when(investigationLoop.investigate(TICKET)).thenReturn(expected);

        InvestigateResponse result = investigateService.investigate("DEMO-101");

        assertThat(result).isEqualTo(expected);
        verify(ticketSource).fetch("DEMO-101");
        verify(investigationLoop).investigate(TICKET);
    }
}
```

- [ ] **Step 7: Run the full test suite to verify nothing else broke**

Run: `mvn test`
Expected: `BUILD SUCCESS` — in particular `InvestigateControllerTest` (unmodified) still passes, confirming `InvestigateService`'s external behavior is unchanged.

- [ ] **Step 8: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigationLoop.java \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/InvestigateService.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigationLoopTest.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/InvestigateServiceTest.java
git commit -m "refactor(sdlc-agent): extract InvestigationLoop out of InvestigateService"
```

---

### Task 9: `FixRequest` and `FixResponse` model records

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/FixRequest.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/FixResponse.java`

**Interfaces:**
- Produces: `FixRequest(@NotBlank String ticketId)`; `FixResponse(RootCauseHypothesis rootCause, String summary, String patch, String branchName, String commitSha, int iterations, List<StepRecord> steps, boolean truncated)`. Consumed by `FixService` (Task 10) and `FixController` (Task 11).

No dedicated test — pure data records, matching this repo's convention (verified indirectly by `FixServiceTest`/`FixControllerTest`).

- [ ] **Step 1: Create the records**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/FixRequest.java`:

```java
package com.testingai.sdlc.model;

import jakarta.validation.constraints.NotBlank;

public record FixRequest(@NotBlank String ticketId) {
}
```

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/FixResponse.java`:

```java
package com.testingai.sdlc.model;

import java.util.List;

public record FixResponse(RootCauseHypothesis rootCause, String summary, String patch, String branchName,
        String commitSha, int iterations, List<StepRecord> steps, boolean truncated) {
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/FixRequest.java \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/model/FixResponse.java
git commit -m "feat(sdlc-agent): add FixRequest/FixResponse model records"
```

---

### Task 10: `FixService` — the fix agentic loop

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/SandboxProperties.java`
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/FixService.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/FixServiceTest.java`
- Modify: `ai/sdlc-agent/spring-demo/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `AnthropicClient` (existing bean), `TicketSource`, `InvestigationLoop` (Task 8), `AgentProperties`/`AnthropicProperties` (existing), `SandboxRepo`, `FixToolExecutor`/`FixToolDefinitions` (Tasks 3, 7), `FixRequest`/`FixResponse` (Task 9).
- Produces: `FixService.fix(String ticketId): FixResponse`. Consumed by `FixController` (Task 11).
- Unlike `InvestigateService`/`InvestigationLoop`'s tests, this test does **not** mock the tool layer — `FixToolExecutor` and `SandboxRepo` are real, so the test exercises genuine JGit operations end-to-end (only `AnthropicClient`, `TicketSource`, and `InvestigationLoop` are mocked).

- [ ] **Step 1: Create `SandboxProperties`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/SandboxProperties.java`:

```java
package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sandbox")
public record SandboxProperties(boolean cleanup) {
}
```

- [ ] **Step 2: Add the `sandbox` block to `application.yml`**

In `ai/sdlc-agent/spring-demo/src/main/resources/application.yml`, add:

```yaml
sandbox:
  cleanup: true
```

- [ ] **Step 3: Write the failing tests**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/FixServiceTest.java`:

```java
package com.testingai.sdlc.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.config.SandboxProperties;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixServiceTest {

    private static final Ticket TICKET = new Ticket("DEMO-101", "title", "description", "High", "checkout-service",
            Instant.parse("2026-07-10T10:00:00Z"));
    private static final RootCauseHypothesis ROOT_CAUSE = new RootCauseHypothesis(
            "NullPointerException in DiscountService.apply when discountCode is null", List.of(), "high",
            List.of("DiscountService.java"));

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock
    private TicketSource ticketSource;
    @Mock
    private InvestigationLoop investigationLoop;

    private FixService fixService;

    @BeforeEach
    void setUp() {
        when(ticketSource.fetch("DEMO-101")).thenReturn(TICKET);
        when(investigationLoop.investigate(TICKET))
                .thenReturn(new InvestigateResponse(ROOT_CAUSE, 1, List.of(), false));
        fixService = new FixService(anthropic, ticketSource, investigationLoop, new AgentProperties(10),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"), new SandboxProperties(true));
    }

    @Test
    void fix_singleIteration_noToolCalls_returnsSummaryWithoutCommit() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("Investigated but made no changes."));

        FixResponse result = fixService.fix("DEMO-101");

        assertThat(result.rootCause()).isEqualTo(ROOT_CAUSE);
        assertThat(result.summary()).isEqualTo("Investigated but made no changes.");
        assertThat(result.branchName()).isNull();
        assertThat(result.commitSha()).isNull();
        assertThat(result.patch()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void fix_writesFileAndCommitsBranch_returnsPatchAndBranch() {
        Message readCall = buildToolUseMessage("t1", "read_file",
                JsonValue.from(Map.of("path", "src/main/java/com/example/checkout/DiscountService.java")));
        Message writeCall = buildToolUseMessage("t2", "write_file",
                JsonValue.from(Map.of("path", "src/main/java/com/example/checkout/DiscountService.java", "content",
                        "public class DiscountService { public java.math.BigDecimal apply("
                                + "java.math.BigDecimal price, String discountCode) { "
                                + "if (discountCode != null && discountCode.length() > 0) { "
                                + "return price.multiply(java.math.BigDecimal.valueOf(0.9)); } "
                                + "return price; } }")));
        Message commitCall = buildToolUseMessage("t3", "git_commit_branch",
                JsonValue.from(Map.of("branchName", "hotfix/DEMO-101", "message", "Fix NPE in DiscountService")));
        Message finalMessage = buildTextMessage("Added a null check before calling discountCode.length().");

        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(readCall).thenReturn(writeCall)
                .thenReturn(commitCall).thenReturn(finalMessage);

        FixResponse result = fixService.fix("DEMO-101");

        assertThat(result.branchName()).isEqualTo("hotfix/DEMO-101");
        assertThat(result.commitSha()).isNotBlank();
        assertThat(result.patch()).contains("DiscountService.java");
        assertThat(result.steps()).hasSize(3);
        assertThat(result.iterations()).isEqualTo(4);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void fix_truncatesWhenIterationCapReached() {
        Message loopingReadCall = buildToolUseMessage("loop", "read_file",
                JsonValue.from(Map.of("path", "src/main/java/com/example/checkout/DiscountService.java")));
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(loopingReadCall);
        fixService = new FixService(anthropic, ticketSource, investigationLoop, new AgentProperties(2),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"), new SandboxProperties(true));

        FixResponse result = fixService.fix("DEMO-101");

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.branchName()).isNull();
    }

    // --- helpers (mirrors InvestigationLoopTest/AgentServiceTest) ---

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

- [ ] **Step 4: Run it to verify it fails**

Run: `mvn test -Dtest=FixServiceTest`
Expected: COMPILATION FAILURE — `FixService` does not exist yet.

- [ ] **Step 5: Implement `FixService`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/FixService.java`:

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
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.config.SandboxProperties;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.model.StepRecord;
import com.testingai.sdlc.sandbox.SandboxRepo;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import com.testingai.sdlc.tool.FixToolDefinitions;
import com.testingai.sdlc.tool.FixToolExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FixService {

    private static final int MAX_TOKENS = 4096;
    private static final String INSTRUCTIONS = """
            You are fixing a production bug in a sandbox git repository, based on a root-cause \
            investigation. Use list_files and read_file to inspect the repository, write_file to \
            apply your fix, and finish by calling git_commit_branch exactly once with a branch \
            name like hotfix/<TICKET-ID> and a clear commit message. After committing, respond \
            with a short plain-text summary of what you changed and why.""";

    private final AnthropicClient anthropic;
    private final TicketSource ticketSource;
    private final InvestigationLoop investigationLoop;
    private final AgentProperties agentProperties;
    private final AnthropicProperties anthropicProperties;
    private final SandboxProperties sandboxProperties;

    public FixService(AnthropicClient anthropic, TicketSource ticketSource, InvestigationLoop investigationLoop,
            AgentProperties agentProperties, AnthropicProperties anthropicProperties,
            SandboxProperties sandboxProperties) {
        this.anthropic = anthropic;
        this.ticketSource = ticketSource;
        this.investigationLoop = investigationLoop;
        this.agentProperties = agentProperties;
        this.anthropicProperties = anthropicProperties;
        this.sandboxProperties = sandboxProperties;
    }

    public FixResponse fix(String ticketId) {
        Ticket ticket = ticketSource.fetch(ticketId);
        InvestigateResponse investigation = investigationLoop.investigate(ticket);
        RootCauseHypothesis rootCause = investigation.rootCause();

        SandboxRepo sandbox = SandboxRepo.create();
        try {
            return runFixLoop(rootCause, sandbox);
        } finally {
            if (sandboxProperties.cleanup()) {
                sandbox.cleanup();
            }
        }
    }

    private FixResponse runFixLoop(RootCauseHypothesis rootCause, SandboxRepo sandbox) {
        FixToolExecutor toolExecutor = new FixToolExecutor(sandbox);

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(buildInitialPrompt(rootCause))
                .build());

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProperties.maxIterations()) {
            Message response = anthropic.messages().create(MessageCreateParams.builder()
                    .model(anthropicProperties.model()).maxTokens(MAX_TOKENS).messages(messages)
                    .addTool(FixToolDefinitions.readFile()).addTool(FixToolDefinitions.listFiles())
                    .addTool(FixToolDefinitions.writeFile()).addTool(FixToolDefinitions.gitCommitBranch())
                    .toolChoice(ToolChoiceAuto.builder().build()).build());

            List<ContentBlockParam> assistantBlocks = response.content().stream().map(ContentBlock::toParam)
                    .filter(Objects::nonNull).toList();
            messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantBlocks).build());

            iterations++;

            List<ToolUseBlock> toolCalls = response.content().stream().filter(ContentBlock::isToolUse)
                    .map(ContentBlock::asToolUse).toList();

            if (toolCalls.isEmpty()) {
                String summary = response.content().stream().filter(ContentBlock::isText).map(ContentBlock::asText)
                        .map(TextBlock::text).collect(Collectors.joining(""));
                return buildResponse(rootCause, summary, sandbox, steps, iterations, false);
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

        return buildResponse(rootCause, "Fix loop truncated: iteration limit reached before a summary was produced.",
                sandbox, steps, iterations, true);
    }

    private String buildInitialPrompt(RootCauseHypothesis rootCause) {
        return INSTRUCTIONS + "\n\nRoot cause: " + rootCause.summary() + "\nSuspected files: "
                + rootCause.suspectedFiles() + "\nEvidence: " + rootCause.evidence();
    }

    private FixResponse buildResponse(RootCauseHypothesis rootCause, String summary, SandboxRepo sandbox,
            List<StepRecord> steps, int iterations, boolean truncated) {
        String patch = sandbox.diffAgainstInitialCommit();
        boolean committed = sandbox.hasCommitted();
        String branchName = committed ? sandbox.currentBranch() : null;
        String commitSha = committed ? sandbox.currentCommitSha() : null;
        return new FixResponse(rootCause, summary, patch, branchName, commitSha, iterations, steps, truncated);
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn test -Dtest=FixServiceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/config/SandboxProperties.java \
  ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/service/FixService.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/service/FixServiceTest.java \
  ai/sdlc-agent/spring-demo/src/main/resources/application.yml
git commit -m "feat(sdlc-agent): add FixService agentic loop"
```

---

### Task 11: `FixController`

**Files:**
- Create: `ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/controller/FixController.java`
- Test: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/controller/FixControllerTest.java`

**Interfaces:**
- Consumes: `FixService` (Task 10), `FixRequest`/`FixResponse` (Task 9).
- Produces: `POST /api/sdlc/fix`.

- [ ] **Step 1: Write the failing test**

`ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/controller/FixControllerTest.java`:

```java
package com.testingai.sdlc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.model.FixRequest;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.service.FixService;
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

@WebMvcTest(FixController.class)
class FixControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private FixService fixService;

    @Test
    void fix_returnsFixResponseAsJson() throws Exception {
        RootCauseHypothesis rootCause = new RootCauseHypothesis("NPE in DiscountService", List.of(), "high",
                List.of("DiscountService.java"));
        FixResponse expected = new FixResponse(rootCause, "Added a null check.", "diff --git a/... b/...",
                "hotfix/DEMO-101", "abc123", 4, List.of(), false);
        when(fixService.fix("DEMO-101")).thenReturn(expected);

        mockMvc.perform(post("/api/sdlc/fix").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FixRequest("DEMO-101")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.branchName").value("hotfix/DEMO-101"))
                .andExpect(jsonPath("$.commitSha").value("abc123")).andExpect(jsonPath("$.iterations").value(4))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void fix_returns400WhenTicketIdIsBlank() throws Exception {
        mockMvc.perform(
                post("/api/sdlc/fix").contentType(MediaType.APPLICATION_JSON).content("{\"ticketId\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=FixControllerTest`
Expected: COMPILATION FAILURE — `FixController` does not exist yet.

- [ ] **Step 3: Implement `FixController`**

`ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/controller/FixController.java`:

```java
package com.testingai.sdlc.controller;

import com.testingai.sdlc.model.FixRequest;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.service.FixService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sdlc")
public class FixController {

    private final FixService fixService;

    public FixController(FixService fixService) {
        this.fixService = fixService;
    }

    @PostMapping("/fix")
    public FixResponse fix(@RequestBody @Valid FixRequest request) {
        return fixService.fix(request.ticketId());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=FixControllerTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full unit test suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all test classes (Phase 1 + Phase 2) passing.

- [ ] **Step 6: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/main/java/com/testingai/sdlc/controller/FixController.java \
  ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/controller/FixControllerTest.java
git commit -m "feat(sdlc-agent): add FixController"
```

---

### Task 12: Extend the integration test

**Files:**
- Modify: `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/integration/SdlcAgentIntegrationTest.java`

**Interfaces:**
- Consumes: the full running app, real credentials, real Splunk — same preconditions as the existing `investigate_withRealApis_returnsNonBlankRootCause()` test.

- [ ] **Step 1: Add the fix-path test**

In `ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/integration/SdlcAgentIntegrationTest.java`, add a new test method (and the `FixResponse` import) alongside the existing one:

```java
    @Test
    void fix_withRealApis_createsHotfixBranchWithPatch() {
        var request = Map.of("ticketId", "DEMO-101");

        ResponseEntity<FixResponse> response = http.postForEntity("http://localhost:" + port + "/api/sdlc/fix",
                request, FixResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().branchName()).isNotBlank();
        assertThat(response.getBody().patch()).isNotBlank();
    }
```

Add the import: `import com.testingai.sdlc.model.FixResponse;`

- [ ] **Step 2: Verify it's still excluded from the regular test run**

Run: `mvn test`
Expected: `BUILD SUCCESS`, surefire report does not mention `SdlcAgentIntegrationTest`.

- [ ] **Step 3: Commit**

```bash
git add ai/sdlc-agent/spring-demo/src/test/java/com/testingai/sdlc/integration/SdlcAgentIntegrationTest.java
git commit -m "test(sdlc-agent): extend integration test to cover the fix path"
```

---

### Task 13: Update READMEs

**Files:**
- Modify: `ai/sdlc-agent/spring-demo/README.md`
- Modify: `ai/sdlc-agent/README.md`

- [ ] **Step 1: Add the Fix endpoint to the module README**

In `ai/sdlc-agent/spring-demo/README.md`, after the existing `## Try it` section's `curl` example for `/investigate`, add:

```markdown
## Try the Fix endpoint

```bash
curl -s -X POST http://localhost:8089/api/sdlc/fix \
  -H "Content-Type: application/json" \
  -d '{"ticketId": "DEMO-101"}' | jq .
```

This re-runs the investigation above internally, then lets Claude read/write files and commit a fix in a disposable sandbox git repo (seeded with the exact `checkout-service` bug the investigation points to) — never touching any real codebase, never pushing or merging.

Example response:

```json
{
  "rootCause": { "summary": "...", "evidence": [...], "confidence": "high", "suspectedFiles": ["DiscountService.java"] },
  "summary": "Added a null check before calling discountCode.length() in DiscountService.apply.",
  "patch": "diff --git a/src/main/java/com/example/checkout/DiscountService.java b/...",
  "branchName": "hotfix/DEMO-101",
  "commitSha": "a1b2c3d...",
  "iterations": 4,
  "steps": [
    {"tool": "read_file", "input": "{path=...}", "output": "..."},
    {"tool": "write_file", "input": "{path=..., content=...}", "output": "{\"status\":\"written\"}"},
    {"tool": "git_commit_branch", "input": "{branchName=hotfix/DEMO-101, message=...}", "output": "{\"branch\":\"hotfix/DEMO-101\",\"commitSha\":\"a1b2c3d\"}"}
  ],
  "truncated": false
}
```
```

Also add a new row to the module README's `## Configuration` table:

```markdown
| `sandbox.cleanup` | `true` | Whether the disposable sandbox repo is deleted after each `/fix` request (set `false` to inspect it) |
```

And update the `## Scope` section's closing line from "Fix (propose + commit a patch), Deploy, Verify, and Release remain future phases" to "Deploy, Verify, and Release remain future phases — Fix is now implemented."

- [ ] **Step 2: Update the concept doc's phased build plan**

In `ai/sdlc-agent/README.md`, change:

```markdown
| **Phase 2** | Fix (propose + commit to a branch, never push/merge) | Requires the write-tool sandboxing above; a human reviews the branch before it goes anywhere. |
```

to:

```markdown
| **Phase 2 — implemented** | Fix (propose + commit to a branch, never push/merge) | See [`spring-demo/`](spring-demo/) — `write_file` replaces the original `write_patch(diff)` sketch (more reliable for an LLM to produce than a unified diff); operates on a disposable sandbox repo seeded with Phase 1's bug scenario, not a configurable arbitrary `target-repo.path`. A human still reviews the branch before it goes anywhere real. |
```

Also update the tool surface code block from `tools: read_file, list_files, write_patch, git_commit_branch` to `tools: read_file, list_files, write_file, git_commit_branch`, and the bullet list below it to describe `write_file(path, content)` instead of `write_patch(path, diff)`.

- [ ] **Step 3: Commit**

```bash
git add ai/sdlc-agent/spring-demo/README.md ai/sdlc-agent/README.md
git commit -m "docs(sdlc-agent): document the Fix endpoint"
```

---

### Task 14: Final build verification

**Files:** none (verification only)

- [ ] **Step 1: Full module build**

Run: `cd ai/sdlc-agent/spring-demo && mvn clean package`
Expected: `BUILD SUCCESS`, all unit tests pass, `SdlcAgentIntegrationTest` excluded.

- [ ] **Step 2: Verify test count**

Run: `mvn test && grep -h "Tests run" target/surefire-reports/*.txt | awk -F'Tests run: |,' '{sum+=$2} END {print sum}'`
Expected: Phase 1's 26 tests, minus the old 5-test `InvestigateServiceTest` (now 1 test) plus the new 5-test `InvestigationLoopTest`, plus this phase's new test classes: `SandboxPathGuardTest` (3) + `SandboxRepoTest` (4) + `ReadFileToolTest` (3) + `ListFilesToolTest` (3) + `WriteFileToolTest` (3) + `GitCommitBranchToolTest` (2) + `FixToolExecutorTest` (5) + `FixServiceTest` (3) + `FixControllerTest` (2) = 26 − 5 + 1 + 5 + 3+4+3+3+3+2+5+3+2 = 55 total.

- [ ] **Step 3: Manually verify a real fix run creates a real commit**

With real credentials exported (`ANTHROPIC_API_KEY`, `JIRA_*` or `ZENDESK_*`, `SPLUNK_API_TOKEN`, Splunk running and seeded per Phase 1's README) run:

```bash
mvn spring-boot:run &
sleep 5
curl -s -X POST http://localhost:8089/api/sdlc/fix -H "Content-Type: application/json" \
  -d '{"ticketId": "DEMO-101"}' | jq '.branchName, .commitSha, .patch'
```

Expected: a non-null `branchName` (e.g. `hotfix/DEMO-101`), a non-null `commitSha`, and a `patch` containing a real unified diff touching `DiscountService.java`. Stop the app afterward.

- [ ] **Step 4: Report completion**

No further action — Phase 2 (Fix) is complete. Deploy/Verify/Release remain out of scope per the design spec, to be planned separately.

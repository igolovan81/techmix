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

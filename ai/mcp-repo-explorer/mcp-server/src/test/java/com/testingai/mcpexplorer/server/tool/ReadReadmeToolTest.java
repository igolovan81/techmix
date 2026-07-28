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

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

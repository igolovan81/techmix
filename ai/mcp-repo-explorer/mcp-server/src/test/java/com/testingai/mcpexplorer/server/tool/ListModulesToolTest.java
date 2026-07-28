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

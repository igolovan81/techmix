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

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

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

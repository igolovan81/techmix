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

    private String runAndFilter(ParsedDiff parsed, AnalyserFunction analyser) throws Exception {
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

    private String extractDiff(JsonValue input) {
        JsonNode node = objectMapper.valueToTree(input);
        JsonNode diff = node.get("diff");
        if (diff == null || !diff.isTextual()) {
            throw new IllegalArgumentException("Missing or non-string 'diff' field in tool input");
        }
        return diff.asText();
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

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

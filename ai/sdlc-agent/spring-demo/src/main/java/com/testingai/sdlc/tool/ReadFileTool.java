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

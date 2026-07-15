package com.testingai.sdlc.tool;

import com.testingai.sdlc.sandbox.SandboxPathGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WriteFileTool {

    private WriteFileTool() {
    }

    public static String write(Path sandboxRoot, String path, String content) {
        try {
            Path resolved = SandboxPathGuard.resolve(sandboxRoot, path);
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);
            return "{\"status\": \"written\", \"path\": \"" + path.replace("\"", "'") + "\"}";
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (IOException e) {
            return errorJson("Failed to write " + path + ": " + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"error\": \"" + message.replace("\"", "'") + "\"}";
    }
}

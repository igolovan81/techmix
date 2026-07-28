package com.testingai.mcpexplorer.server.tool;

import java.nio.file.Path;

public final class RepoPathGuard {

    private RepoPathGuard() {
    }

    public static Path resolve(Path repoRoot, String relativePath) {
        Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes repo root: " + relativePath);
        }
        return resolved;
    }
}

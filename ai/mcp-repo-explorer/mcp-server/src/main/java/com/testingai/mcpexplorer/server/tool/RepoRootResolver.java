package com.testingai.mcpexplorer.server.tool;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RepoRootResolver {

    private static final String MARKER_FILE = "CLAUDE.md";
    private static final int MAX_LEVELS = 10;

    private RepoRootResolver() {
    }

    public static Path resolve() {
        return resolveFrom(Path.of(System.getProperty("user.dir")));
    }

    static Path resolveFrom(Path startDir) {
        Path current = startDir.toAbsolutePath().normalize();
        for (int i = 0; i <= MAX_LEVELS; i++) {
            if (Files.exists(current.resolve(MARKER_FILE))) {
                return current;
            }
            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }
        throw new IllegalStateException(
                "Could not locate repo root (" + MARKER_FILE + ") within " + MAX_LEVELS
                        + " levels above " + startDir);
    }
}

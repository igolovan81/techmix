package com.testingai.sdlc.sandbox;

import java.nio.file.Path;

public final class SandboxPathGuard {

    private SandboxPathGuard() {
    }

    public static Path resolve(Path sandboxRoot, String relativePath) {
        Path normalizedRoot = sandboxRoot.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes sandbox root: " + relativePath);
        }
        return resolved;
    }
}

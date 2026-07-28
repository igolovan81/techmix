package com.testingai.mcpexplorer.server.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoPathGuardTest {

    @Test
    void resolve_allowsPathWithinRoot(@TempDir Path repoRoot) {
        Path resolved = RepoPathGuard.resolve(repoRoot, "ai/code-review-agent");

        assertThat(resolved).isEqualTo(repoRoot.toAbsolutePath().normalize().resolve("ai/code-review-agent"));
    }

    @Test
    void resolve_rejectsPathTraversal(@TempDir Path repoRoot) {
        assertThatThrownBy(() -> RepoPathGuard.resolve(repoRoot, "../../../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

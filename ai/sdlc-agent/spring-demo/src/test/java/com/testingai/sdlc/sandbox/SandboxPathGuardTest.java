package com.testingai.sdlc.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPathGuardTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void resolve_shouldReturnPathWithinSandbox() {
        Path resolved = SandboxPathGuard.resolve(sandboxRoot, "src/Foo.java");

        assertThat(resolved).isEqualTo(sandboxRoot.toAbsolutePath().normalize().resolve("src/Foo.java"));
    }

    @Test
    void resolve_shouldRejectParentTraversal() {
        assertThatThrownBy(() -> SandboxPathGuard.resolve(sandboxRoot, "../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_shouldRejectAbsolutePathOutsideSandbox() {
        assertThatThrownBy(() -> SandboxPathGuard.resolve(sandboxRoot, "/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

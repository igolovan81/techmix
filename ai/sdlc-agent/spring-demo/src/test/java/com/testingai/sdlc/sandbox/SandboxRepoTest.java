package com.testingai.sdlc.sandbox;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxRepoTest {

    @Test
    void create_shouldInitializeGitRepoWithTemplateFilesAndInitialCommit() {
        SandboxRepo sandbox = SandboxRepo.create();
        try {
            assertThat(sandbox.root().resolve("src/main/java/com/example/checkout/DiscountService.java")).exists();
            assertThat(sandbox.root().resolve("src/main/java/com/example/checkout/CheckoutController.java")).exists();
            assertThat(sandbox.currentCommitSha()).isNotBlank();
            assertThat(sandbox.hasCommitted()).isFalse();
        } finally {
            sandbox.cleanup();
        }
    }

    @Test
    void diffAgainstInitialCommit_shouldBeEmptyBeforeAnyChange() {
        SandboxRepo sandbox = SandboxRepo.create();
        try {
            assertThat(sandbox.diffAgainstInitialCommit()).isEmpty();
        } finally {
            sandbox.cleanup();
        }
    }

    @Test
    void diffAgainstInitialCommit_shouldReflectChangesAfterCommit() throws Exception {
        SandboxRepo sandbox = SandboxRepo.create();
        try {
            Path file = sandbox.root().resolve("src/main/java/com/example/checkout/DiscountService.java");
            Files.writeString(file, "public class DiscountService { }");
            sandbox.git().add().addFilepattern(".").call();
            sandbox.git().commit().setMessage("test change").setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com").call();

            String diff = sandbox.diffAgainstInitialCommit();

            assertThat(diff).contains("DiscountService.java");
            assertThat(sandbox.hasCommitted()).isTrue();
        } finally {
            sandbox.cleanup();
        }
    }

    @Test
    void cleanup_shouldDeleteTempDirectory() {
        SandboxRepo sandbox = SandboxRepo.create();
        Path root = sandbox.root();

        sandbox.cleanup();

        assertThat(Files.exists(root)).isFalse();
    }
}

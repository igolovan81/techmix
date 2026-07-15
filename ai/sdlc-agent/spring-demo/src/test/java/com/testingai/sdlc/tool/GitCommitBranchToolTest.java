package com.testingai.sdlc.tool;

import com.testingai.sdlc.sandbox.SandboxRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class GitCommitBranchToolTest {

    private SandboxRepo sandbox;

    @BeforeEach
    void setUp() {
        sandbox = SandboxRepo.create();
    }

    @AfterEach
    void tearDown() {
        sandbox.cleanup();
    }

    @Test
    void commitBranch_shouldCreateBranchAndCommitChanges() throws Exception {
        Files.writeString(sandbox.root().resolve("src/main/java/com/example/checkout/DiscountService.java"),
                "changed content");

        String result = GitCommitBranchTool.commitBranch(sandbox, "hotfix/DEMO-101", "Fix the bug");

        assertThat(result).contains("hotfix/DEMO-101").contains("commitSha");
        assertThat(sandbox.currentBranch()).isEqualTo("hotfix/DEMO-101");
        assertThat(sandbox.hasCommitted()).isTrue();
    }

    @Test
    void commitBranch_shouldReturnErrorOnSecondCallWithSameBranchName() {
        GitCommitBranchTool.commitBranch(sandbox, "hotfix/DEMO-101", "first");

        String result = GitCommitBranchTool.commitBranch(sandbox, "hotfix/DEMO-101", "second");

        assertThat(result).contains("error");
    }
}

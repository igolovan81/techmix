package com.testingai.mcpexplorer.server.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoRootResolverTest {

    @Test
    void resolveFrom_findsMarkerInAncestor(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("CLAUDE.md"));
        Path nested = tempDir.resolve("a/b/c");
        Files.createDirectories(nested);

        Path result = RepoRootResolver.resolveFrom(nested);

        assertThat(result).isEqualTo(tempDir.toAbsolutePath().normalize());
    }

    @Test
    void resolveFrom_throwsWhenMarkerNotFoundWithinCap(@TempDir Path tempDir) throws IOException {
        Path deep = tempDir;
        for (int i = 0; i < 12; i++) {
            deep = deep.resolve("level" + i);
        }
        Files.createDirectories(deep);
        Path finalDeep = deep;

        assertThatThrownBy(() -> RepoRootResolver.resolveFrom(finalDeep))
                .isInstanceOf(IllegalStateException.class);
    }
}

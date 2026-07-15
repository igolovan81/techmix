package com.testingai.sdlc.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ListFilesToolTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void list_shouldReturnFileNamesInDirectory() throws Exception {
        Files.createDirectories(sandboxRoot.resolve("src"));
        Files.writeString(sandboxRoot.resolve("src/A.java"), "");
        Files.writeString(sandboxRoot.resolve("src/B.java"), "");

        String result = ListFilesTool.list(sandboxRoot, "src");

        assertThat(result).contains("A.java").contains("B.java");
    }

    @Test
    void list_shouldReturnErrorWhenNotADirectory() throws Exception {
        Files.writeString(sandboxRoot.resolve("file.txt"), "content");

        String result = ListFilesTool.list(sandboxRoot, "file.txt");

        assertThat(result).contains("error");
    }

    @Test
    void list_shouldReturnErrorWhenPathEscapesSandbox() {
        String result = ListFilesTool.list(sandboxRoot, "../../etc");

        assertThat(result).contains("error");
    }
}

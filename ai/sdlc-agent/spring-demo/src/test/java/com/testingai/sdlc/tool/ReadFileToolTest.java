package com.testingai.sdlc.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void read_shouldReturnFileContent() throws Exception {
        Files.writeString(sandboxRoot.resolve("Foo.java"), "class Foo {}");

        String result = ReadFileTool.read(sandboxRoot, "Foo.java");

        assertThat(result).isEqualTo("class Foo {}");
    }

    @Test
    void read_shouldReturnErrorWhenFileMissing() {
        String result = ReadFileTool.read(sandboxRoot, "Missing.java");

        assertThat(result).contains("error");
    }

    @Test
    void read_shouldReturnErrorWhenPathEscapesSandbox() {
        String result = ReadFileTool.read(sandboxRoot, "../../etc/passwd");

        assertThat(result).contains("error");
    }
}

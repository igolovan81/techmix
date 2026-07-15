package com.testingai.sdlc.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WriteFileToolTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void write_shouldCreateFileWithContent() throws Exception {
        String result = WriteFileTool.write(sandboxRoot, "src/Foo.java", "class Foo {}");

        assertThat(result).contains("written");
        assertThat(Files.readString(sandboxRoot.resolve("src/Foo.java"))).isEqualTo("class Foo {}");
    }

    @Test
    void write_shouldOverwriteExistingFile() throws Exception {
        Files.writeString(sandboxRoot.resolve("Foo.java"), "old");

        WriteFileTool.write(sandboxRoot, "Foo.java", "new");

        assertThat(Files.readString(sandboxRoot.resolve("Foo.java"))).isEqualTo("new");
    }

    @Test
    void write_shouldReturnErrorWhenPathEscapesSandbox() {
        String result = WriteFileTool.write(sandboxRoot, "../../etc/passwd", "malicious");

        assertThat(result).contains("error");
    }
}

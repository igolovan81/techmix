package com.testingai.reviewer;

import com.testingai.reviewer.model.RawFinding;
import com.testingai.reviewer.tool.CheckstyleTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckstyleToolTest {

    private final CheckstyleTool tool = new CheckstyleTool();

    @Test
    void detectsMethodLengthViolation(@TempDir Path tempDir) throws IOException {
        // Build a method that exceeds 30 lines
        StringBuilder sb = new StringBuilder("public class LongMethod {\n    public void tooLong() {\n");
        for (int i = 1; i <= 31; i++) {
            sb.append("        int v").append(i).append(" = ").append(i).append(";\n");
        }
        sb.append("    }\n}\n");
        Files.writeString(tempDir.resolve("LongMethod.java"), sb.toString(), StandardCharsets.UTF_8);

        List<RawFinding> findings = tool.analyse(tempDir);

        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f ->
                f.tool().equals("checkstyle") && f.rule().contains("MethodLength"));
    }

    @Test
    void returnsEmptyForCleanCode(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Clean.java"), """
                public class Clean {
                    public void ok() {
                        System.out.println("hello");
                    }
                }
                """, StandardCharsets.UTF_8);

        List<RawFinding> findings = tool.analyse(tempDir);

        assertThat(findings).isEmpty();
    }

    @Test
    void toolDefinitionHasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("run_checkstyle");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}

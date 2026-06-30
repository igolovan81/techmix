package com.testingai.reviewer;

import com.testingai.reviewer.model.RawFinding;
import com.testingai.reviewer.tool.PmdTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PmdToolTest {

    private final PmdTool tool = new PmdTool();

    @Test
    void detectsUnusedLocalVariable(@TempDir Path tempDir) throws IOException {
        // Note: PMD 7 filters variables named "unused" or "ignored", so use a plain name
        Files.writeString(tempDir.resolve("HasUnused.java"), """
                public class HasUnused {
                    public void foo() {
                        String greeting = "hello";
                    }
                }
                """, StandardCharsets.UTF_8);

        List<RawFinding> findings = tool.analyse(tempDir);

        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f ->
                f.tool().equals("pmd") && f.rule().contains("UnusedLocalVariable"));
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
        assertThat(tool.definition().name()).isEqualTo("run_pmd");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}

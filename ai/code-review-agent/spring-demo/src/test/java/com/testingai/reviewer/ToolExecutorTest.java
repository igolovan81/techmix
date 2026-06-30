package com.testingai.reviewer;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.reviewer.config.ReviewerProperties;
import com.testingai.reviewer.model.ParsedDiff;
import com.testingai.reviewer.model.RawFinding;
import com.testingai.reviewer.service.DiffParser;
import com.testingai.reviewer.tool.CheckstyleTool;
import com.testingai.reviewer.tool.PmdTool;
import com.testingai.reviewer.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock private CheckstyleTool checkstyleTool;
    @Mock private PmdTool pmdTool;
    @Mock private DiffParser diffParser;

    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        ReviewerProperties reviewerProps = new ReviewerProperties(5, System.getProperty("java.io.tmpdir"));
        executor = new ToolExecutor(checkstyleTool, pmdTool, diffParser, new ObjectMapper(), reviewerProps);
    }

    @Test
    void executeCheckstyleReturnsFindingsJson() {
        String diff = "diff content";
        ParsedDiff parsed = new ParsedDiff(
                Map.of("Foo.java", "class Foo {}"),
                Map.of("Foo.java", Set.of(1)));
        when(diffParser.parse(diff)).thenReturn(parsed);
        // Return a finding whose absolute path is under the actual temp dir passed to analyse()
        when(checkstyleTool.analyse(any(Path.class))).thenAnswer(inv -> {
            Path dir = inv.getArgument(0);
            return List.of(new RawFinding(dir.resolve("Foo.java").toAbsolutePath().toString(),
                    "checkstyle", "MethodLength", "Too long", 1));
        });

        JsonValue input = JsonValue.from(Map.of("diff", diff));
        String result = executor.execute("run_checkstyle", input);

        assertThat(result).contains("checkstyle");
        assertThat(result).contains("MethodLength");
        assertThat(result).doesNotContain("\"error\"");
    }

    @Test
    void filtersToChangedLinesOnly() {
        String diff = "diff";
        ParsedDiff parsed = new ParsedDiff(
                Map.of("Foo.java", "class Foo {}"),
                Map.of("Foo.java", Set.of(5)));  // only line 5 is changed
        when(diffParser.parse(diff)).thenReturn(parsed);
        when(checkstyleTool.analyse(any(Path.class))).thenAnswer(inv -> {
            Path dir = inv.getArgument(0);
            String path = dir.resolve("Foo.java").toAbsolutePath().toString();
            return List.of(
                    new RawFinding(path, "checkstyle", "Rule", "msg", 3),  // not changed
                    new RawFinding(path, "checkstyle", "Rule", "msg", 5)); // changed
        });

        JsonValue input = JsonValue.from(Map.of("diff", diff));
        String result = executor.execute("run_checkstyle", input);

        // line 3 is filtered; line 5 survives — exactly one finding in the result
        assertThat(result).startsWith("[");
        assertThat(result).containsOnlyOnce("\"line\":5");
        assertThat(result).doesNotContain("\"line\":3");
    }

    @Test
    void returnsErrorJsonForUnknownTool() {
        when(diffParser.parse(any())).thenReturn(
                new ParsedDiff(Map.of(), Map.of()));
        JsonValue input = JsonValue.from(Map.of("diff", "x"));
        String result = executor.execute("unknown_tool", input);
        assertThat(result).contains("\"error\"");
    }
}

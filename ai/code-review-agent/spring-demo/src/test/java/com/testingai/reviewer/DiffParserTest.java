package com.testingai.reviewer;

import com.testingai.reviewer.model.ParsedDiff;
import com.testingai.reviewer.service.DiffParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DiffParserTest {

    private final DiffParser parser = new DiffParser();

    private static final String DIFF = """
            diff --git a/src/main/java/com/example/Foo.java b/src/main/java/com/example/Foo.java
            index abc1234..def5678 100644
            --- a/src/main/java/com/example/Foo.java
            +++ b/src/main/java/com/example/Foo.java
            @@ -1,4 +1,6 @@
             package com.example;
            \s
            +import java.util.List;
            +
             public class Foo {
                 public void bar() {
            """;

    @Test
    void parsesChangedLines() {
        ParsedDiff result = parser.parse(DIFF);

        assertThat(result.changedLines()).containsKey("src/main/java/com/example/Foo.java");
        assertThat(result.changedLines().get("src/main/java/com/example/Foo.java"))
                .containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void parsesFileContents() {
        ParsedDiff result = parser.parse(DIFF);

        String content = result.fileContents().get("src/main/java/com/example/Foo.java");
        assertThat(content).contains("package com.example;");
        assertThat(content).contains("import java.util.List;");
        assertThat(content).contains("public class Foo {");
    }

    @Test
    void skipsNonJavaFiles() {
        String diff = """
                diff --git a/README.md b/README.md
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,2 @@
                 # Title
                +New line
                """;

        ParsedDiff result = parser.parse(diff);

        assertThat(result.fileContents()).isEmpty();
        assertThat(result.changedLines()).isEmpty();
    }

    @Test
    void preservesLineNumbersAcrossHunkGap() {
        String diff = """
                diff --git a/src/Foo.java b/src/Foo.java
                index abc..def 100644
                --- a/src/Foo.java
                +++ b/src/Foo.java
                @@ -1,3 +1,4 @@
                 line1
                +line2
                 line3
                 line4
                @@ -10,3 +10,4 @@
                 line10
                +line11
                 line12
                 line13
                """;

        ParsedDiff result = parser.parse(diff);

        // changedLines should be {2, 11}
        Set<Integer> changed = result.changedLines().get("src/Foo.java");
        assertThat(changed).containsExactlyInAnyOrder(2, 11);

        // the content buffer should have at least 11 lines so physical line 11 == logical line 11
        String content = result.fileContents().get("src/Foo.java");
        String[] lines = content.split("\n", -1);
        assertThat(lines.length).isGreaterThanOrEqualTo(11);
        // physical line 2 (0-indexed: 1) is the added line
        assertThat(lines[1]).isEqualTo("line2");
        // physical line 11 (0-indexed: 10) is the second added line
        assertThat(lines[10]).isEqualTo("line11");
    }

    @Test
    void handlesMultipleFiles() {
        String diff = """
                diff --git a/Foo.java b/Foo.java
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,1 +1,2 @@
                 class Foo {}
                +// added
                diff --git a/Bar.java b/Bar.java
                --- a/Bar.java
                +++ b/Bar.java
                @@ -1,1 +1,2 @@
                 class Bar {}
                +// added
                """;

        ParsedDiff result = parser.parse(diff);

        assertThat(result.fileContents()).containsKeys("Foo.java", "Bar.java");
        assertThat(result.changedLines().get("Foo.java")).contains(2);
        assertThat(result.changedLines().get("Bar.java")).contains(2);
    }
}

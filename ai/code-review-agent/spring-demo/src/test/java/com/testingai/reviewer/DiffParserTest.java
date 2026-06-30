package com.testingai.reviewer;

import com.testingai.reviewer.model.ParsedDiff;
import com.testingai.reviewer.service.DiffParser;
import org.junit.jupiter.api.Test;

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

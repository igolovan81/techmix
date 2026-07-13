package com.testingai.sdlc.ticket;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdfTextExtractorTest {

    @Test
    void extractText_shouldFlattenSingleParagraph() {
        Object adf = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(
                                Map.of("type", "text", "text", "Checkout fails intermittently.")))));

        assertThat(AdfTextExtractor.extractText(adf)).contains("Checkout fails intermittently.");
    }

    @Test
    void extractText_shouldJoinMultipleParagraphs() {
        Object adf = Map.of(
                "type", "doc",
                "content", List.of(
                        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "First."))),
                        Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "Second.")))));

        String result = AdfTextExtractor.extractText(adf);

        assertThat(result).contains("First.").contains("Second.");
    }

    @Test
    void extractText_shouldReturnEmptyStringForNull() {
        assertThat(AdfTextExtractor.extractText(null)).isEmpty();
    }

    @Test
    void extractText_shouldReturnPlainStringUnchanged() {
        assertThat(AdfTextExtractor.extractText("already plain text")).isEqualTo("already plain text");
    }
}

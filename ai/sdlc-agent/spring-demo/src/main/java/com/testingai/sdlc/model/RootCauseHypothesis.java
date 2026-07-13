package com.testingai.sdlc.model;

import java.util.List;

public record RootCauseHypothesis(String summary, List<String> evidence, String confidence,
        List<String> suspectedFiles) {
}

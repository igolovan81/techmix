package com.testingai.sdlc.model;

import java.util.List;

public record FixResponse(RootCauseHypothesis rootCause, String summary, String patch, String branchName,
        String commitSha, int iterations, List<StepRecord> steps, boolean truncated) {
}

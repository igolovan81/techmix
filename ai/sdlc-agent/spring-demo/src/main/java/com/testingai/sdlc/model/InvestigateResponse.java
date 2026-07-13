package com.testingai.sdlc.model;

import java.util.List;

public record InvestigateResponse(RootCauseHypothesis rootCause, int iterations, List<StepRecord> steps,
        boolean truncated) {
}

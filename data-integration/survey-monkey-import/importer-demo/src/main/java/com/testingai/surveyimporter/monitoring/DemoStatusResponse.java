package com.testingai.surveyimporter.monitoring;

import java.util.List;

public record DemoStatusResponse(List<SurveyStatus> surveys, int queueDepth, long dlqSize, String circuitBreakerState) {
}

package com.testingai.surveyimporter.controller;

import java.time.Instant;

public record SurveyResponseView(String responseId, Instant dateModified, String payload) {
}

package com.testingai.mcpexplorer.client.model;

import java.util.List;

public record AgentResponse(String answer, List<StepRecord> steps, int iterations, boolean truncated) {
}

package com.testingai.mcpexplorer.client.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(@NotBlank String goal) {
}

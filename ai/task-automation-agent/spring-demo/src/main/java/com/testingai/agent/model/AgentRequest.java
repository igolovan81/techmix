package com.testingai.agent.model;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(@NotBlank String goal) {}

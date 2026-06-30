package com.testingai.reviewer.model;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(@NotBlank String diff) {}

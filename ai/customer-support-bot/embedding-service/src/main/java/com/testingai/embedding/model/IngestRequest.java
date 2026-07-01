package com.testingai.embedding.model;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(@NotBlank String title, @NotBlank String content) {}

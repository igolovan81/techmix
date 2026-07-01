package com.testingai.chat.model;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest(@NotBlank String text) {}

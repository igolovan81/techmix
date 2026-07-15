package com.testingai.sdlc.model;

import jakarta.validation.constraints.NotBlank;

public record FixRequest(@NotBlank String ticketId) {
}

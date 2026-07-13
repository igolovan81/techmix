package com.testingai.sdlc.ticket;

import java.time.Instant;

public record Ticket(String id, String title, String description, String severity, String service,
        Instant reportedAt) {
}

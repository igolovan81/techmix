package com.testingai.sdlc.log;

import java.time.Instant;

public record LogEntry(Instant timestamp, String service, String level, String message, String correlationId) {
}

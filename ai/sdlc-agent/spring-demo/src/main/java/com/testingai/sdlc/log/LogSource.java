package com.testingai.sdlc.log;

import java.time.Instant;
import java.util.List;

public interface LogSource {

    List<LogEntry> query(String service, Instant from, Instant to, String keyword, String correlationId);
}

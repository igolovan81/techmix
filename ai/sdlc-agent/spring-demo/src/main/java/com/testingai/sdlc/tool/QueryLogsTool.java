package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testingai.sdlc.log.LogEntry;
import com.testingai.sdlc.log.LogSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class QueryLogsTool {

    private final LogSource logSource;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public QueryLogsTool(LogSource logSource) {
        this.logSource = logSource;
    }

    public String query(String service, String from, String to, String keyword, String correlationId) {
        try {
            Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(Duration.ofDays(1));
            Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
            List<LogEntry> entries = logSource.query(service, fromInstant, toInstant, keyword, correlationId);
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    public Tool definition() {
        return Tool.builder().name("query_logs")
                .description("Search production logs for a given service within a time window, optionally "
                        + "filtered by keyword or correlation ID. Call this multiple times to narrow down — "
                        + "e.g. a broad keyword search first, then a follow-up scoped to a correlationId found "
                        + "in a promising result.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("service", JsonValue.from(Map.of("type", "string",
                                        "description", "The service name to search logs for, e.g. checkout-service")))
                                .putAdditionalProperty("from", JsonValue.from(Map.of("type", "string", "description",
                                        "ISO-8601 start of the time window. Optional.")))
                                .putAdditionalProperty("to", JsonValue.from(Map.of("type", "string", "description",
                                        "ISO-8601 end of the time window. Optional.")))
                                .putAdditionalProperty("keyword", JsonValue.from(Map.of("type", "string",
                                        "description", "Free-text keyword, e.g. an exception class name. Optional.")))
                                .putAdditionalProperty("correlationId", JsonValue.from(Map.of("type", "string",
                                        "description", "A specific correlation/trace ID to fetch related entries for. Optional.")))
                                .build())
                        .required(List.of("service")).putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }
}

package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolExecutor {

    private final QueryLogsTool queryLogsTool;

    public ToolExecutor(QueryLogsTool queryLogsTool) {
        this.queryLogsTool = queryLogsTool;
    }

    public String execute(String toolName, JsonValue input) {
        try {
            Map<String, Object> fields = input.convert(new TypeReference<Map<String, Object>>() {
            });
            if (fields == null) {
                return "{\"error\": \"Tool input must be a JSON object\"}";
            }
            return switch (toolName) {
                case "query_logs" -> executeQueryLogs(fields);
                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\": \"ToolExecutor error: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String executeQueryLogs(Map<String, Object> fields) {
        Object service = fields.get("service");
        if (service == null) {
            return "{\"error\": \"query_logs: missing required field 'service'\"}";
        }
        String from = stringOrNull(fields.get("from"));
        String to = stringOrNull(fields.get("to"));
        String keyword = stringOrNull(fields.get("keyword"));
        String correlationId = stringOrNull(fields.get("correlationId"));
        return queryLogsTool.query(service.toString(), from, to, keyword, correlationId);
    }

    private String stringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }
}

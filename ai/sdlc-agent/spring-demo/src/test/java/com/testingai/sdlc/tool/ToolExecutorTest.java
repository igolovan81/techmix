package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock
    private QueryLogsTool queryLogsTool;

    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        toolExecutor = new ToolExecutor(queryLogsTool);
    }

    @Test
    void execute_shouldDispatchQueryLogsWithAllFields() {
        when(queryLogsTool.query("checkout-service", "2026-07-10T00:00:00Z", null, "NullPointerException", null))
                .thenReturn("[]");

        String result = toolExecutor.execute("query_logs", JsonValue.from(Map.of("service", "checkout-service",
                "from", "2026-07-10T00:00:00Z", "keyword", "NullPointerException")));

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void execute_shouldReturnErrorWhenServiceFieldMissing() {
        String result = toolExecutor.execute("query_logs", JsonValue.from(Map.of("keyword", "test")));

        assertThat(result).contains("error").contains("service");
    }

    @Test
    void execute_shouldReturnErrorForUnknownTool() {
        String result = toolExecutor.execute("unknown_tool", JsonValue.from(Map.of()));

        assertThat(result).contains("error").contains("unknown_tool");
    }
}

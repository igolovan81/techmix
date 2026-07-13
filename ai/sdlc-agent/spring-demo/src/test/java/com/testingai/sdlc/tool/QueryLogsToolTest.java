package com.testingai.sdlc.tool;

import com.testingai.sdlc.log.LogEntry;
import com.testingai.sdlc.log.LogSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryLogsToolTest {

    @Mock
    private LogSource logSource;

    private QueryLogsTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryLogsTool(logSource);
    }

    @Test
    void query_shouldSerializeLogEntriesAsJsonArray() {
        when(logSource.query(eq("checkout-service"), any(), any(), eq("NullPointerException"), eq(null)))
                .thenReturn(List.of(new LogEntry(Instant.parse("2026-07-10T14:22:01Z"), "checkout-service", "ERROR",
                        "NullPointerException: discountCode is null", "corr-abc")));

        String result = tool.query("checkout-service", "2026-07-10T00:00:00Z", "2026-07-11T00:00:00Z",
                "NullPointerException", null);

        assertThat(result).contains("checkout-service").contains("ERROR").contains("corr-abc");
    }

    @Test
    void query_shouldDefaultTimeWindowWhenFromToOmitted() {
        when(logSource.query(eq("checkout-service"), any(), any(), eq(null), eq(null))).thenReturn(List.of());

        String result = tool.query("checkout-service", null, null, null, null);

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void definition_hasCorrectNameAndRequiredField() {
        assertThat(tool.definition().name()).isEqualTo("query_logs");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}

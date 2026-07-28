package com.testingai.mcpexplorer.client.integration;

import com.testingai.mcpexplorer.client.model.AgentResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpAgentIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate http = new TestRestTemplate();

    @Test
    void run_withRealMcpServerAndClaude_returnsNonEmptyAnswer() {
        var request = Map.of("goal", "Using the available tools, name one module under the message-brokers category.");

        ResponseEntity<AgentResponse> response = http.postForEntity(
                "http://localhost:" + port + "/api/mcp-agent/run",
                request,
                AgentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isNotBlank();
        assertThat(response.getBody().steps()).isNotEmpty();
    }
}

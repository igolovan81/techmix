package com.testingai.agent.integration;

import com.testingai.agent.model.AgentResponse;
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
class AgentIntegrationTest {

    @LocalServerPort private int port;

    private final TestRestTemplate http = new TestRestTemplate();

    @Test
    void run_withRealApis_returnsNonEmptyAnswer() {
        var request = Map.of("goal", "In one sentence, what is the capital of France?");

        ResponseEntity<AgentResponse> response = http.postForEntity(
                "http://localhost:" + port + "/api/agent/run",
                request,
                AgentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isNotBlank();
    }
}

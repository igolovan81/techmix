package com.testingai.sdlc.integration;

import com.testingai.sdlc.model.InvestigateResponse;
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
class SdlcAgentIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate http = new TestRestTemplate();

    @Test
    void investigate_withRealApis_returnsNonBlankRootCause() {
        var request = Map.of("ticketId", "DEMO-101");

        ResponseEntity<InvestigateResponse> response = http
                .postForEntity("http://localhost:" + port + "/api/sdlc/investigate", request,
                        InvestigateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rootCause().summary()).isNotBlank();
    }
}

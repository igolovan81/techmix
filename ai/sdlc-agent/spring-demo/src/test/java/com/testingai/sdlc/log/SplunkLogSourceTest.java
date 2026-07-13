package com.testingai.sdlc.log;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.sdlc.config.SplunkProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class SplunkLogSourceTest {

    private WireMockServer wireMock;
    private SplunkLogSource source;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        SplunkProperties props = new SplunkProperties("http://localhost:" + wireMock.port(), "token", 5, false);
        RestClient restClient = RestClient.builder().baseUrl(props.baseUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(props.apiToken())).build();
        source = new SplunkLogSource(restClient, props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void query_shouldCreateJobPollAndReturnResults() {
        stubFor(post(urlPathEqualTo("/services/search/jobs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\": \"12345\"}")));
        stubFor(get(urlPathEqualTo("/services/search/jobs/12345")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"entry\": [{\"content\": {\"dispatchState\": \"DONE\"}}]}")));
        stubFor(get(urlPathEqualTo("/services/search/jobs/12345/results")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "results": [
                            {
                              "_time": "2026-07-10T14:22:01Z",
                              "_raw": "{\\"service\\": \\"checkout-service\\", \\"level\\": \\"ERROR\\", \\"message\\": \\"NullPointerException: discountCode is null\\", \\"correlationId\\": \\"corr-abc\\"}"
                            }
                          ]
                        }
                        """)));

        List<LogEntry> entries = source.query("checkout-service", Instant.parse("2026-07-10T00:00:00Z"),
                Instant.parse("2026-07-11T00:00:00Z"), "NullPointerException", null);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().service()).isEqualTo("checkout-service");
        assertThat(entries.getFirst().level()).isEqualTo("ERROR");
        assertThat(entries.getFirst().message()).contains("NullPointerException");
        assertThat(entries.getFirst().correlationId()).isEqualTo("corr-abc");
    }

    @Test
    void query_shouldReturnEmptyListWhenJobNeverCompletes() {
        stubFor(post(urlPathEqualTo("/services/search/jobs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\": \"stuck-job\"}")));
        stubFor(get(urlPathEqualTo("/services/search/jobs/stuck-job")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"entry\": [{\"content\": {\"dispatchState\": \"RUNNING\"}}]}")));

        SplunkProperties fastTimeoutProps = new SplunkProperties("http://localhost:" + wireMock.port(), "token", 1,
                false);
        RestClient restClient = RestClient.builder().baseUrl(fastTimeoutProps.baseUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(fastTimeoutProps.apiToken())).build();
        SplunkLogSource fastTimeoutSource = new SplunkLogSource(restClient, fastTimeoutProps);

        List<LogEntry> entries = fastTimeoutSource.query("checkout-service", Instant.now(), Instant.now(), null,
                null);

        assertThat(entries).isEmpty();
    }
}

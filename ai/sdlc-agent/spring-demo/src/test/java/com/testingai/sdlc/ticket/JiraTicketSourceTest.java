package com.testingai.sdlc.ticket;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.sdlc.config.JiraProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraTicketSourceTest {

    private WireMockServer wireMock;
    private JiraTicketSource source;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        JiraProperties props = new JiraProperties("http://localhost:" + wireMock.port(), "user@example.com",
                "token", "customfield_10050");
        RestClient restClient = RestClient.builder().baseUrl(props.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(props.email(), props.apiToken())).build();
        source = new JiraTicketSource(restClient, props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetch_shouldMapJiraIssueToTicket() {
        stubFor(get(urlEqualTo("/rest/api/3/issue/DEMO-101")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "key": "DEMO-101",
                          "fields": {
                            "summary": "Checkout fails with 500 error for some orders",
                            "description": {
                              "type": "doc",
                              "content": [
                                {"type": "paragraph", "content": [
                                  {"type": "text", "text": "Intermittent failures reported."}
                                ]}
                              ]
                            },
                            "priority": {"name": "High"},
                            "created": "2026-07-10T14:00:00.000+0000",
                            "customfield_10050": "checkout-service"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("DEMO-101");

        assertThat(ticket.id()).isEqualTo("DEMO-101");
        assertThat(ticket.title()).isEqualTo("Checkout fails with 500 error for some orders");
        assertThat(ticket.description()).contains("Intermittent failures reported.");
        assertThat(ticket.severity()).isEqualTo("High");
        assertThat(ticket.service()).isEqualTo("checkout-service");
        assertThat(ticket.reportedAt()).isEqualTo(Instant.parse("2026-07-10T14:00:00Z"));
    }

    @Test
    void fetch_shouldDefaultServiceToUnknownWhenCustomFieldMissing() {
        stubFor(get(urlEqualTo("/rest/api/3/issue/DEMO-102")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "key": "DEMO-102",
                          "fields": {
                            "summary": "Some other bug",
                            "description": null,
                            "priority": {"name": "Low"},
                            "created": "2026-07-10T14:00:00.000+0000"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("DEMO-102");

        assertThat(ticket.service()).isEqualTo("unknown");
        assertThat(ticket.description()).isEmpty();
    }

    @Test
    void fetch_shouldThrowNotFoundWhenJiraReturns404() {
        stubFor(get(urlEqualTo("/rest/api/3/issue/MISSING-1")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> source.fetch("MISSING-1")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MISSING-1");
    }
}

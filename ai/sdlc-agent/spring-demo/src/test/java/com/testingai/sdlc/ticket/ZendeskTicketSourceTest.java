package com.testingai.sdlc.ticket;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.sdlc.config.ZendeskProperties;
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

class ZendeskTicketSourceTest {

    private WireMockServer wireMock;
    private ZendeskTicketSource source;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        ZendeskProperties props = new ZendeskProperties("acme", "agent@example.com", "token", "");
        RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + wireMock.port())
                .defaultHeaders(headers -> headers.setBasicAuth(props.email() + "/token", props.apiToken())).build();
        source = new ZendeskTicketSource(restClient, props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetch_shouldMapZendeskTicketToTicket() {
        stubFor(get(urlEqualTo("/api/v2/tickets/1001.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "ticket": {
                            "id": 1001,
                            "subject": "Checkout fails with 500 error for some orders",
                            "description": "Intermittent failures reported.",
                            "priority": "high",
                            "tags": ["checkout-service", "bug"],
                            "created_at": "2026-07-10T14:00:00Z"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("1001");

        assertThat(ticket.id()).isEqualTo("1001");
        assertThat(ticket.title()).isEqualTo("Checkout fails with 500 error for some orders");
        assertThat(ticket.description()).isEqualTo("Intermittent failures reported.");
        assertThat(ticket.severity()).isEqualTo("high");
        assertThat(ticket.service()).isEqualTo("checkout-service");
        assertThat(ticket.reportedAt()).isEqualTo(Instant.parse("2026-07-10T14:00:00Z"));
    }

    @Test
    void fetch_shouldDefaultServiceToUnknownWhenNoTags() {
        stubFor(get(urlEqualTo("/api/v2/tickets/1002.json")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {
                          "ticket": {
                            "id": 1002,
                            "subject": "Some other bug",
                            "description": "N/A",
                            "priority": "low",
                            "tags": [],
                            "created_at": "2026-07-10T14:00:00Z"
                          }
                        }
                        """)));

        Ticket ticket = source.fetch("1002");

        assertThat(ticket.service()).isEqualTo("unknown");
    }

    @Test
    void fetch_shouldThrowNotFoundWhenZendeskReturns404() {
        stubFor(get(urlEqualTo("/api/v2/tickets/9999.json")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> source.fetch("9999")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("9999");
    }
}

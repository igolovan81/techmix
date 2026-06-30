package com.testingai.agent.tool;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class FetchPageToolTest {

    private WireMockServer wireMock;
    private FetchPageTool tool;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        tool = new FetchPageTool(HttpClient.newHttpClient(), 4000);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetch_stripsHtmlAndReturnsPlainText() {
        stubFor(get("/article")
                .willReturn(ok()
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Quantum Computing</h1><p>New breakthrough.</p></body></html>")));

        String result = tool.fetch("http://localhost:" + wireMock.port() + "/article");

        assertThat(result).contains("Quantum Computing");
        assertThat(result).contains("New breakthrough");
        assertThat(result).doesNotContain("<html>");
        assertThat(result).doesNotContain("<p>");
    }

    @Test
    void fetch_trimsOutputToMaxChars() {
        String longContent = "x".repeat(10_000);
        stubFor(get("/long")
                .willReturn(ok()
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><p>" + longContent + "</p></body></html>")));

        String result = tool.fetch("http://localhost:" + wireMock.port() + "/long");

        assertThat(result.length()).isLessThanOrEqualTo(4000);
    }

    @Test
    void fetch_returnsErrorJsonOnNon200() {
        stubFor(get("/missing").willReturn(notFound()));

        String result = tool.fetch("http://localhost:" + wireMock.port() + "/missing");

        assertThat(result).contains("error");
    }

    @Test
    void definition_hasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("fetch_page");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}

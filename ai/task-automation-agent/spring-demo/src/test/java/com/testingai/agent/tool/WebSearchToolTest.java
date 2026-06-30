package com.testingai.agent.tool;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.testingai.agent.config.TavilyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class WebSearchToolTest {

    private WireMockServer wireMock;
    private WebSearchTool tool;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor(wireMock.port());
        var props = new TavilyProperties("test-key", "http://localhost:" + wireMock.port());
        tool = new WebSearchTool(
                RestClient.builder().baseUrl(props.baseUrl()).build(),
                props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void search_returnsParsedResultsAsJsonArray() {
        stubFor(post("/search")
                .withRequestBody(matchingJsonPath("$.query", equalTo("AI news")))
                .withRequestBody(matchingJsonPath("$.max_results", equalTo("3")))
                .willReturn(ok()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "results": [
                                    {
                                      "title": "AI Advances 2025",
                                      "url": "https://example.com/ai",
                                      "content": "Latest AI breakthroughs..."
                                    }
                                  ]
                                }
                                """)));

        String result = tool.search("AI news", 3);

        assertThat(result).contains("AI Advances 2025");
        assertThat(result).contains("https://example.com/ai");
        assertThat(result).contains("Latest AI breakthroughs");
    }

    @Test
    void search_returnsErrorJsonOnServerError() {
        stubFor(post("/search").willReturn(serverError()));

        String result = tool.search("anything", 5);

        assertThat(result).contains("error");
    }

    @Test
    void definition_hasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("web_search");
        assertThat(tool.definition().description().orElse("")).isNotBlank();
    }
}

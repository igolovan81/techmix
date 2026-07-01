package com.testingai.chat.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.testingai.chat.model.SearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseClientTest {

  private WireMockServer wireMock;
  private KnowledgeBaseClient client;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
    RestClient restClient = RestClient.builder()
        .baseUrl("http://localhost:" + wireMock.port())
        .build();
    client = new KnowledgeBaseClient(restClient);
  }

  @AfterEach
  void tearDown() {
    wireMock.stop();
  }

  @Test
  void search_returnsChunksFromEmbeddingService() {
    wireMock.stubFor(get(urlPathEqualTo("/api/kb/search"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                [{"title":"Refund Policy","content":"30 days","score":0.92}]
                """)));

    List<SearchResult> results = client.search("refund", 3);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().title()).isEqualTo("Refund Policy");
    assertThat(results.getFirst().score()).isEqualTo(0.92);
  }

  @Test
  void search_returnsEmptyList_whenServiceUnavailable() {
    wireMock.stubFor(get(urlPathEqualTo("/api/kb/search"))
        .willReturn(aResponse().withStatus(503)));

    List<SearchResult> results = client.search("anything", 3);

    assertThat(results).isEmpty();
  }
}

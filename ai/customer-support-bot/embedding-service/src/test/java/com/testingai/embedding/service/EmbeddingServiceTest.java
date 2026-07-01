package com.testingai.embedding.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EmbeddingServiceTest {

  static WireMockServer wireMock =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    wireMock.start();
    registry.add("openai.api-key", () -> "test-key");
    registry.add("openai.base-url", () -> "http://localhost:" + wireMock.port());
    registry.add("spring.datasource.url", () -> "jdbc:h2:mem:embed-test;DB_CLOSE_DELAY=-1");
    registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
    registry.add("spring.datasource.username", () -> "sa");
    registry.add("spring.datasource.password", () -> "");
    registry.add("spring.liquibase.enabled", () -> "false");
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @BeforeEach
  void reset() {
    wireMock.resetAll();
  }

  @Autowired private EmbeddingService embeddingService;

  @Test
  void embed_callsOpenAiAndReturnsFloatArray() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"data": [{"embedding": [0.1, 0.2, 0.3]}]}
                        """)));

    float[] result = embeddingService.embed("test text");

    assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v1/embeddings"))
            .withHeader("Authorization", equalTo("Bearer test-key")));
  }

  @Test
  void embed_sendsModelAndInputInRequestBody() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"data": [{"embedding": [0.5]}]}
                        """)));

    embeddingService.embed("hello world");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/v1/embeddings"))
            .withRequestBody(matchingJsonPath("$.input", equalTo("hello world")))
            .withRequestBody(matchingJsonPath("$.model", equalTo("text-embedding-3-small"))));
  }
}

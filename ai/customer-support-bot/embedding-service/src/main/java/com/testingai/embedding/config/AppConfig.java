package com.testingai.embedding.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

  private final OpenAiProperties openAi;

  public AppConfig(OpenAiProperties openAi) {
    this.openAi = openAi;
  }

  @PostConstruct
  public void validateApiKey() {
    if (!StringUtils.hasText(openAi.apiKey())) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
    }
  }

  @Bean
  public RestClient openAiRestClient() {
    return RestClient.builder()
        .requestFactory(new SimpleClientHttpRequestFactory())
        .baseUrl(openAi.baseUrl())
        .defaultHeader("Authorization", "Bearer " + openAi.apiKey())
        .defaultHeader("Content-Type", "application/json")
        .build();
  }
}

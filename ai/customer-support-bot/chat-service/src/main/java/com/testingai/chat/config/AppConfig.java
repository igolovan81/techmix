package com.testingai.chat.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

  private final AnthropicProperties anthropic;
  private final ChatProperties chat;

  public AppConfig(AnthropicProperties anthropic, ChatProperties chat) {
    this.anthropic = anthropic;
    this.chat = chat;
  }

  @PostConstruct
  public void validateApiKey() {
    if (!StringUtils.hasText(anthropic.apiKey())) {
      throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
    }
  }

  @Bean
  public AnthropicClient anthropicClient() {
    return AnthropicOkHttpClient.builder().apiKey(anthropic.apiKey()).build();
  }

  @Bean
  public RestClient embeddingServiceClient() {
    return RestClient.builder().baseUrl(chat.embeddingServiceUrl()).build();
  }
}

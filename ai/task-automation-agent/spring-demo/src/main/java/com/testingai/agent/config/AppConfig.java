package com.testingai.agent.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class AppConfig {

    private final AnthropicProperties anthropic;
    private final TavilyProperties tavily;

    public AppConfig(AnthropicProperties anthropic, TavilyProperties tavily) {
        this.anthropic = anthropic;
        this.tavily = tavily;
    }

    @PostConstruct
    public void validateApiKeys() {
        if (!StringUtils.hasText(anthropic.apiKey())) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }
        if (!StringUtils.hasText(tavily.apiKey())) {
            throw new IllegalStateException("TAVILY_API_KEY environment variable is not set");
        }
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropic.apiKey())
                .build();
    }

    @Bean
    public RestClient tavilyRestClient() {
        return RestClient.builder()
                .baseUrl(tavily.baseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }
}

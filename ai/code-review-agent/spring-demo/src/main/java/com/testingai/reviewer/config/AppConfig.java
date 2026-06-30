package com.testingai.reviewer.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private final AnthropicProperties anthropicProps;
    private final GitHubProperties githubProps;

    public AppConfig(AnthropicProperties anthropicProps, GitHubProperties githubProps) {
        this.anthropicProps = anthropicProps;
        this.githubProps = githubProps;
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropicProps.apiKey())
                .build();
    }

    @Bean
    public RestClient gitHubRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + githubProps.token())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @PostConstruct
    public void validateConfig() {
        if (anthropicProps.apiKey() == null || anthropicProps.apiKey().isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }
        if (githubProps.token() == null || githubProps.token().isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN is not set");
        }
    }
}

package com.testingai.embedding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(String apiKey, String baseUrl, String model) {}

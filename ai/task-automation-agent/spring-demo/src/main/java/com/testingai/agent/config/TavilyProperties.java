package com.testingai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tavily")
public record TavilyProperties(String apiKey, String baseUrl) {}

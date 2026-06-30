package com.testingai.reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("github")
public record GitHubProperties(String token, String webhookSecret) {}

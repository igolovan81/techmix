package com.testingai.chat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
    int maxTurns,
    List<String> escalationKeywords,
    String resolutionPhrase,
    String embeddingServiceUrl) {}

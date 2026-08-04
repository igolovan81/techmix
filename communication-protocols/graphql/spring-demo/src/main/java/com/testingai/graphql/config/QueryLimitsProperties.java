package com.testingai.graphql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.graphql")
public record QueryLimitsProperties(int maxQueryDepth, int maxQueryComplexity) {
}

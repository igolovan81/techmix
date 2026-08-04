package com.testingai.graphql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean enabled, int userCount, int categoryCount, int productCount,
		int minReviewsPerProduct, int maxReviewsPerProduct, int orderCount) {
}

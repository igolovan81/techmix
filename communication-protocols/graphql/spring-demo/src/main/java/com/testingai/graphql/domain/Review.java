package com.testingai.graphql.domain;

public record Review(String id, String productId, Long authorId, int rating, String comment) {
}

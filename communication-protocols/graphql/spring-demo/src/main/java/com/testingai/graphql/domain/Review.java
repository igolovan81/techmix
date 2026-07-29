package com.testingai.graphql.domain;

public record Review(String id, String productId, String author, int rating, String comment) {
}

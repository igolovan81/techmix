package com.testingai.graphql.domain;

public record AddReviewInput(String productId, String author, int rating, String comment) {
}

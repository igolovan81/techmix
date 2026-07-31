package com.testingai.graphql.domain;

public record AddReviewInput(String productId, int rating, String comment) {
}

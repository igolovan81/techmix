package com.testingai.graphql.domain;

public record ProductFilter(String nameContains, Integer minPriceCents, Integer maxPriceCents) {
}

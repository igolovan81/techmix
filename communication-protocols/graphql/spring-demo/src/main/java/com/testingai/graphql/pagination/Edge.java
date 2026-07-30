package com.testingai.graphql.pagination;

public record Edge<T>(T node, String cursor) {
}

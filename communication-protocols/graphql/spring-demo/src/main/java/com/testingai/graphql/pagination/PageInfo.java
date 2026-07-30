package com.testingai.graphql.pagination;

public record PageInfo(boolean hasNextPage, String endCursor) {
}

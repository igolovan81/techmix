package com.testingai.graphql.pagination;

import java.util.List;

public record Connection<T>(List<Edge<T>> edges, PageInfo pageInfo, int totalCount) {
}

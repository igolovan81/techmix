package com.testingai.graphql.controller;

import com.testingai.graphql.config.QueryLimitsProperties;
import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.ProductRepository;
import graphql.introspection.IntrospectionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueryLimitsTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private QueryLimitsProperties queryLimitsProperties;

	private HttpGraphQlTester graphQlTester;

	@BeforeEach
	void setUpTester() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();
		graphQlTester = HttpGraphQlTester.create(webTestClient);
	}

	private Long saveProduct(String name) {
		ProductEntity entity = new ProductEntity();
		entity.setName(name);
		entity.setPriceCents(1000);
		entity.setStockQty(10);
		return productRepository.save(entity).getId();
	}

	private Long saveCategory(String name) {
		CategoryEntity entity = new CategoryEntity();
		entity.setName(name);
		return categoryRepository.save(entity).getId();
	}

	@Test
	void regression_representativeLegitimateQueriesStillSucceed() {
		String tag = "tag" + System.nanoTime();
		saveProduct(tag + "-widget");
		Long categoryId = saveCategory(tag + "-category");

		graphQlTester.document("""
				query {
				  products(filter: { nameContains: "%s" }, first: 10) {
				    totalCount
				    edges { cursor node { id name priceCents stockQty categories { id name } } }
				    pageInfo { hasNextPage endCursor }
				  }
				  category(id: "%s") {
				    id name
				    children(first: 10) { edges { node { id name } } }
				    products(first: 10) { edges { node { id name } } }
				  }
				}
				""".formatted(tag, categoryId)).execute().path("products.totalCount").entity(Integer.class).isEqualTo(1)
				.path("category.id").entity(String.class).isEqualTo(categoryId.toString());
	}

	@Test
	void regression_introspectionQueryStillSucceeds() {
		graphQlTester.document(IntrospectionQuery.INTROSPECTION_QUERY).execute().path("__schema.queryType.name")
				.entity(String.class).isEqualTo("Query");
	}

	@Test
	void depthLimit_rejectsQueryWalkingProductReviewUserOrderCycleTwice() {
		List<ResponseError> errors = new ArrayList<>();
		graphQlTester.document(cyclicDepthQuery(2)).execute().errors().satisfy(errors::addAll);

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(errors.get(0).getMessage()).contains("exceeds maximum allowed depth");
	}

	@Test
	void depthLimit_boundaryIsExactlyAtConfiguredMaxDepth() {
		Long categoryId = saveCategory("tag" + System.nanoTime() + "-category");
		int maxDepth = queryLimitsProperties.maxQueryDepth();

		// Query depth is a structural property of the query document, independent of real data — this succeeds
		// or fails purely from the query shape, even against a category with no real parent chain that deep.
		// graphql-java's own depth count for a given nesting level includes a fixed offset (the outer
		// `category(id)` field, the `query { }` wrapper, etc.) that's an internal detail rather than something
		// to hard-code here — so calibrate it from a deliberately oversized probe query's own rejection message,
		// then derive the nesting count that lands exactly on maxDepth.
		int probeNestingLevels = maxDepth + 10;
		List<ResponseError> probeErrors = new ArrayList<>();
		graphQlTester.document(categoryParentChainQuery(categoryId, probeNestingLevels)).execute().errors()
				.satisfy(probeErrors::addAll);
		assertThat(probeErrors).hasSize(1);
		int measuredDepth = extractMeasuredDepth(probeErrors.get(0).getMessage());
		int offset = measuredDepth - probeNestingLevels;

		int atLimitNestingLevels = maxDepth - offset;
		graphQlTester.document(categoryParentChainQuery(categoryId, atLimitNestingLevels)).execute().path("category.id")
				.entity(String.class).isEqualTo(categoryId.toString());

		List<ResponseError> overLimitErrors = new ArrayList<>();
		graphQlTester.document(categoryParentChainQuery(categoryId, atLimitNestingLevels + 1)).execute().errors()
				.satisfy(overLimitErrors::addAll);
		assertThat(overLimitErrors).hasSize(1);
		assertThat(overLimitErrors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
	}

	private String cyclicDepthQuery(int cycles) {
		String openCycle = "reviews(first: 1) { edges { node { author { "
				+ "orders(first: 1) { edges { node { items { product { ";
		String closeCycle = " } } } } } } } } }";

		StringBuilder query = new StringBuilder();
		query.append("query { products(first: 1) { edges { node { ");
		query.append(openCycle.repeat(cycles));
		query.append("id");
		query.append(closeCycle.repeat(cycles));
		query.append(" } } } }");
		return query.toString();
	}

	private String categoryParentChainQuery(Long categoryId, int nestingLevels) {
		StringBuilder query = new StringBuilder("query { category(id: \"").append(categoryId).append("\") { id ");
		query.append("parent { ".repeat(nestingLevels));
		query.append("id");
		query.append(" }".repeat(nestingLevels));
		query.append(" } }");
		return query.toString();
	}

	private int extractMeasuredDepth(String message) {
		Matcher matcher = Pattern.compile("Query depth (\\d+) exceeds maximum allowed depth").matcher(message);
		assertThat(matcher.find()).as("unexpected rejection message: " + message).isTrue();
		return Integer.parseInt(matcher.group(1));
	}

	@Test
	void complexityLimit_rejectsExtremelyWideNestedConnectionQuery() {
		String query = """
				query {
				  products(first: 1000) {
				    edges { node {
				      reviews(first: 1000) { edges { node { id } } }
				    } }
				  }
				}
				""";

		List<ResponseError> errors = new ArrayList<>();
		graphQlTester.document(query).execute().errors().satisfy(errors::addAll);

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(errors.get(0).getMessage()).contains("exceeds maximum allowed complexity");
	}
}

package com.testingai.graphql.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoIntegrationTest {

	@LocalServerPort
	private int port;

	private HttpGraphQlTester graphQlTester;

	@BeforeEach
	void setUpTester() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();
		graphQlTester = HttpGraphQlTester.create(webTestClient);
	}

	@Test
	void query_returnsAllProducts() {
		graphQlTester.document("""
				query {
				  products { id name }
				}
				""").execute().path("products").entityList(Object.class).hasSize(40);
	}

	@Test
	void query_returnsOneProduct_byId() {
		graphQlTester.document("""
				query {
				  product(id: "p1") { id name }
				}
				""").execute().path("product.name").entity(String.class).isEqualTo("Mini Widget");
	}

	@Test
	void query_partiallyFails_whenProductLookupSimulatesFailure() {
		// FailureSimulator's 5% failure is real (not mocked): the GraphQL execution runs on a Tomcat worker
		// thread, not the test thread, so Mockito's thread-confined mockStatic can't reach it here. Instead,
		// repeat until one of the ~5%-chance failures actually happens (same statistical approach as
		// FailureSimulatorTest) and assert on that response's partial-failure shape.
		String query = """
				query {
				  products { id }
				  product(id: "p1") { id name }
				}
				""";

		for (int attempt = 0; attempt < 200; attempt++) {
			List<ResponseError> errors = new ArrayList<>();
			GraphQlTester.Traversable afterErrors = graphQlTester.document(query).execute().errors()
					.satisfy(errors::addAll);

			if (!errors.isEmpty()) {
				assertThat(errors).hasSize(1);
				assertThat(errors.get(0).getMessage()).isEqualTo("Simulated 5% failure in product query");
				assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
				afterErrors.path("product").valueIsNull();
				afterErrors.path("products").entityList(Object.class).hasSize(40);
				return;
			}
		}

		fail("Expected at least one simulated failure across 200 attempts (5% failure rate)");
	}
}

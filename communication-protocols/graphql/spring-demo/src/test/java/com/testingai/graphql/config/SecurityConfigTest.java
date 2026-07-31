package com.testingai.graphql.config;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ProductRepository productRepository;

	private HttpGraphQlTester graphQlTester;

	@BeforeEach
	void setUpTester() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();
		graphQlTester = HttpGraphQlTester.create(webTestClient);

		// This class shares its Spring context (and thus its database) with other @SpringBootTest(RANDOM_PORT)
		// classes in this module, none of which roll back between tests — so unlike the original in-memory catalog
		// (always exactly 40 products), nothing here can assume products already exist. Seed enough to satisfy the
		// default page size below regardless of what other test classes have (or haven't) inserted yet.
		for (int i = 0; i < 10; i++) {
			ProductEntity product = new ProductEntity();
			product.setName("SecurityConfigTest Product " + i);
			product.setPriceCents(1000 + i);
			product.setStockQty(10);
			productRepository.save(product);
		}
	}

	@Test
	void basicAuth_withCorrectCredentials_authenticatesSuccessfully() {
		HttpGraphQlTester authenticated = graphQlTester.mutate()
				.header("Authorization", basicAuthHeader("user", "userPassword")).build();

		authenticated.document("""
				query {
				  products { edges { node { id } } }
				}
				""").execute().path("products.edges").entityList(Object.class).hasSize(10);
	}

	@Test
	void basicAuth_withWrongPassword_returnsUnauthorizedHttpStatus() {
		WebTestClient webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql")
				.build();

		webTestClient.post().header("Authorization", basicAuthHeader("user", "wrongPassword"))
				.contentType(MediaType.APPLICATION_JSON).bodyValue("{\"query\":\"{ products { id } }\"}").exchange()
				.expectStatus().isUnauthorized();
	}

	private static String basicAuthHeader(String username, String password) {
		String credentials = username + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}
}

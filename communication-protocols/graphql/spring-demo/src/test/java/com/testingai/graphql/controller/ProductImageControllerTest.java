package com.testingai.graphql.controller;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductImageControllerTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ProductRepository productRepository;

	private WebTestClient webTestClient;
	private Long productId;

	@BeforeEach
	void setUp() {
		webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

		ProductEntity product = new ProductEntity();
		product.setName("Image Test Product " + System.nanoTime());
		product.setPriceCents(1000);
		product.setStockQty(5);
		productId = productRepository.save(product).getId();
	}

	@Test
	void upload_thenDownload_asAdmin_roundTripsBytes() {
		byte[] bytes = {1, 2, 3, 4};

		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(bytes, "image/png"))).exchange().expectStatus()
				.isNoContent();

		webTestClient.get().uri("/api/products/{id}/image", productId).exchange().expectStatus().isOk().expectHeader()
				.contentType(MediaType.IMAGE_PNG).expectBody(byte[].class).isEqualTo(bytes);
	}

	@Test
	void upload_isRejected_whenAuthenticatedAsUser() {
		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("user", "userPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[]{1}, "image/png"))).exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void upload_isRejected_whenAnonymous() {
		webTestClient.post().uri("/api/products/{id}/image", productId)
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[]{1}, "image/png"))).exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void upload_isRejected_whenContentTypeIsNotAnImage() {
		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[]{1}, "text/plain"))).exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void upload_isRejected_whenProductDoesNotExist() {
		webTestClient.post().uri("/api/products/{id}/image", 999999999L)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(new byte[]{1}, "image/png"))).exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void upload_isRejected_whenFileExceedsSizeLimit() {
		byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];

		webTestClient.post().uri("/api/products/{id}/image", productId)
				.header("Authorization", basicAuthHeader("admin", "adminPassword"))
				.body(BodyInserters.fromMultipartData(multipartBody(tooLarge, "image/png"))).exchange().expectStatus()
				.isEqualTo(413);
	}

	@Test
	void download_returnsNotFound_whenNoImageUploaded() {
		webTestClient.get().uri("/api/products/{id}/image", productId).exchange().expectStatus().isNotFound();
	}

	private static MultiValueMap<String, HttpEntity<?>> multipartBody(byte[] bytes, String contentType) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("file", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return "image.bin";
			}
		}).contentType(MediaType.parseMediaType(contentType));
		return builder.build();
	}

	private static String basicAuthHeader(String username, String password) {
		String credentials = username + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}
}

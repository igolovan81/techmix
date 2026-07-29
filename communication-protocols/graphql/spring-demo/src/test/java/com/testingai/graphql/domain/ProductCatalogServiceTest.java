package com.testingai.graphql.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCatalogServiceTest {

	private final ProductCatalogService service = new ProductCatalogService();

	@Test
	void listProducts_returnsAllFortyProducts() {
		assertThat(service.listProducts()).hasSize(40);
	}

	@Test
	void findProduct_returnsProduct_whenKnown() {
		assertThat(service.findProduct("p1")).isPresent().get().extracting(Product::name).isEqualTo("Mini Widget");
	}

	@Test
	void findProduct_returnsEmpty_whenUnknown() {
		assertThat(service.findProduct("unknown")).isEmpty();
	}
}

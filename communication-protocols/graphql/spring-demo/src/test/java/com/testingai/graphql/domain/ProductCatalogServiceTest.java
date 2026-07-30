package com.testingai.graphql.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

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

	@Test
	void listProducts_withNullFilter_returnsFullCatalog() {
		assertThat(service.listProducts(null)).hasSize(40);
	}

	@Test
	void listProducts_filtersByNameContains_caseInsensitive() {
		List<Product> filtered = service.listProducts(new ProductFilter("widget", null, null));

		assertThat(filtered).isNotEmpty()
				.allSatisfy(product -> assertThat(product.name().toLowerCase()).contains("widget"));
	}

	@Test
	void listProducts_filtersByPriceRange() {
		List<Product> filtered = service.listProducts(new ProductFilter(null, 1000, 2000));

		assertThat(filtered).isNotEmpty()
				.allSatisfy(product -> assertThat(product.priceCents()).isBetween(1000L, 2000L));
	}

	@Test
	void listProducts_combinesNameAndPriceFilters() {
		// "widget" alone matches 4 products across all variants (Mini/Standard/Pro/Max Widget, priced
		// 636/2006/3376/4746
		// in this catalog's deterministic pricing formula); adding the price range narrows it to just "Standard
		// Widget".
		List<Product> nameOnly = service.listProducts(new ProductFilter("widget", null, null));
		List<Product> combined = service.listProducts(new ProductFilter("widget", 1000, 3000));

		assertThat(combined).isNotEmpty();
		assertThat(combined).allSatisfy(product -> {
			assertThat(product.name().toLowerCase()).contains("widget");
			assertThat(product.priceCents()).isBetween(1000L, 3000L);
		});
		assertThat(combined.size()).isLessThanOrEqualTo(nameOnly.size());
	}
}

package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductCatalogServiceTest {

	@Autowired
	private ProductRepository productRepository;

	private ProductCatalogService service;

	@BeforeEach
	void setUp() {
		service = new ProductCatalogService(productRepository);
		productRepository.save(newProduct("Mini Widget", 636, 10));
		productRepository.save(newProduct("Standard Widget", 2006, 10));
		productRepository.save(newProduct("Mini Gadget", 700, 10));
	}

	private static ProductEntity newProduct(String name, long priceCents, int stockQty) {
		ProductEntity entity = new ProductEntity();
		entity.setName(name);
		entity.setPriceCents(priceCents);
		entity.setStockQty(stockQty);
		return entity;
	}

	@Test
	void listProducts_returnsAllProducts_whenNoFilter() {
		Connection<Product> connection = service.listProducts(null, 50, null);

		assertThat(connection.totalCount()).isEqualTo(3);
		assertThat(connection.edges()).hasSize(3);
	}

	@Test
	void findProduct_returnsEmpty_whenIdIsNotNumeric() {
		assertThat(service.findProduct("not-a-number")).isEmpty();
	}

	@Test
	void findProduct_returnsEmpty_whenUnknown() {
		assertThat(service.findProduct("999999")).isEmpty();
	}

	@Test
	void listProducts_filtersByNameContains_caseInsensitive() {
		Connection<Product> connection = service.listProducts(new ProductFilter("mini", null, null), 50, null);

		assertThat(connection.edges()).extracting(edge -> edge.node().name())
				.allSatisfy(name -> assertThat(name.toLowerCase()).contains("mini"));
		assertThat(connection.totalCount()).isEqualTo(2);
	}

	@Test
	void listProducts_filtersByPriceRange() {
		Connection<Product> connection = service.listProducts(new ProductFilter(null, 1000, 3000), 50, null);

		assertThat(connection.edges()).extracting(edge -> edge.node().priceCents())
				.allSatisfy(priceCents -> assertThat(priceCents).isBetween(1000L, 3000L));
	}

	@Test
	void listProducts_pushesPaginationToTheDatabase_returningOnlyTheRequestedPage() {
		Connection<Product> firstPage = service.listProducts(null, 2, null);

		assertThat(firstPage.edges()).hasSize(2);
		assertThat(firstPage.pageInfo().hasNextPage()).isTrue();

		Connection<Product> secondPage = service.listProducts(null, 2, firstPage.pageInfo().endCursor());

		assertThat(secondPage.edges()).hasSize(1);
		assertThat(secondPage.pageInfo().hasNextPage()).isFalse();
	}

	@Test
	void findByIds_returnsMapKeyedByStringId() {
		ProductEntity saved = productRepository.save(newProduct("Gizmo", 400, 5));

		var byId = service.findByIds(List.of(saved.getId().toString()));

		assertThat(byId.get(saved.getId().toString()).name()).isEqualTo("Gizmo");
	}
}

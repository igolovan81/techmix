package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

	@InjectMocks
	private ProductSearchService productSearchService;

	@Mock
	private MongoTemplate mongoTemplate;

	@Test
	void searchByText_shouldReturnMatchingProducts() {
		List<Product> expected = List.of(new Product("p1", "Widget", 9.99, 100));
		when(mongoTemplate.find(any(), eq(Product.class))).thenReturn(expected);

		List<Product> result = productSearchService.searchByText("Widget");

		assertThat(result).isEqualTo(expected);
	}

	@Test
	void findByPriceRange_shouldReturnProductsWithinRange() {
		List<Product> expected = List.of(new Product("p1", "Widget", 9.99, 100));
		when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(expected);

		List<Product> result = productSearchService.findByPriceRange(5.0, 50.0);

		assertThat(result).isEqualTo(expected);
	}
}

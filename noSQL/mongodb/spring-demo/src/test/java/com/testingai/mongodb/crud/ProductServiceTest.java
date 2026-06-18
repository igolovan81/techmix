package com.testingai.mongodb.crud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@InjectMocks
	private ProductService productService;

	@Mock
	private MongoTemplate mongoTemplate;

	@Test
	void create_shouldSaveAndReturnProduct() {
		Product product = new Product(null, "Widget", 9.99, 100);
		when(mongoTemplate.save(product)).thenReturn(product);

		Product result = productService.create(product);

		assertThat(result).isEqualTo(product);
		verify(mongoTemplate).save(product);
	}

	@Test
	void findById_shouldReturnProduct() {
		Product product = new Product("abc123", "Widget", 9.99, 100);
		when(mongoTemplate.findById("abc123", Product.class)).thenReturn(product);

		Product result = productService.findById("abc123");

		assertThat(result).isEqualTo(product);
	}

	@Test
	void update_shouldSetIdAndSave() {
		Product update = new Product(null, "Widget v2", 12.99, 50);
		when(mongoTemplate.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Product result = productService.update("abc123", update);

		assertThat(result.getId()).isEqualTo("abc123");
		verify(mongoTemplate).save(update);
	}

	@Test
	void delete_shouldRemoveById() {
		productService.delete("abc123");

		verify(mongoTemplate).remove(any(Query.class), eq(Product.class));
	}
}

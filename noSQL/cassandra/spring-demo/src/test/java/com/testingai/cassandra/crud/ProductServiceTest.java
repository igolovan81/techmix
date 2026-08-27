package com.testingai.cassandra.crud;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Query;

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
	private CassandraTemplate cassandraTemplate;

	@Test
	void create_shouldGenerateIdAndSave() {
		Product product = new Product(null, "Widget", new BigDecimal("9.99"), 100);
		when(cassandraTemplate.insert(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Product result = productService.create(product);

		assertThat(result.getId()).isNotNull();
		verify(cassandraTemplate).insert(product);
	}

	@Test
	void findById_shouldReturnProduct() {
		UUID id = UUID.randomUUID();
		Product product = new Product(id, "Widget", new BigDecimal("9.99"), 100);
		when(cassandraTemplate.selectOneById(id, Product.class)).thenReturn(product);

		Product result = productService.findById(id);

		assertThat(result).isEqualTo(product);
	}

	@Test
	void update_shouldSetIdAndSave() {
		UUID id = UUID.randomUUID();
		Product update = new Product(null, "Widget v2", new BigDecimal("12.99"), 50);
		when(cassandraTemplate.update(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Product result = productService.update(id, update);

		assertThat(result.getId()).isEqualTo(id);
		verify(cassandraTemplate).update(update);
	}

	@Test
	void delete_shouldRemoveById() {
		UUID id = UUID.randomUUID();

		productService.delete(id);

		verify(cassandraTemplate).delete(any(Query.class), eq(Product.class));
	}
}

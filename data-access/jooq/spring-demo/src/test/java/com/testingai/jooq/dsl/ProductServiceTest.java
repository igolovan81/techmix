package com.testingai.jooq.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import({CategoryService.class, ProductService.class})
class ProductServiceTest {

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private ProductService productService;

	private Long categoryId;

	@BeforeEach
	void seedCategory() {
		categoryId = categoryService.create("Books").id();
	}

	@Test
	void createsReadsUpdatesAndDeletesAProduct() {
		ProductView created = productService.create(categoryId, "Refactoring", new BigDecimal("49.99"), 10);
		assertThat(created.id()).isNotNull();

		ProductView found = productService.findById(created.id());
		assertThat(found).isEqualTo(created);

		ProductView updated = productService.update(created.id(), categoryId, "Refactoring, 2nd Ed.",
				new BigDecimal("54.99"), 8);
		assertThat(productService.findById(created.id())).isEqualTo(updated);

		productService.delete(created.id());
		assertThatThrownBy(() -> productService.findById(created.id())).isInstanceOf(NoSuchElementException.class);
	}
}

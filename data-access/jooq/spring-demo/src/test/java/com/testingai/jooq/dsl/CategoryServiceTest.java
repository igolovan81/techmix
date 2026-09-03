package com.testingai.jooq.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jooq.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import(CategoryService.class)
class CategoryServiceTest {

	@Autowired
	private CategoryService categoryService;

	@Test
	void createsAndReadsBackACategory() {
		CategoryView created = categoryService.create("Electronics");

		assertThat(created.id()).isNotNull();
		assertThat(created.name()).isEqualTo("Electronics");

		CategoryView found = categoryService.findById(created.id());
		assertThat(found).isEqualTo(created);
	}
}

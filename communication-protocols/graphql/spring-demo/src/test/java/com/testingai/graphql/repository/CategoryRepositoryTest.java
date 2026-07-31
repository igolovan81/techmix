package com.testingai.graphql.repository;

import com.testingai.graphql.entity.CategoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTest {

	@Autowired
	private CategoryRepository categoryRepository;

	@Test
	void findByParentId_returnsChildren() {
		CategoryEntity root = new CategoryEntity();
		root.setName("Electronics");
		root = categoryRepository.save(root);

		CategoryEntity child = new CategoryEntity();
		child.setName("Audio");
		child.setParent(root);
		categoryRepository.save(child);

		List<CategoryEntity> children = categoryRepository.findByParentId(root.getId());

		assertThat(children).extracting(CategoryEntity::getName).containsExactly("Audio");
	}

	@Test
	void rootCategory_hasNullParent() {
		CategoryEntity root = new CategoryEntity();
		root.setName("Electronics");
		CategoryEntity saved = categoryRepository.save(root);

		assertThat(categoryRepository.findById(saved.getId())).get().extracting(CategoryEntity::getParent).isNull();
	}
}

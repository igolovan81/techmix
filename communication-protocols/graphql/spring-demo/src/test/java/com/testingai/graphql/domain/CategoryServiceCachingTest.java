package com.testingai.graphql.domain;

import com.testingai.graphql.config.CacheConfig;
import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.repository.CategoryRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CacheConfig.class, CategoryService.class})
class CategoryServiceCachingTest {

	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private CategoryService categoryService;

	private CategoryEntity root;
	private CategoryEntity child;

	@BeforeEach
	void setUp() {
		root = new CategoryEntity();
		root.setName("Electronics");
		root = categoryRepository.save(root);

		child = new CategoryEntity();
		child.setName("Audio");
		child.setParent(root);
		child = categoryRepository.save(child);
	}

	@Test
	void findCategory_hitsDatabaseOnlyOnce_forRepeatedCalls() {
		categoryService.findCategory(root.getId());
		int afterFirstCall = categoryService.getDbLoadCount();

		categoryService.findCategory(root.getId());

		assertThat(categoryService.getDbLoadCount()).isEqualTo(afterFirstCall);
	}

	@Test
	void findByIds_hitsCache_warmedByFindCategory() {
		categoryService.findCategory(root.getId());
		int afterFirstCall = categoryService.getDbLoadCount();

		Map<Long, Category> byId = categoryService.findByIds(List.of(root.getId()));

		assertThat(byId.get(root.getId()).name()).isEqualTo("Electronics");
		assertThat(categoryService.getDbLoadCount()).isEqualTo(afterFirstCall);
	}

	@Test
	void findChildrenByParentIds_hitsDatabaseOnlyOnce_forRepeatedCalls() {
		categoryService.findChildrenByParentIds(List.of(root.getId()));
		int afterFirstCall = categoryService.getDbLoadCount();

		categoryService.findChildrenByParentIds(List.of(root.getId()));

		assertThat(categoryService.getDbLoadCount()).isEqualTo(afterFirstCall);
	}

	@Test
	void findChildrenByParentIds_onlyLoadsTheMissingParent_whenOneIsAlreadyCached() {
		categoryService.findChildrenByParentIds(List.of(root.getId()));
		int afterFirstCall = categoryService.getDbLoadCount();

		Map<Long, List<Category>> byParent = categoryService
				.findChildrenByParentIds(List.of(root.getId(), child.getId()));

		assertThat(byParent.get(root.getId())).extracting(Category::name).containsExactly("Audio");
		assertThat(byParent.get(child.getId())).isEmpty();
		assertThat(categoryService.getDbLoadCount()).isEqualTo(afterFirstCall + 1);
	}
}

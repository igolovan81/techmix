package com.testingai.graphql.domain;

import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryServiceTest {

	@Autowired
	private CategoryRepository categoryRepository;

	private CategoryService service;
	private CategoryEntity root;
	private CategoryEntity child;

	@BeforeEach
	void setUp() {
		service = new CategoryService(categoryRepository,
				new CaffeineCacheManager("categoriesById", "categoryChildren"));

		root = new CategoryEntity();
		root.setName("Electronics");
		root = categoryRepository.save(root);

		child = new CategoryEntity();
		child.setName("Audio");
		child.setParent(root);
		child = categoryRepository.save(child);
	}

	@Test
	void findCategory_mapsParentIdFromNestedEntity() {
		Category found = service.findCategory(child.getId()).orElseThrow();

		assertThat(found.parentId()).isEqualTo(root.getId());
	}

	@Test
	void findCategory_hasNullParentId_forRootCategory() {
		Category found = service.findCategory(root.getId()).orElseThrow();

		assertThat(found.parentId()).isNull();
	}

	@Test
	void listCategories_pushesPaginationToTheDatabase() {
		Connection<Category> page = service.listCategories(1, null);

		assertThat(page.edges()).hasSize(1);
		assertThat(page.pageInfo().hasNextPage()).isTrue();
		assertThat(page.totalCount()).isEqualTo(2);
	}

	@Test
	void findChildrenByParentIds_returnsEmptyList_forParentWithNoChildren() {
		Map<Long, List<Category>> byParent = service.findChildrenByParentIds(List.of(root.getId(), child.getId()));

		assertThat(byParent.get(root.getId())).extracting(Category::name).containsExactly("Audio");
		assertThat(byParent.get(child.getId())).isEmpty();
	}

	@Test
	void findByIds_returnsMapKeyedById() {
		Map<Long, Category> byId = service.findByIds(List.of(root.getId()));

		assertThat(byId.get(root.getId()).name()).isEqualTo("Electronics");
	}
}

package com.testingai.graphql.domain;

import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.KeysetPagination;
import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.CategorySpecifications;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	public Optional<Category> findCategory(Long id) {
		return categoryRepository.findById(id).map(CategoryService::toCategory);
	}

	public Connection<Category> listCategories(Integer first, String after) {
		Long cursorId = KeysetPagination.decodeCursor(after);
		int limit = KeysetPagination.normalizeFirst(first);
		var spec = CategorySpecifications.idAfter(cursorId);

		List<CategoryEntity> rows = categoryRepository.findAll(spec, PageRequest.of(0, limit + 1, Sort.by("id")))
				.getContent();
		return KeysetPagination.paginate(rows, limit, CategoryEntity::getId, CategoryService::toCategory,
				categoryRepository.count());
	}

	/** Batch-loads each category's full, unpaginated child list — cheap since only ~100 categories exist total. */
	public Map<Long, List<Category>> findChildrenByParentIds(List<Long> parentIds) {
		Map<Long, List<Category>> byParent = categoryRepository.findByParentIdIn(parentIds).stream()
				.map(CategoryService::toCategory).collect(Collectors.groupingBy(Category::parentId));
		Map<Long, List<Category>> result = new LinkedHashMap<>();
		for (Long parentId : parentIds) {
			result.put(parentId, byParent.getOrDefault(parentId, List.of()));
		}
		return result;
	}

	public Map<Long, Category> findByIds(List<Long> ids) {
		return categoryRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(CategoryEntity::getId, CategoryService::toCategory));
	}

	static Category toCategory(CategoryEntity entity) {
		Long parentId = entity.getParent() == null ? null : entity.getParent().getId();
		return new Category(entity.getId(), entity.getName(), parentId);
	}
}

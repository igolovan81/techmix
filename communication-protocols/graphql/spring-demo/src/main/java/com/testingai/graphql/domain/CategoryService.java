package com.testingai.graphql.domain;

import com.testingai.graphql.config.CacheConfig;
import com.testingai.graphql.entity.CategoryEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.KeysetPagination;
import com.testingai.graphql.repository.CategoryRepository;
import com.testingai.graphql.repository.CategorySpecifications;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final Cache categoriesByIdCache;
	private final Cache categoryChildrenCache;
	private final AtomicInteger dbLoadCount = new AtomicInteger();

	public CategoryService(CategoryRepository categoryRepository, CacheManager cacheManager) {
		this.categoryRepository = categoryRepository;
		this.categoriesByIdCache = cacheManager.getCache(CacheConfig.CATEGORIES_BY_ID);
		this.categoryChildrenCache = cacheManager.getCache(CacheConfig.CATEGORY_CHILDREN);
	}

	@Cacheable(cacheNames = CacheConfig.CATEGORIES_BY_ID, key = "#id")
	public Optional<Category> findCategory(Long id) {
		dbLoadCount.incrementAndGet();
		log.info("loading category {} from the database (cache miss)", id);
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

	/**
	 * Batch-loads each category's full, unpaginated child list — cheap since only ~100 categories exist total.
	 * Cache-aside by hand, not {@code @Cacheable}: annotating this method would cache by the whole incoming
	 * {@code parentIds} list as one key, which almost never repeats across requests (near-zero hit rate).
	 */
	public Map<Long, List<Category>> findChildrenByParentIds(List<Long> parentIds) {
		Map<Long, List<Category>> result = new LinkedHashMap<>();
		List<Long> misses = new ArrayList<>();
		for (Long parentId : parentIds) {
			@SuppressWarnings("unchecked")
			List<Category> cached = categoryChildrenCache.get(parentId, List.class);
			if (cached != null) {
				result.put(parentId, cached);
			} else {
				misses.add(parentId);
			}
		}
		if (!misses.isEmpty()) {
			dbLoadCount.incrementAndGet();
			log.info("loading children for {} categories from the database (cache miss): {}", misses.size(), misses);
			Map<Long, List<Category>> loaded = categoryRepository.findByParentIdIn(misses).stream()
					.map(CategoryService::toCategory).collect(Collectors.groupingBy(Category::parentId));
			for (Long parentId : misses) {
				List<Category> children = loaded.getOrDefault(parentId, List.of());
				categoryChildrenCache.put(parentId, children);
				result.put(parentId, children);
			}
		}
		return result;
	}

	/**
	 * Same cache-aside reasoning as {@link #findChildrenByParentIds(List)} — this is also a batch method keyed by a
	 * list of ids. Shares the {@code categoriesById} cache with {@link #findCategory(Long)}: both cache the same shape
	 * (a single {@link Category} by id), so a category warmed by one is a hit in the other.
	 */
	public Map<Long, Category> findByIds(List<Long> ids) {
		Map<Long, Category> result = new LinkedHashMap<>();
		List<Long> misses = new ArrayList<>();
		for (Long id : ids) {
			Category cached = categoriesByIdCache.get(id, Category.class);
			if (cached != null) {
				result.put(id, cached);
			} else {
				misses.add(id);
			}
		}
		if (!misses.isEmpty()) {
			dbLoadCount.incrementAndGet();
			log.info("loading {} categories from the database (cache miss): {}", misses.size(), misses);
			for (CategoryEntity entity : categoryRepository.findAllById(misses)) {
				Category category = toCategory(entity);
				categoriesByIdCache.put(entity.getId(), category);
				result.put(entity.getId(), category);
			}
		}
		return result;
	}

	public int getDbLoadCount() {
		return dbLoadCount.get();
	}

	static Category toCategory(CategoryEntity entity) {
		Long parentId = entity.getParent() == null ? null : entity.getParent().getId();
		return new Category(entity.getId(), entity.getName(), parentId);
	}
}

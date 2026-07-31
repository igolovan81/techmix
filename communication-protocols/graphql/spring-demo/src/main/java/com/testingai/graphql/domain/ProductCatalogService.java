package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.pagination.Connection;
import com.testingai.graphql.pagination.KeysetPagination;
import com.testingai.graphql.repository.ProductRepository;
import com.testingai.graphql.repository.ProductSpecifications;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogService {

	private final ProductRepository productRepository;

	public ProductCatalogService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public Optional<Product> findProduct(String productId) {
		return parseId(productId).flatMap(productRepository::findById).map(ProductCatalogService::toProduct);
	}

	public Connection<Product> listProducts(ProductFilter filter, Integer first, String after) {
		Long cursorId = KeysetPagination.decodeCursor(after);
		int limit = KeysetPagination.normalizeFirst(first);
		var spec = ProductSpecifications.matching(filter).and(ProductSpecifications.idAfter(cursorId));

		List<ProductEntity> rows = productRepository.findAll(spec, PageRequest.of(0, limit + 1, Sort.by("id")))
				.getContent();
		long totalCount = productRepository.count(ProductSpecifications.matching(filter));

		return KeysetPagination.paginate(rows, limit, ProductEntity::getId, ProductCatalogService::toProduct,
				totalCount);
	}

	public Map<String, Product> findByIds(List<String> productIds) {
		List<Long> ids = productIds.stream().map(Long::parseLong).toList();
		Map<String, Product> result = new LinkedHashMap<>();
		for (ProductEntity entity : productRepository.findAllById(ids)) {
			result.put(entity.getId().toString(), toProduct(entity));
		}
		return result;
	}

	private static Optional<Long> parseId(String productId) {
		try {
			return Optional.of(Long.parseLong(productId));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	static Product toProduct(ProductEntity entity) {
		return new Product(entity.getId().toString(), entity.getName(), entity.getPriceCents(), entity.getStockQty());
	}
}

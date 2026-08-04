package com.testingai.graphql.domain;

import com.testingai.graphql.entity.ProductImageEntity;
import com.testingai.graphql.repository.ProductImageRepository;
import com.testingai.graphql.repository.ProductRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProductImageService {

	private final ProductRepository productRepository;
	private final ProductImageRepository productImageRepository;

	public ProductImageService(ProductRepository productRepository, ProductImageRepository productImageRepository) {
		this.productRepository = productRepository;
		this.productImageRepository = productImageRepository;
	}

	public Set<Long> findProductIdsWithImage(List<Long> productIds) {
		return new HashSet<>(productImageRepository.findProductIdsWithImage(productIds));
	}

	public void upload(Long productId, String contentType, byte[] data) {
		if (!productRepository.existsById(productId)) {
			throw new NoSuchElementException("No product with id " + productId);
		}
		productImageRepository.save(new ProductImageEntity(productId, contentType, data, Instant.now()));
	}

	public Optional<ProductImage> find(Long productId) {
		return productImageRepository.findById(productId)
				.map(entity -> new ProductImage(entity.getContentType(), entity.getData()));
	}
}

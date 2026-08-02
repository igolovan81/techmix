package com.testingai.graphql.domain;

import com.testingai.graphql.repository.ProductImageRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProductImageService {

	private final ProductImageRepository productImageRepository;

	public ProductImageService(ProductImageRepository productImageRepository) {
		this.productImageRepository = productImageRepository;
	}

	public Set<Long> findProductIdsWithImage(List<Long> productIds) {
		return new HashSet<>(productImageRepository.findProductIdsWithImage(productIds));
	}
}

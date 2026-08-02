package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ProductEntity;
import com.testingai.graphql.entity.ProductImageEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductImageRepositoryTest {

	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductImageRepository productImageRepository;

	@Test
	void findProductIdsWithImage_returnsOnlyIdsThatHaveAnImageRow() {
		Long withImageId = saveProduct("With Image");
		Long withoutImageId = saveProduct("Without Image");
		productImageRepository
				.save(new ProductImageEntity(withImageId, "image/png", new byte[]{1, 2, 3}, Instant.now()));

		List<Long> result = productImageRepository.findProductIdsWithImage(List.of(withImageId, withoutImageId));

		assertThat(result).containsExactly(withImageId);
	}

	@Test
	void save_replacesThePreviousImage_whenCalledTwiceForTheSameProduct() {
		Long productId = saveProduct("Replaceable");
		productImageRepository.save(new ProductImageEntity(productId, "image/png", new byte[]{1}, Instant.now()));

		productImageRepository.save(new ProductImageEntity(productId, "image/jpeg", new byte[]{2, 2}, Instant.now()));

		ProductImageEntity latest = productImageRepository.findById(productId).orElseThrow();
		assertThat(latest.getContentType()).isEqualTo("image/jpeg");
		assertThat(latest.getData()).isEqualTo(new byte[]{2, 2});
	}

	private Long saveProduct(String name) {
		ProductEntity entity = new ProductEntity();
		entity.setName(name);
		entity.setPriceCents(1000);
		entity.setStockQty(10);
		return productRepository.save(entity).getId();
	}
}

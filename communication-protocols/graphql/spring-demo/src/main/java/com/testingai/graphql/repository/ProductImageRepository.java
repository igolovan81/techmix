package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ProductImageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

	@Query("select pi.productId from ProductImageEntity pi where pi.productId in :productIds")
	List<Long> findProductIdsWithImage(@Param("productIds") List<Long> productIds);
}

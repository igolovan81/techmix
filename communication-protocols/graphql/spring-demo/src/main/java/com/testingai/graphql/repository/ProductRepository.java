package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ProductEntity p where p.id = :id")
	Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);

	@Query("select distinct p from ProductEntity p left join fetch p.categories where p.id in :ids")
	List<ProductEntity> findByIdInWithCategories(@Param("ids") List<Long> ids);
}

package com.testingai.graphql.repository;

import com.testingai.graphql.entity.ReviewEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {
	List<ReviewEntity> findByProductIdIn(List<Long> productIds);
}

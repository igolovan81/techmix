package com.testingai.graphql.repository;

import com.testingai.graphql.entity.OrderItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
	List<OrderItemEntity> findByOrderIdIn(List<Long> orderIds);
}

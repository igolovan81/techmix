package com.testingai.graphql.repository;

import com.testingai.graphql.entity.CategoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoryRepository
		extends
			JpaRepository<CategoryEntity, Long>,
			JpaSpecificationExecutor<CategoryEntity> {
	List<CategoryEntity> findByParentId(Long parentId);
	List<CategoryEntity> findByParentIdIn(List<Long> parentIds);
}

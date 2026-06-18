package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchIndexInitializer {

	private final MongoTemplate mongoTemplate;

	@PostConstruct
	public void createIndexes() {
		mongoTemplate.indexOps(Product.class).ensureIndex(TextIndexDefinition.builder().onField("name").build());
		mongoTemplate.indexOps(Product.class)
				.ensureIndex(new Index().on("price", Sort.Direction.ASC).on("stock", Sort.Direction.ASC));
		log.info(
				"[SearchIndexInitializer] Ensured text index on products.name and compound index on products.(price, stock)");
	}
}

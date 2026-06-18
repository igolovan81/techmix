package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.TextQuery.queryText;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

	private final MongoTemplate mongoTemplate;

	public List<Product> searchByText(String text) {
		return mongoTemplate.find(queryText(TextCriteria.forDefaultLanguage().matching(text)), Product.class);
	}

	public List<Product> findByPriceRange(double min, double max) {
		Query query = new Query(where("price").gte(min).lte(max));
		return mongoTemplate.find(query, Product.class);
	}
}

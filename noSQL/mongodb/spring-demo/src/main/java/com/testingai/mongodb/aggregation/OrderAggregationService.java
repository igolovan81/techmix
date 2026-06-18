package com.testingai.mongodb.aggregation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderAggregationService {

	private final MongoTemplate mongoTemplate;

	public List<StatusSummary> summarizeByStatus() {
		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.group("status").count().as("orderCount").sum("lineTotal").as("totalRevenue"),
				Aggregation.sort(Sort.Direction.ASC, "_id"));
		return mongoTemplate.aggregate(aggregation, "orders", StatusSummary.class).getMappedResults();
	}
}

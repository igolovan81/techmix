package com.testingai.mongodb.aggregation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAggregationServiceTest {

	@InjectMocks
	private OrderAggregationService aggregationService;

	@Mock
	private MongoTemplate mongoTemplate;

	@Test
	void summarizeByStatus_shouldReturnMappedResults() {
		List<StatusSummary> expected = List.of(new StatusSummary("PLACED", 2, 50.0));
		@SuppressWarnings("unchecked")
		AggregationResults<StatusSummary> results = mock(AggregationResults.class);
		when(results.getMappedResults()).thenReturn(expected);
		when(mongoTemplate.aggregate(any(Aggregation.class), eq("orders"), eq(StatusSummary.class)))
				.thenReturn(results);

		List<StatusSummary> actual = aggregationService.summarizeByStatus();

		assertThat(actual).isEqualTo(expected);
	}
}

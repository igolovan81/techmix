package com.testingai.graphql.config;

import com.testingai.graphql.instrumentation.BadRequestMaxQueryComplexityInstrumentation;
import com.testingai.graphql.instrumentation.BadRequestMaxQueryDepthInstrumentation;
import com.testingai.graphql.instrumentation.PaginationAwareFieldComplexityCalculator;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryLimitsConfig {

	@Bean
	public Instrumentation maxQueryDepthInstrumentation(QueryLimitsProperties properties) {
		return new BadRequestMaxQueryDepthInstrumentation(properties.maxQueryDepth());
	}

	@Bean
	public Instrumentation maxQueryComplexityInstrumentation(QueryLimitsProperties properties) {
		return new BadRequestMaxQueryComplexityInstrumentation(properties.maxQueryComplexity(),
				new PaginationAwareFieldComplexityCalculator());
	}
}

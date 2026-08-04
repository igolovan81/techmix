package com.testingai.graphql.config;

import com.testingai.graphql.instrumentation.BadRequestMaxQueryComplexityInstrumentation;
import com.testingai.graphql.instrumentation.BadRequestMaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QueryLimitsConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(QueryLimitsConfig.class)
			.withBean(QueryLimitsProperties.class, () -> new QueryLimitsProperties(15, 10000));

	@Test
	void registersBothInstrumentationsWithBadRequestClassification() {
		contextRunner.run(context -> {
			assertThat(context.getBeansOfType(Instrumentation.class).values()).hasSize(2)
					.hasAtLeastOneElementOfType(BadRequestMaxQueryDepthInstrumentation.class)
					.hasAtLeastOneElementOfType(BadRequestMaxQueryComplexityInstrumentation.class);
		});
	}
}

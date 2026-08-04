package com.testingai.graphql.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QueryLimitsPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class);

	@Test
	void bindsMaxQueryDepthAndMaxQueryComplexity_fromAppGraphqlProperties() {
		contextRunner.withPropertyValues("app.graphql.max-query-depth=20", "app.graphql.max-query-complexity=12345")
				.run(context -> {
					QueryLimitsProperties properties = context.getBean(QueryLimitsProperties.class);
					assertThat(properties.maxQueryDepth()).isEqualTo(20);
					assertThat(properties.maxQueryComplexity()).isEqualTo(12345);
				});
	}

	@EnableConfigurationProperties(QueryLimitsProperties.class)
	static class TestConfig {
	}
}

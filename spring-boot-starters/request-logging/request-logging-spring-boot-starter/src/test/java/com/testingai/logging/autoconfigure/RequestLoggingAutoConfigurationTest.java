package com.testingai.logging.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RequestLoggingAutoConfiguration.class));

	@Test
	void filterRegisteredByDefault_withDefaultProperties() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RequestLoggingFilter.class);
			RequestLoggingProperties properties = context.getBean(RequestLoggingProperties.class);
			assertThat(properties.enabled()).isTrue();
			assertThat(properties.includeBody()).isFalse();
			assertThat(properties.excludedPaths()).containsExactly("/actuator/**");
		});
	}

	@Test
	void filterAbsentWhenDisabled() {
		contextRunner.withPropertyValues("app.logging.request.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(RequestLoggingFilter.class));
	}

	@Test
	void backsOffWhenUserProvidesOwnFilterBean() {
		contextRunner.withUserConfiguration(CustomFilterConfig.class).run(context -> {
			assertThat(context).hasSingleBean(RequestLoggingFilter.class);
			assertThat(context.getBean(RequestLoggingFilter.class)).isSameAs(CustomFilterConfig.CUSTOM_FILTER);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomFilterConfig {

		static final RequestLoggingFilter CUSTOM_FILTER = new RequestLoggingFilter(
				new RequestLoggingProperties(true, false, List.of()));

		@Bean
		RequestLoggingFilter requestLoggingFilter() {
			return CUSTOM_FILTER;
		}
	}
}

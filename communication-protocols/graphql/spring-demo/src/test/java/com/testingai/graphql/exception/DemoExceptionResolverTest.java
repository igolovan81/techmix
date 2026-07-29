package com.testingai.graphql.exception;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoExceptionResolverTest {

	private final DemoExceptionResolver resolver = new DemoExceptionResolver();

	@Test
	void resolveException_classifiesRuntimeExceptionAsInternalError() {
		DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

		List<GraphQLError> errors = resolver.resolveException(new RuntimeException("Simulated 5% failure"), env)
				.block();

		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getMessage()).isEqualTo("Simulated 5% failure");
		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
	}

	@Test
	void resolveException_classifiesIllegalArgumentExceptionAsBadRequest() {
		DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);

		List<GraphQLError> errors = resolver.resolveException(new IllegalArgumentException("Unknown product: p99"), env)
				.block();

		assertThat(errors.get(0).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
	}
}

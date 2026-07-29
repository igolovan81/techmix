package com.testingai.graphql.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Classifies exceptions thrown from data fetchers into typed GraphQL errors instead of leaking a raw stack trace:
 * {@link IllegalArgumentException} (e.g. {@code addReview} against an unknown product) becomes {@code BAD_REQUEST};
 * anything else — including {@link com.testingai.graphql.util.FailureSimulator}'s simulated failures — becomes
 * {@code INTERNAL_ERROR}.
 */
@Component
public class DemoExceptionResolver extends DataFetcherExceptionResolverAdapter {

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		ErrorType errorType = ex instanceof IllegalArgumentException ? ErrorType.BAD_REQUEST : ErrorType.INTERNAL_ERROR;
		return GraphqlErrorBuilder.newError().errorType(errorType).message(ex.getMessage()).build();
	}
}

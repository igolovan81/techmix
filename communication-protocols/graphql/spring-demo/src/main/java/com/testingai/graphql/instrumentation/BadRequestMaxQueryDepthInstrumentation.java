package com.testingai.graphql.instrumentation;

import graphql.GraphqlErrorBuilder;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.AbortExecutionException;
import org.springframework.graphql.execution.ErrorType;

import java.util.List;

/**
 * {@link MaxQueryDepthInstrumentation} rejects via {@link AbortExecutionException}, whose {@code getErrorType()} is
 * hardcoded to {@code graphql.ErrorType.ExecutionAborted} — a different classification than this app's
 * {@code BAD_REQUEST} convention ({@link com.testingai.graphql.exception.DemoExceptionResolver}). Overriding
 * {@code mkAbortException} to carry a pre-built {@link graphql.GraphQLError} makes graphql-java's abort handler use
 * that error's classification verbatim instead, bypassing {@code ExecutionAborted} entirely.
 */
public class BadRequestMaxQueryDepthInstrumentation extends MaxQueryDepthInstrumentation {

	public BadRequestMaxQueryDepthInstrumentation(int maxDepth) {
		super(maxDepth);
	}

	@Override
	protected AbortExecutionException mkAbortException(int depth, int maxDepth) {
		return new AbortExecutionException(List.of(GraphqlErrorBuilder.newError().errorType(ErrorType.BAD_REQUEST)
				.message("Query depth " + depth + " exceeds maximum allowed depth " + maxDepth).build()));
	}
}

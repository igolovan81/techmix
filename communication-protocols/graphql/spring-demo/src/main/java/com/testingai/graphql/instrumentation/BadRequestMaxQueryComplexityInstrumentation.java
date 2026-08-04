package com.testingai.graphql.instrumentation;

import graphql.GraphqlErrorBuilder;
import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.execution.AbortExecutionException;
import org.springframework.graphql.execution.ErrorType;

import java.util.List;

/**
 * Same {@code AbortExecutionException} remapping as {@link BadRequestMaxQueryDepthInstrumentation}, for the complexity
 * limit.
 */
public class BadRequestMaxQueryComplexityInstrumentation extends MaxQueryComplexityInstrumentation {

	public BadRequestMaxQueryComplexityInstrumentation(int maxComplexity,
			FieldComplexityCalculator fieldComplexityCalculator) {
		super(maxComplexity, fieldComplexityCalculator);
	}

	@Override
	protected AbortExecutionException mkAbortException(int totalComplexity, int maxComplexity) {
		return new AbortExecutionException(List.of(GraphqlErrorBuilder.newError().errorType(ErrorType.BAD_REQUEST)
				.message("Query complexity " + totalComplexity + " exceeds maximum allowed complexity " + maxComplexity)
				.build()));
	}
}

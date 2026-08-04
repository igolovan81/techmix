package com.testingai.graphql.instrumentation;

import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.FieldComplexityEnvironment;

/**
 * Weights a field's cost by its {@code first} argument instead of graphql-java's default flat
 * {@code 1 + childComplexity}, but only for fields whose schema definition actually declares {@code first} — plain
 * object fields like {@code Review.author} or {@code Category.parent} keep the flat cost. When a paginated field's
 * {@code first} argument is omitted by the client, this defaults the multiplier to 10, mirroring
 * {@code CursorPagination.DEFAULT_FIRST} — the actual page size the server would apply.
 */
public class PaginationAwareFieldComplexityCalculator implements FieldComplexityCalculator {

	private static final int DEFAULT_FIRST = 10;

	@Override
	public int calculate(FieldComplexityEnvironment environment, int childComplexity) {
		boolean isPaginated = environment.getFieldDefinition().getArgument("first") != null;
		if (!isPaginated) {
			return 1 + childComplexity;
		}
		Object first = environment.getArguments().get("first");
		int multiplier = first instanceof Integer value ? value : DEFAULT_FIRST;
		return 1 + multiplier * childComplexity;
	}
}

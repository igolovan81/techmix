package com.testingai.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.analysis.FieldComplexityCalculator;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestMaxQueryComplexityInstrumentationTest {

	private static final String SDL = """
			type Query { products(first: Int): ProductConnection }
			type ProductConnection { edges: [ProductEdge] }
			type ProductEdge { node: Product }
			type Product { id: ID }
			""";

	private static final String QUERY = "{ products(first: 5) { edges { node { id } } } }";

	@Test
	void rejectsWithBadRequestClassification_whenComplexityExceedsMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryComplexityInstrumentation(15, calculator())).build();

		ExecutionResult result = graphQl.execute(QUERY);

		assertThat(result.getErrors()).hasSize(1);
		GraphQLError error = result.getErrors().get(0);
		assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(error.getMessage()).isEqualTo("Query complexity 16 exceeds maximum allowed complexity 15");
	}

	@Test
	void allowsExecution_whenComplexityWithinMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryComplexityInstrumentation(20, calculator())).build();

		ExecutionResult result = graphQl.execute(QUERY);

		assertThat(result.getErrors()).isEmpty();
	}

	private FieldComplexityCalculator calculator() {
		return new PaginationAwareFieldComplexityCalculator();
	}

	private GraphQLSchema buildSchema() {
		TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
		return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
	}
}

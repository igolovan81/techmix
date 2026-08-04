package com.testingai.graphql.instrumentation;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestMaxQueryDepthInstrumentationTest {

	private static final String SDL = """
			type Query { a: A }
			type A { b: A id: ID }
			""";

	@Test
	void rejectsWithBadRequestClassification_whenDepthExceedsMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryDepthInstrumentation(1)).build();

		ExecutionResult result = graphQl.execute("{ a { b { id } } }");

		assertThat(result.getErrors()).hasSize(1);
		GraphQLError error = result.getErrors().get(0);
		assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		assertThat(error.getMessage()).isEqualTo("Query depth 3 exceeds maximum allowed depth 1");
	}

	@Test
	void allowsExecution_whenDepthWithinMax() {
		GraphQL graphQl = GraphQL.newGraphQL(buildSchema())
				.instrumentation(new BadRequestMaxQueryDepthInstrumentation(10)).build();

		ExecutionResult result = graphQl.execute("{ a { b { id } } }");

		assertThat(result.getErrors()).isEmpty();
	}

	private GraphQLSchema buildSchema() {
		TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
		return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.newRuntimeWiring().build());
	}
}

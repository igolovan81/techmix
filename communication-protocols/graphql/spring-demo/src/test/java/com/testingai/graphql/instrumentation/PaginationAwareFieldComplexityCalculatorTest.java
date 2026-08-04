package com.testingai.graphql.instrumentation;

import graphql.Scalars;
import graphql.analysis.FieldComplexityEnvironment;
import graphql.language.Field;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationAwareFieldComplexityCalculatorTest {

	private final PaginationAwareFieldComplexityCalculator calculator = new PaginationAwareFieldComplexityCalculator();

	@Test
	void multipliesChildComplexityByFirstArgument_whenFieldDeclaresFirstArgument() {
		FieldComplexityEnvironment environment = paginatedFieldEnvironment(Map.of("first", 40));

		int complexity = calculator.calculate(environment, 10);

		assertThat(complexity).isEqualTo(1 + 40 * 10);
	}

	@Test
	void usesDefaultMultiplierOfTen_whenFirstArgumentDeclaredButOmitted() {
		FieldComplexityEnvironment environment = paginatedFieldEnvironment(Map.of());

		int complexity = calculator.calculate(environment, 3);

		assertThat(complexity).isEqualTo(1 + 10 * 3);
	}

	@Test
	void doesNotMultiply_whenFieldHasNoFirstArgumentInItsSchemaDefinition() {
		GraphQLFieldDefinition fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("author")
				.type(Scalars.GraphQLID).build();
		FieldComplexityEnvironment environment = new FieldComplexityEnvironment(Field.newField("author").build(),
				fieldDefinition, GraphQLObjectType.newObject().name("Query").build(), Map.of(), null);

		int complexity = calculator.calculate(environment, 7);

		assertThat(complexity).isEqualTo(1 + 7);
	}

	private FieldComplexityEnvironment paginatedFieldEnvironment(Map<String, Object> arguments) {
		GraphQLFieldDefinition fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name("products")
				.type(GraphQLList.list(Scalars.GraphQLID))
				.argument(GraphQLArgument.newArgument().name("first").type(Scalars.GraphQLInt)).build();
		return new FieldComplexityEnvironment(Field.newField("products").build(), fieldDefinition,
				GraphQLObjectType.newObject().name("Query").build(), arguments, null);
	}
}

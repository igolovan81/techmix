package com.testingai.mongodb.search;

import com.testingai.mongodb.crud.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexInitializerTest {

	@InjectMocks
	private SearchIndexInitializer initializer;

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private IndexOperations indexOperations;

	@Test
	void createIndexes_shouldEnsureBothIndexesWithoutThrowing() {
		when(mongoTemplate.indexOps(Product.class)).thenReturn(indexOperations);
		when(indexOperations.ensureIndex(any())).thenReturn("ok");

		assertThatCode(() -> initializer.createIndexes()).doesNotThrowAnyException();

		verify(indexOperations, times(2)).ensureIndex(any());
	}
}

package com.testingai.embedding.service;

import com.testingai.embedding.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class VectorStoreServiceTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16")
          .withDatabaseName("testdb")
          .withUsername("postgres")
          .withPassword("postgres");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("openai.api-key", () -> "test");
    registry.add("openai.base-url", () -> "http://localhost:1");
  }

  @Autowired
  private VectorStoreService vectorStoreService;

  @Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  @BeforeEach
  void clearTable() {
    jdbc.update("DELETE FROM kb_chunk");
  }

  @Test
  void upsertAndSearch_returnsInsertedChunk() {
    float[] embedding = new float[1536];
    embedding[0] = 1.0f;

    vectorStoreService.upsert("Refund Policy", "Refunds within 30 days.", embedding);

    float[] query = new float[1536];
    query[0] = 1.0f;
    List<SearchResult> results = vectorStoreService.search(query, 3);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().title()).isEqualTo("Refund Policy");
    assertThat(results.getFirst().content()).isEqualTo("Refunds within 30 days.");
    assertThat(results.getFirst().score()).isGreaterThan(0.99);
  }

  @Test
  void search_limitsResults() {
    float[] v = new float[1536];
    v[0] = 1.0f;
    vectorStoreService.upsert("A", "content a", v);
    vectorStoreService.upsert("B", "content b", v);
    vectorStoreService.upsert("C", "content c", v);

    List<SearchResult> results = vectorStoreService.search(v, 2);

    assertThat(results).hasSize(2);
  }
}

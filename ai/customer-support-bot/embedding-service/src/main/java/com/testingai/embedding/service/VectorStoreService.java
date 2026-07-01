package com.testingai.embedding.service;

import com.testingai.embedding.model.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorStoreService {

  private final JdbcTemplate jdbc;

  public VectorStoreService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void upsert(String title, String content, float[] embedding) {
    jdbc.update(
        "INSERT INTO kb_chunk (title, content, embedding) VALUES (?, ?, ?::vector)",
        title, content, formatVector(embedding));
  }

  public List<SearchResult> search(float[] queryEmbedding, int limit) {
    String vec = formatVector(queryEmbedding);
    return jdbc.query(
        "SELECT title, content, 1 - (embedding <=> ?::vector) AS score "
            + "FROM kb_chunk ORDER BY embedding <=> ?::vector LIMIT ?",
        (rs, rowNum) -> new SearchResult(
            rs.getString("title"),
            rs.getString("content"),
            rs.getDouble("score")),
        vec, vec, limit);
  }

  private static String formatVector(float[] v) {
    var sb = new StringBuilder("[");
    for (int i = 0; i < v.length; i++) {
      if (i > 0) sb.append(",");
      sb.append(v[i]);
    }
    return sb.append("]").toString();
  }
}

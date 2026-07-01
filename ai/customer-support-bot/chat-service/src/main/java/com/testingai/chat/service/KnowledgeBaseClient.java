package com.testingai.chat.service;

import com.testingai.chat.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
public class KnowledgeBaseClient {

  private final RestClient restClient;

  public KnowledgeBaseClient(RestClient embeddingServiceClient) {
    this.restClient = embeddingServiceClient;
  }

  public List<SearchResult> search(String query, int limit) {
    try {
      return restClient.get()
          .uri("/api/kb/search?q={q}&limit={limit}", query, limit)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {});
    } catch (RestClientException e) {
      log.warn("Embedding service unavailable: {}", e.getMessage());
      return List.of();
    }
  }
}

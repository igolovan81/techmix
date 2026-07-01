package com.testingai.embedding.controller;

import com.testingai.embedding.model.IngestRequest;
import com.testingai.embedding.model.SearchResult;
import com.testingai.embedding.service.EmbeddingService;
import com.testingai.embedding.service.VectorStoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

  private final EmbeddingService embeddingService;
  private final VectorStoreService vectorStoreService;

  public KnowledgeBaseController(
      EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
    this.embeddingService = embeddingService;
    this.vectorStoreService = vectorStoreService;
  }

  @PostMapping("/ingest")
  @ResponseStatus(HttpStatus.CREATED)
  public void ingest(@RequestBody @Valid IngestRequest request) {
    float[] embedding = embeddingService.embed(request.content());
    vectorStoreService.upsert(request.title(), request.content(), embedding);
  }

  @GetMapping("/search")
  public List<SearchResult> search(
      @RequestParam String q, @RequestParam(defaultValue = "3") int limit) {
    float[] embedding = embeddingService.embed(q);
    return vectorStoreService.search(embedding, limit);
  }
}

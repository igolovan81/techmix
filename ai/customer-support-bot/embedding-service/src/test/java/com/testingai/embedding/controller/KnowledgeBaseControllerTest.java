package com.testingai.embedding.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.embedding.model.IngestRequest;
import com.testingai.embedding.model.SearchResult;
import com.testingai.embedding.service.EmbeddingService;
import com.testingai.embedding.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KnowledgeBaseController.class)
class KnowledgeBaseControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private EmbeddingService embeddingService;
  @MockBean private VectorStoreService vectorStoreService;

  @Test
  void ingest_returns201AndCallsServices() throws Exception {
    float[] vector = {0.1f, 0.2f};
    when(embeddingService.embed("How to return items?")).thenReturn(vector);

    mockMvc
        .perform(
            post("/api/kb/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new IngestRequest("Returns", "How to return items?"))))
        .andExpect(status().isCreated());

    verify(vectorStoreService).upsert("Returns", "How to return items?", vector);
  }

  @Test
  void ingest_returns400WhenTitleBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/kb/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"content\":\"some content\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void search_returnsChunks() throws Exception {
    float[] qVec = {0.5f};
    when(embeddingService.embed("refund")).thenReturn(qVec);
    when(vectorStoreService.search(qVec, 3))
        .thenReturn(List.of(new SearchResult("Refund Policy", "30-day policy", 0.95)));

    mockMvc
        .perform(get("/api/kb/search").param("q", "refund"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Refund Policy"))
        .andExpect(jsonPath("$[0].score").value(0.95));
  }
}

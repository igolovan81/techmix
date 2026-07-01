package com.testingai.embedding.service;

import com.testingai.embedding.config.OpenAiProperties;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService {

  private final RestClient restClient;
  private final OpenAiProperties props;

  public EmbeddingService(RestClient openAiRestClient, OpenAiProperties props) {
    this.restClient = openAiRestClient;
    this.props = props;
  }

  public float[] embed(String text) {
    var request = new EmbeddingRequest(text, props.model());
    var response =
        restClient
            .post()
            .uri("/v1/embeddings")
            .body(request)
            .retrieve()
            .body(EmbeddingResponse.class);
    List<Float> floats = response.data().getFirst().embedding();
    float[] result = new float[floats.size()];
    for (int i = 0; i < floats.size(); i++) result[i] = floats.get(i);
    return result;
  }

  private record EmbeddingRequest(String input, String model) {}

  private record EmbeddingResponse(List<EmbeddingData> data) {
    private record EmbeddingData(List<Float> embedding) {}
  }
}

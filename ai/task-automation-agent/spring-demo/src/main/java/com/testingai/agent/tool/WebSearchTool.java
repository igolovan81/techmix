package com.testingai.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.agent.config.TavilyProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class WebSearchTool {

    private final RestClient restClient;
    private final TavilyProperties tavily;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSearchTool(RestClient tavilyRestClient, TavilyProperties tavily) {
        this.restClient = tavilyRestClient;
        this.tavily = tavily;
    }

    public String search(String query, int numResults) {
        try {
            var body = Map.of(
                    "api_key", tavily.apiKey(),
                    "query", query,
                    "max_results", numResults);
            TavilyResponse response = restClient.post()
                    .uri("/search")
                    .body(body)
                    .retrieve()
                    .body(TavilyResponse.class);
            List<TavilyResult> results = response != null ? response.results() : List.of();
            return objectMapper.writeValueAsString(results);
        } catch (RestClientException | JsonProcessingException e) {
            return errorJson(e.getMessage());
        }
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    public Tool definition() {
        return Tool.builder()
                .name("web_search")
                .description("Search the web for up-to-date information. Returns a list of results with title, URL, and text content.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("query", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "The search query")))
                                .putAdditionalProperty("num_results", JsonValue.from(Map.of(
                                        "type", "integer",
                                        "description", "Number of results to return (default 5)")))
                                .build())
                        .required(List.of("query"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    record TavilyResponse(List<TavilyResult> results) {}
    record TavilyResult(String title, String url, String content) {}
}

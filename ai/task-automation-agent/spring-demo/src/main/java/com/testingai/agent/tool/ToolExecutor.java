package com.testingai.agent.tool;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolExecutor {

    private final WebSearchTool webSearch;
    private final FetchPageTool fetchPage;

    public ToolExecutor(WebSearchTool webSearch, FetchPageTool fetchPage) {
        this.webSearch = webSearch;
        this.fetchPage = fetchPage;
    }

    public String execute(String toolName, JsonValue input) {
        try {
            Map<String, Object> fields = input.convert(new TypeReference<Map<String, Object>>() {});
            if (fields == null) {
                return "{\"error\": \"Tool input must be a JSON object\"}";
            }
            return switch (toolName) {
                case "web_search" -> {
                    Object query = fields.get("query");
                    if (query == null) yield "{\"error\": \"web_search: missing required field 'query'\"}";
                    int numResults = fields.containsKey("num_results")
                            ? ((Number) fields.get("num_results")).intValue()
                            : 5;
                    yield webSearch.search(query.toString(), numResults);
                }
                case "fetch_page" -> {
                    Object url = fields.get("url");
                    if (url == null) yield "{\"error\": \"fetch_page: missing required field 'url'\"}";
                    yield fetchPage.fetch(url.toString());
                }
                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\": \"ToolExecutor error: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}

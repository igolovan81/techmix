package com.testingai.agent.tool;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
public class ToolExecutor {

    private final WebSearchTool webSearch;
    private final FetchPageTool fetchPage;

    public ToolExecutor(WebSearchTool webSearch, FetchPageTool fetchPage) {
        this.webSearch = webSearch;
        this.fetchPage = fetchPage;
    }

    public String execute(String toolName, JsonValue input) {
        Map<String, Object> fields = input.convert(new TypeReference<Map<String, Object>>() {});
        if (fields == null) {
            throw new IllegalArgumentException("Tool input must be a JSON object");
        }
        return switch (toolName) {
            case "web_search" -> {
                String query = (String) Objects.requireNonNull(
                        fields.get("query"), "web_search: missing 'query'");
                Number numResultsNum = (Number) fields.get("num_results");
                int numResults = numResultsNum != null ? numResultsNum.intValue() : 5;
                yield webSearch.search(query, numResults);
            }
            case "fetch_page" -> {
                String url = (String) Objects.requireNonNull(
                        fields.get("url"), "fetch_page: missing 'url'");
                yield fetchPage.fetch(url);
            }
            default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
        };
    }
}

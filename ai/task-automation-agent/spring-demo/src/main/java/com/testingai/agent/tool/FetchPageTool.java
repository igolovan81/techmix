package com.testingai.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.testingai.agent.config.AgentProperties;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Component
public class FetchPageTool {

    private final HttpClient httpClient;
    private final int maxChars;

    public FetchPageTool(HttpClient httpClient, AgentProperties agentProperties) {
        this.httpClient = httpClient;
        this.maxChars = agentProperties.fetchPageMaxChars();
    }

    // Package-private constructor used by tests to inject fixed maxChars without a full Spring context
    FetchPageTool(HttpClient httpClient, int maxChars) {
        this.httpClient = httpClient;
        this.maxChars = maxChars;
    }

    public String fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AgentBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "{\"error\": \"HTTP " + response.statusCode() + " fetching " + url + "\"}";
            }
            String text = Jsoup.parse(response.body()).text();
            return text.length() > maxChars ? text.substring(0, maxChars) : text;
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    public Tool definition() {
        return Tool.builder()
                .name("fetch_page")
                .description("Fetch the full text content of a web page by URL. Use this after web_search to read a result in detail.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("url", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "The full URL of the page to fetch")))
                                .build())
                        .required(List.of("url"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }
}

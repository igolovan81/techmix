package com.testingai.mcpexplorer.client.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AppConfig {

    private final AnthropicProperties anthropic;
    private final McpClientProperties mcpClientProperties;

    public AppConfig(AnthropicProperties anthropic, McpClientProperties mcpClientProperties) {
        this.anthropic = anthropic;
        this.mcpClientProperties = mcpClientProperties;
    }

    @PostConstruct
    public void validateApiKeys() {
        if (!StringUtils.hasText(anthropic.apiKey())) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropic.apiKey())
                .build();
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpSyncClient mcpSyncClient() {
        var transport = HttpClientStreamableHttpTransport.builder(mcpClientProperties.serverUrl())
                .endpoint("/mcp")
                .build();
        McpSyncClient client = McpClient.sync(transport).build();
        client.initialize();
        return client;
    }
}

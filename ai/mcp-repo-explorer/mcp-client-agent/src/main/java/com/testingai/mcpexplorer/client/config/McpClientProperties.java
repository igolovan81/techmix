package com.testingai.mcpexplorer.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp")
public record McpClientProperties(String serverUrl) {
}

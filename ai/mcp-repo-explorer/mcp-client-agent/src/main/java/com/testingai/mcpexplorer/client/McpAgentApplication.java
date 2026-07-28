package com.testingai.mcpexplorer.client;

import com.testingai.mcpexplorer.client.config.AgentProperties;
import com.testingai.mcpexplorer.client.config.AnthropicProperties;
import com.testingai.mcpexplorer.client.config.McpClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, AnthropicProperties.class, McpClientProperties.class})
public class McpAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpAgentApplication.class, args);
    }
}

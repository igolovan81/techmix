package com.testingai.mcpexplorer.server;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerWiringTest {

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    @BeforeEach
    void setUp() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void tearDown() {
        client.closeGracefully();
    }

    @Test
    void listTools_returnsAllThreeTools() {
        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();

        assertThat(names).containsExactlyInAnyOrder("list_modules", "read_readme", "search_readmes");
    }

    @Test
    void callTool_listModules_returnsNonEmptyResult() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("list_modules", Map.of()));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void callTool_readReadme_withPathTraversal_returnsError() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("read_readme", Map.of("path", "../../../../etc/passwd")));

        assertThat(result.isError()).isTrue();
    }
}

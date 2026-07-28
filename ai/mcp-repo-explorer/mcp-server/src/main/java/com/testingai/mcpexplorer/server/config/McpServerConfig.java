package com.testingai.mcpexplorer.server.config;

import com.testingai.mcpexplorer.server.tool.ListModulesTool;
import com.testingai.mcpexplorer.server.tool.ReadReadmeTool;
import com.testingai.mcpexplorer.server.tool.SearchReadmesTool;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class McpServerConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider transportProvider() {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpSyncServer(WebMvcStreamableServerTransportProvider transportProvider,
                                        ListModulesTool listModulesTool,
                                        ReadReadmeTool readReadmeTool,
                                        SearchReadmesTool searchReadmesTool) {
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("mcp-repo-explorer", "0.0.1")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();

        server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                .tool(listModulesTool.definition())
                .callHandler((exchange, request) -> listModulesTool.call(request.arguments()))
                .build());
        server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                .tool(readReadmeTool.definition())
                .callHandler((exchange, request) -> readReadmeTool.call(request.arguments()))
                .build());
        server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                .tool(searchReadmesTool.definition())
                .callHandler((exchange, request) -> searchReadmesTool.call(request.arguments()))
                .build());

        return server;
    }
}

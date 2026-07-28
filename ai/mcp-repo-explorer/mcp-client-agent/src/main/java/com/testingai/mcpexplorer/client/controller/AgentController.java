package com.testingai.mcpexplorer.client.controller;

import com.testingai.mcpexplorer.client.model.AgentRequest;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import com.testingai.mcpexplorer.client.service.McpAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp-agent")
public class AgentController {

    private final McpAgentService agentService;

    public AgentController(McpAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public AgentResponse run(@RequestBody @Valid AgentRequest request) {
        return agentService.run(request.goal());
    }
}

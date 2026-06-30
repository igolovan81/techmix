package com.testingai.agent.controller;

import com.testingai.agent.model.AgentRequest;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public AgentResponse run(@RequestBody @Valid AgentRequest request) {
        return agentService.run(request.goal());
    }
}

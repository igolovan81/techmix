package com.testingai.mcpexplorer.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.mcpexplorer.client.model.AgentRequest;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import com.testingai.mcpexplorer.client.service.McpAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private McpAgentService agentService;

    @Test
    void run_returnsAgentResponseAsJson() throws Exception {
        AgentResponse expected = new AgentResponse("There are 3 modules.", List.of(), 1, false);
        when(agentService.run("How many modules are there?")).thenReturn(expected);

        mockMvc.perform(post("/api/mcp-agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentRequest("How many modules are there?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("There are 3 modules."))
                .andExpect(jsonPath("$.iterations").value(1))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void run_returns400WhenGoalIsBlank() throws Exception {
        mockMvc.perform(post("/api/mcp-agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}

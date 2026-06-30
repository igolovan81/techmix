package com.testingai.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.agent.model.AgentRequest;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private AgentService agentService;

    @Test
    void run_returnsAgentResponseAsJson() throws Exception {
        AgentResponse expected = new AgentResponse("Paris.", List.of(), 1, false);
        when(agentService.run("Capital of France?")).thenReturn(expected);

        mockMvc.perform(post("/api/agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentRequest("Capital of France?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Paris."))
                .andExpect(jsonPath("$.iterations").value(1))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void run_returns400WhenGoalIsBlank() throws Exception {
        mockMvc.perform(post("/api/agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}

package com.testingai.sdlc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.model.FixRequest;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.service.FixService;
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

@WebMvcTest(FixController.class)
class FixControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private FixService fixService;

    @Test
    void fix_returnsFixResponseAsJson() throws Exception {
        RootCauseHypothesis rootCause = new RootCauseHypothesis("NPE in DiscountService", List.of(), "high",
                List.of("DiscountService.java"));
        FixResponse expected = new FixResponse(rootCause, "Added a null check.", "diff --git a/... b/...",
                "hotfix/DEMO-101", "abc123", 4, List.of(), false);
        when(fixService.fix("DEMO-101")).thenReturn(expected);

        mockMvc.perform(post("/api/sdlc/fix").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FixRequest("DEMO-101")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.branchName").value("hotfix/DEMO-101"))
                .andExpect(jsonPath("$.commitSha").value("abc123")).andExpect(jsonPath("$.iterations").value(4))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void fix_returns400WhenTicketIdIsBlank() throws Exception {
        mockMvc.perform(
                post("/api/sdlc/fix").contentType(MediaType.APPLICATION_JSON).content("{\"ticketId\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}

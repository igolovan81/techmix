package com.testingai.sdlc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.model.InvestigateRequest;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.service.InvestigateService;
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

@WebMvcTest(InvestigateController.class)
class InvestigateControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private InvestigateService investigateService;

    @Test
    void investigate_returnsInvestigateResponseAsJson() throws Exception {
        RootCauseHypothesis hypothesis = new RootCauseHypothesis("NPE in DiscountService", List.of("line1"), "high",
                List.of("DiscountService.java"));
        InvestigateResponse expected = new InvestigateResponse(hypothesis, 2, List.of(), false);
        when(investigateService.investigate("DEMO-101")).thenReturn(expected);

        mockMvc.perform(post("/api/sdlc/investigate").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InvestigateRequest("DEMO-101"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rootCause.summary").value("NPE in DiscountService"))
                .andExpect(jsonPath("$.rootCause.confidence").value("high")).andExpect(jsonPath("$.iterations").value(2))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void investigate_returns400WhenTicketIdIsBlank() throws Exception {
        mockMvc.perform(post("/api/sdlc/investigate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketId\": \"\"}")).andExpect(status().isBadRequest());
    }
}

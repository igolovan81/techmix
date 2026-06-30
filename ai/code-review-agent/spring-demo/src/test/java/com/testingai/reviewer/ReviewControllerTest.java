package com.testingai.reviewer;

import com.testingai.reviewer.controller.ReviewController;
import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void analyseReturnsFindings() throws Exception {
        when(reviewService.analyse(anyString())).thenReturn(
                new ReviewResponse(
                        List.of(new Finding("WARNING", "Foo.java", 3, "Too long", "Split the method.")),
                        "1 warning found."));

        mockMvc.perform(post("/api/review/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diff\": \"diff content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("1 warning found."))
                .andExpect(jsonPath("$.findings[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.findings[0].file").value("Foo.java"));
    }

    @Test
    void returnsValidationErrorForMissingDiff() throws Exception {
        mockMvc.perform(post("/api/review/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diff\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}

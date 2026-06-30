package com.testingai.reviewer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyseReturnsFindings() throws Exception {
        String diff = """
                diff --git a/Foo.java b/Foo.java
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,6 @@
                 public class Foo {
                +    public void bar() {
                +        String unused = "hello";
                +    }
                 }
                """;

        mockMvc.perform(post("/api/review/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diff\": \"" + diff.replace("\"", "\\\"").replace("\n", "\\n") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings").isArray())
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }
}

package com.testingai.reviewer;

import com.testingai.reviewer.config.GitHubProperties;
import com.testingai.reviewer.controller.WebhookController;
import com.testingai.reviewer.service.GitHubClient;
import com.testingai.reviewer.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@Import(WebhookControllerTest.TestConfig.class)
@TestPropertySource(properties = {
        "github.webhook-secret=test-webhook-secret",
        "github.token=test-token"
})
class WebhookControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(GitHubProperties.class)
    static class TestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private GitHubClient gitHubClient;

    private static final String SECRET = "test-webhook-secret";
    private static final String PAYLOAD = """
            {"action":"opened","pull_request":{"number":1,"base":{"repo":{"name":"repo","owner":{"login":"owner"}}}}}
            """;

    @Test
    void acceptsValidSignature() throws Exception {
        String sig = "sha256=" + computeHmac(SECRET, PAYLOAD);
        mockMvc.perform(post("/api/review/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sig)
                        .header("X-GitHub-Event", "pull_request")
                        .content(PAYLOAD))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingSignature() throws Exception {
        mockMvc.perform(post("/api/review/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWrongSignature() throws Exception {
        mockMvc.perform(post("/api/review/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=wrongsig")
                        .content(PAYLOAD))
                .andExpect(status().isForbidden());
    }

    private static String computeHmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}

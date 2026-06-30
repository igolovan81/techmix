package com.testingai.reviewer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.reviewer.config.GitHubProperties;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.model.WebhookPayload;
import com.testingai.reviewer.service.GitHubClient;
import com.testingai.reviewer.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/review")
public class WebhookController {

    private final ReviewService reviewService;
    private final GitHubClient gitHubClient;
    private final GitHubProperties githubProps;
    private final ObjectMapper objectMapper;

    public WebhookController(ReviewService reviewService, GitHubClient gitHubClient,
                             GitHubProperties githubProps, ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.gitHubClient = gitHubClient;
        this.githubProps = githubProps;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (!verifySignature(body, signature)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            WebhookPayload payload = objectMapper.readValue(body, WebhookPayload.class);
            if (payload.pullRequest() == null) {
                return ResponseEntity.ok().build();
            }
            if (!"opened".equals(payload.action()) && !"synchronize".equals(payload.action())) {
                return ResponseEntity.ok().build();
            }

            String owner = payload.pullRequest().base().repo().owner().login();
            String repo = payload.pullRequest().base().repo().name();
            int prNumber = payload.pullRequest().number();

            String diff = gitHubClient.fetchPrDiff(owner, repo, prNumber);
            ReviewResponse review = reviewService.analyse(diff);
            gitHubClient.postReview(owner, repo, prNumber, review);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean verifySignature(byte[] body, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        String secret = githubProps.webhookSecret();
        if (secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }
}

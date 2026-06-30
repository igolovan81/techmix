package com.testingai.reviewer.service;

import com.testingai.reviewer.model.ReviewResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {

    private final RestClient restClient;

    public GitHubClient(@Qualifier("gitHubRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String fetchPrDiff(String owner, String repo, int prNumber) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                .header("Accept", "application/vnd.github.diff")
                .retrieve()
                .body(String.class);
    }

    public void postReview(String owner, String repo, int prNumber, ReviewResponse review) {
        String event = review.findings().stream()
                .anyMatch(f -> "ERROR".equals(f.severity())) ? "REQUEST_CHANGES" : "COMMENT";

        List<Map<String, Object>> comments = review.findings().stream()
                .map(f -> Map.<String, Object>of(
                        "path", f.file(),
                        "line", f.line(),
                        "body", "**" + f.severity() + " — " + f.message() + "**\n\n**Suggestion**: " + f.suggestion()))
                .toList();

        Map<String, Object> reviewBody = new LinkedHashMap<>();
        reviewBody.put("event", event);
        reviewBody.put("body", review.summary());
        reviewBody.put("comments", comments);

        restClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{number}/reviews", owner, repo, prNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .body(reviewBody)
                .retrieve()
                .toBodilessEntity();
    }
}

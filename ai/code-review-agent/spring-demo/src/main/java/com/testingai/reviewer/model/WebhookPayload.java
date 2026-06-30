package com.testingai.reviewer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
        String action,
        @JsonProperty("pull_request") PullRequest pullRequest
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(int number, Base base) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Base(Repo repo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repo(String name, Owner owner) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {}
}

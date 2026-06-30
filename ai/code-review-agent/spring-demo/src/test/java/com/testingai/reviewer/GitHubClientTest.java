package com.testingai.reviewer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.GitHubClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubClientTest {

    private WireMockServer wireMock;
    private GitHubClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Authorization", "Bearer test-token")
                .build();
        client = new GitHubClient(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetchPrDiffSendsCorrectAcceptHeader() {
        wireMock.stubFor(get(urlPathEqualTo("/repos/owner/repo/pulls/42"))
                .withHeader("Accept", equalTo("application/vnd.github.diff"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("diff --git a/Foo.java b/Foo.java\n")));

        String diff = client.fetchPrDiff("owner", "repo", 42);

        assertThat(diff).contains("diff --git");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42"))
                .withHeader("Accept", equalTo("application/vnd.github.diff")));
    }

    @Test
    void postReviewSendsCommentEvent() {
        wireMock.stubFor(post(urlPathEqualTo("/repos/owner/repo/pulls/42/reviews"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        ReviewResponse review = new ReviewResponse(
                List.of(new Finding("WARNING", "Foo.java", 3, "Too long", "Split it.")),
                "1 warning.");

        client.postReview("owner", "repo", 42, review);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/reviews"))
                .withRequestBody(matchingJsonPath("$.event", equalTo("COMMENT")))
                .withRequestBody(matchingJsonPath("$.comments[0].path", equalTo("Foo.java")))
                .withRequestBody(matchingJsonPath("$.comments[0].line", equalTo("3"))));
    }

    @Test
    void postReviewSendsRequestChangesForErrors() {
        wireMock.stubFor(post(urlPathEqualTo("/repos/owner/repo/pulls/1/reviews"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        ReviewResponse review = new ReviewResponse(
                List.of(new Finding("ERROR", "Bar.java", 5, "Null check missing", "Add null guard.")),
                "1 error.");

        client.postReview("owner", "repo", 1, review);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/1/reviews"))
                .withRequestBody(matchingJsonPath("$.event", equalTo("REQUEST_CHANGES"))));
    }
}

package com.testingai.sdlc.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.config.SandboxProperties;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixServiceTest {

    private static final Ticket TICKET = new Ticket("DEMO-101", "title", "description", "High", "checkout-service",
            Instant.parse("2026-07-10T10:00:00Z"));
    private static final RootCauseHypothesis ROOT_CAUSE = new RootCauseHypothesis(
            "NullPointerException in DiscountService.apply when discountCode is null", List.of(), "high",
            List.of("DiscountService.java"));

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock
    private TicketSource ticketSource;
    @Mock
    private InvestigationLoop investigationLoop;

    private FixService fixService;

    @BeforeEach
    void setUp() {
        when(ticketSource.fetch("DEMO-101")).thenReturn(TICKET);
        when(investigationLoop.investigate(TICKET))
                .thenReturn(new InvestigateResponse(ROOT_CAUSE, 1, List.of(), false));
        fixService = new FixService(anthropic, ticketSource, investigationLoop, new AgentProperties(10),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"), new SandboxProperties(true));
    }

    @Test
    void fix_singleIteration_noToolCalls_returnsSummaryWithoutCommit() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("Investigated but made no changes."));

        FixResponse result = fixService.fix("DEMO-101");

        assertThat(result.rootCause()).isEqualTo(ROOT_CAUSE);
        assertThat(result.summary()).isEqualTo("Investigated but made no changes.");
        assertThat(result.branchName()).isNull();
        assertThat(result.commitSha()).isNull();
        assertThat(result.patch()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void fix_writesFileAndCommitsBranch_returnsPatchAndBranch() {
        Message readCall = buildToolUseMessage("t1", "read_file",
                JsonValue.from(Map.of("path", "src/main/java/com/example/checkout/DiscountService.java")));
        Message writeCall = buildToolUseMessage("t2", "write_file",
                JsonValue.from(Map.of("path", "src/main/java/com/example/checkout/DiscountService.java", "content",
                        "public class DiscountService { public java.math.BigDecimal apply("
                                + "java.math.BigDecimal price, String discountCode) { "
                                + "if (discountCode != null && discountCode.length() > 0) { "
                                + "return price.multiply(java.math.BigDecimal.valueOf(0.9)); } "
                                + "return price; } }")));
        Message commitCall = buildToolUseMessage("t3", "git_commit_branch",
                JsonValue.from(Map.of("branchName", "hotfix/DEMO-101", "message", "Fix NPE in DiscountService")));
        Message finalMessage = buildTextMessage("Added a null check before calling discountCode.length().");

        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(readCall).thenReturn(writeCall)
                .thenReturn(commitCall).thenReturn(finalMessage);

        FixResponse result = fixService.fix("DEMO-101");

        assertThat(result.branchName()).isEqualTo("hotfix/DEMO-101");
        assertThat(result.commitSha()).isNotBlank();
        assertThat(result.patch()).contains("DiscountService.java");
        assertThat(result.steps()).hasSize(3);
        assertThat(result.iterations()).isEqualTo(4);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void fix_truncatesWhenIterationCapReached() {
        Message loopingReadCall = buildToolUseMessage("loop", "read_file",
                JsonValue.from(Map.of("path", "src/main/java/com/example/checkout/DiscountService.java")));
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(loopingReadCall);
        fixService = new FixService(anthropic, ticketSource, investigationLoop, new AgentProperties(2),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"), new SandboxProperties(true));

        FixResponse result = fixService.fix("DEMO-101");

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.branchName()).isNull();
    }

    // --- helpers (mirrors InvestigationLoopTest/AgentServiceTest) ---

    private Message buildTextMessage(String text) {
        TextBlock textBlock = TextBlock.builder().citations(Optional.empty()).text(text).build();
        return buildMessage(List.of(ContentBlock.ofText(textBlock)));
    }

    private Message buildToolUseMessage(String id, String name, JsonValue input) {
        ToolUseBlock toolUse = ToolUseBlock.builder().id(id).caller(DirectCaller.builder().build()).input(input)
                .name(name).build();
        return buildMessage(List.of(ContentBlock.ofToolUse(toolUse)));
    }

    private Message buildMessage(List<ContentBlock> blocks) {
        Usage usage = Usage.builder().cacheCreation(Optional.empty()).cacheCreationInputTokens(Optional.empty())
                .cacheReadInputTokens(Optional.empty()).inferenceGeo(Optional.empty()).inputTokens(0L)
                .outputTokens(0L).outputTokensDetails(Optional.empty()).serverToolUse(Optional.empty())
                .serviceTier(Optional.empty()).build();
        return Message.builder().id("msg_test").content(blocks).model("claude-sonnet-4-6")
                .stopDetails(Optional.empty()).stopReason(Optional.empty()).stopSequence(Optional.empty())
                .usage(usage).build();
    }
}

package com.testingai.sdlc.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.tool.QueryLogsTool;
import com.testingai.sdlc.tool.ToolExecutor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationLoopTest {

    private static final Ticket TICKET = new Ticket("DEMO-101", "Checkout fails with 500 error for some orders",
            "Intermittent failures reported.", "High", "checkout-service", Instant.parse("2026-07-10T10:00:00Z"));

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock
    private ToolExecutor toolExecutor;
    @Mock
    private QueryLogsTool queryLogsTool;

    private InvestigationLoop investigationLoop;

    @BeforeEach
    void setUp() {
        Tool stubTool = Tool.builder().name("query_logs").inputSchema(Tool.InputSchema.builder().build()).build();
        when(queryLogsTool.definition()).thenReturn(stubTool);
        investigationLoop = new InvestigationLoop(anthropic, toolExecutor, queryLogsTool, new AgentProperties(10),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));
    }

    @Test
    void investigate_singleIteration_returnsParsedHypothesis() {
        String json = """
                {"summary": "NPE in DiscountService", "evidence": ["line1"], "confidence": "high", "suspectedFiles": ["DiscountService.java"]}
                """;
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(buildTextMessage(json));

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.rootCause().summary()).isEqualTo("NPE in DiscountService");
        assertThat(result.rootCause().confidence()).isEqualTo("high");
        assertThat(result.rootCause().suspectedFiles()).containsExactly("DiscountService.java");
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void investigate_multiIteration_executesQueryLogsThenReturnsHypothesis() {
        Message toolCallResponse = buildToolUseMessage("tool_1", "query_logs",
                JsonValue.from(Map.of("service", "checkout-service", "keyword", "NullPointerException")));
        String json = """
                {"summary": "NPE", "evidence": [], "confidence": "medium", "suspectedFiles": []}
                """;
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(toolCallResponse)
                .thenReturn(buildTextMessage(json));
        when(toolExecutor.execute(eq("query_logs"), any())).thenReturn("[]");

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().tool()).isEqualTo("query_logs");
        assertThat(result.iterations()).isEqualTo(2);
    }

    @Test
    void investigate_truncatesWhenIterationCapReached() {
        Message loopingToolCall = buildToolUseMessage("tool_loop", "query_logs",
                JsonValue.from(Map.of("service", "checkout-service")));
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(loopingToolCall);
        when(toolExecutor.execute(any(), any())).thenReturn("[]");
        investigationLoop = new InvestigationLoop(anthropic, toolExecutor, queryLogsTool, new AgentProperties(2),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.rootCause().confidence()).isEqualTo("low");
    }

    @Test
    void investigate_fallsBackToLowConfidenceWhenFinalTextIsNotValidJson() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("I couldn't determine a root cause."));

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.rootCause().confidence()).isEqualTo("low");
        assertThat(result.rootCause().summary()).contains("couldn't determine");
    }

    @Test
    void investigate_stripsMarkdownCodeFenceBeforeParsing() {
        String fenced = """
                ```json
                {"summary": "NPE", "evidence": [], "confidence": "high", "suspectedFiles": []}
                ```
                """;
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(buildTextMessage(fenced));

        InvestigateResponse result = investigationLoop.investigate(TICKET);

        assertThat(result.rootCause().summary()).isEqualTo("NPE");
        assertThat(result.rootCause().confidence()).isEqualTo("high");
    }

    // --- helpers (unchanged from the old InvestigateServiceTest) ---

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

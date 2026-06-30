package com.testingai.reviewer;

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
import com.testingai.reviewer.config.AnthropicProperties;
import com.testingai.reviewer.config.ReviewerProperties;
import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.service.ReviewService;
import com.testingai.reviewer.tool.CheckstyleTool;
import com.testingai.reviewer.tool.PmdTool;
import com.testingai.reviewer.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock private ToolExecutor toolExecutor;
    @Mock private CheckstyleTool checkstyleTool;
    @Mock private PmdTool pmdTool;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        Tool stubTool = Tool.builder()
                .name("stub")
                .inputSchema(Tool.InputSchema.builder().build())
                .build();
        when(checkstyleTool.definition()).thenReturn(stubTool);
        when(pmdTool.definition()).thenReturn(stubTool);

        service = new ReviewService(
                anthropic, toolExecutor, checkstyleTool, pmdTool,
                new AnthropicProperties("test-key", "claude-sonnet-4-6"),
                new ReviewerProperties(5, System.getProperty("java.io.tmpdir")));
    }

    @Test
    void returnsEmptyFindingsWhenClaudeReturnsNoToolCalls() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage(""));

        ReviewResponse result = service.analyse("diff content");

        assertThat(result).isNotNull();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void parsesFindingsAndSummaryFromClaudeResponse() {
        String cannedJson = """
                [{"severity":"WARNING","file":"Foo.java","line":3,"message":"Too long","suggestion":"Split the method."}]
                1 warning found.""";
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage(cannedJson));

        ReviewResponse result = service.analyse("diff content");

        assertThat(result.findings()).hasSize(1);
        Finding f = result.findings().getFirst();
        assertThat(f.severity()).isEqualTo("WARNING");
        assertThat(f.file()).isEqualTo("Foo.java");
        assertThat(f.line()).isEqualTo(3);
        assertThat(f.message()).isEqualTo("Too long");
        assertThat(result.summary()).isEqualTo("1 warning found.");
    }

    @Test
    void executesToolCallAndReturnsFindings() {
        Message toolCallResponse = buildToolUseMessage(
                "tool_cs", "run_checkstyle",
                JsonValue.from(Map.of("diff", "diff content")));
        String finalJson = """
                [{"severity":"ERROR","file":"Bar.java","line":10,"message":"Bad style","suggestion":"Fix it."}]
                1 error found.""";
        Message finalResponse = buildTextMessage(finalJson);

        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);
        when(toolExecutor.execute(eq("run_checkstyle"), any()))
                .thenReturn("[{\"file\":\"Bar.java\",\"tool\":\"checkstyle\",\"rule\":\"R\",\"message\":\"Bad style\",\"line\":10}]");

        ReviewResponse result = service.analyse("diff content");

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().getFirst().severity()).isEqualTo("ERROR");
        assertThat(result.summary()).isEqualTo("1 error found.");
    }

    // --- helpers ---

    private Message buildTextMessage(String text) {
        TextBlock textBlock = TextBlock.builder()
                .citations(Optional.empty())
                .text(text)
                .build();
        return buildMessage(List.of(ContentBlock.ofText(textBlock)));
    }

    private Message buildToolUseMessage(String id, String name, JsonValue input) {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id(id)
                .caller(DirectCaller.builder().build())
                .input(input)
                .name(name)
                .build();
        return buildMessage(List.of(ContentBlock.ofToolUse(toolUse)));
    }

    private Message buildMessage(List<ContentBlock> blocks) {
        Usage usage = Usage.builder()
                .cacheCreation(Optional.empty())
                .cacheCreationInputTokens(Optional.empty())
                .cacheReadInputTokens(Optional.empty())
                .inferenceGeo(Optional.empty())
                .inputTokens(0L)
                .outputTokens(0L)
                .outputTokensDetails(Optional.empty())
                .serverToolUse(Optional.empty())
                .serviceTier(Optional.empty())
                .build();
        return Message.builder()
                .id("msg_test")
                .content(blocks)
                .model("claude-sonnet-4-6")
                .stopDetails(Optional.empty())
                .stopReason(Optional.empty())
                .stopSequence(Optional.empty())
                .usage(usage)
                .build();
    }
}

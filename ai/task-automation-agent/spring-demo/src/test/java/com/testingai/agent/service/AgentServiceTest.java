package com.testingai.agent.service;

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
import com.testingai.agent.config.AgentProperties;
import com.testingai.agent.config.AnthropicProperties;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.tool.FetchPageTool;
import com.testingai.agent.tool.ToolExecutor;
import com.testingai.agent.tool.WebSearchTool;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    // RETURNS_DEEP_STUBS allows chaining: anthropic.messages().create(any())
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock private ToolExecutor toolExecutor;
    @Mock private WebSearchTool webSearchTool;
    @Mock private FetchPageTool fetchPageTool;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        Tool stubTool = Tool.builder()
                .name("stub")
                .inputSchema(Tool.InputSchema.builder().build())
                .build();
        when(webSearchTool.definition()).thenReturn(stubTool);
        when(fetchPageTool.definition()).thenReturn(stubTool);
        agentService = new AgentService(
                anthropic, toolExecutor, webSearchTool, fetchPageTool,
                new AgentProperties(10, 4000),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));
    }

    @Test
    void run_singleIteration_noToolCalls_returnsAnswer() {
        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(buildTextMessage("Paris."));

        AgentResponse result = agentService.run("Capital of France?");

        assertThat(result.answer()).isEqualTo("Paris.");
        assertThat(result.steps()).isEmpty();
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void run_multiIteration_executesToolThenReturnsAnswer() {
        Message toolCallResponse = buildToolUseMessage(
                "tool_abc", "web_search",
                JsonValue.from(Map.of("query", "quantum news", "num_results", 5)));
        Message finalResponse = buildTextMessage("Quantum computing advances rapidly.");

        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);
        when(toolExecutor.execute(eq("web_search"), any()))
                .thenReturn("[{\"title\":\"Q News\",\"url\":\"http://q.com\",\"content\":\"...\"}]");

        AgentResponse result = agentService.run("Latest quantum computing news?");

        assertThat(result.answer()).isEqualTo("Quantum computing advances rapidly.");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().tool()).isEqualTo("web_search");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void run_truncatesWhenIterationCapReached() {
        Message loopingToolCall = buildToolUseMessage(
                "tool_loop", "web_search",
                JsonValue.from(Map.of("query", "test", "num_results", 5)));
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(loopingToolCall);
        when(toolExecutor.execute(any(), any())).thenReturn("[]");

        // Override with maxIterations = 2
        agentService = new AgentService(
                anthropic, toolExecutor, webSearchTool, fetchPageTool,
                new AgentProperties(2, 4000),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));

        AgentResponse result = agentService.run("Loop forever");

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
    }

    // --- helpers ---

    private Message buildTextMessage(String text) {
        TextBlock textBlock = TextBlock.builder()
                .citations(Optional.empty())
                .text(text)
                .build();
        ContentBlock block = ContentBlock.ofText(textBlock);
        return buildMessage(List.of(block));
    }

    private Message buildToolUseMessage(String id, String name, JsonValue input) {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id(id)
                .caller(DirectCaller.builder().build())
                .input(input)
                .name(name)
                .build();
        ContentBlock block = ContentBlock.ofToolUse(toolUse);
        return buildMessage(List.of(block));
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

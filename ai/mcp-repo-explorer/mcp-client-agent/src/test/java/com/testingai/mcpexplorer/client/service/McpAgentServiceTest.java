package com.testingai.mcpexplorer.client.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.mcpexplorer.client.config.AgentProperties;
import com.testingai.mcpexplorer.client.config.AnthropicProperties;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpAgentServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AnthropicClient anthropic;

    @Mock
    private McpSyncClient mcpClient;

    private McpAgentService agentService;

    @BeforeEach
    void setUp() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null);
        McpSchema.Tool stubTool = McpSchema.Tool.builder()
                .name("list_modules")
                .description("stub")
                .inputSchema(schema)
                .build();
        when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(stubTool), null));

        agentService = new McpAgentService(
                anthropic, mcpClient,
                new AgentProperties(10),
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
    void run_multiIteration_callsMcpToolThenReturnsAnswer() {
        Message toolCallResponse = buildToolUseMessage("tool_abc", "list_modules", JsonValue.from(Map.of()));
        Message finalResponse = buildTextMessage("There are 3 modules.");

        when(anthropic.messages().create(any(MessageCreateParams.class)))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);
        when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(McpSchema.CallToolResult.builder()
                        .addTextContent("[{\"category\":\"ai\",\"module\":\"code-review-agent\"}]")
                        .build());

        AgentResponse result = agentService.run("How many modules are there?");

        assertThat(result.answer()).isEqualTo("There are 3 modules.");
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().tool()).isEqualTo("list_modules");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void run_truncatesWhenIterationCapReached() {
        Message loopingToolCall = buildToolUseMessage("tool_loop", "list_modules", JsonValue.from(Map.of()));
        when(anthropic.messages().create(any(MessageCreateParams.class))).thenReturn(loopingToolCall);
        when(mcpClient.callTool(any(McpSchema.CallToolRequest.class)))
                .thenReturn(McpSchema.CallToolResult.builder().addTextContent("[]").build());

        agentService = new McpAgentService(
                anthropic, mcpClient,
                new AgentProperties(2),
                new AnthropicProperties("test-key", "claude-sonnet-4-6"));

        AgentResponse result = agentService.run("Loop forever");

        assertThat(result.truncated()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
    }

    private Message buildTextMessage(String text) {
        TextBlock textBlock = TextBlock.builder().citations(Optional.empty()).text(text).build();
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

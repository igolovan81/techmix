package com.testingai.mcpexplorer.client.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.testingai.mcpexplorer.client.config.AgentProperties;
import com.testingai.mcpexplorer.client.config.AnthropicProperties;
import com.testingai.mcpexplorer.client.model.AgentResponse;
import com.testingai.mcpexplorer.client.model.StepRecord;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class McpAgentService {

    private final AnthropicClient anthropic;
    private final McpSyncClient mcpClient;
    private final AgentProperties agentProps;
    private final AnthropicProperties anthropicProps;

    public McpAgentService(AnthropicClient anthropic,
                            McpSyncClient mcpClient,
                            AgentProperties agentProps,
                            AnthropicProperties anthropicProps) {
        this.anthropic = anthropic;
        this.mcpClient = mcpClient;
        this.agentProps = agentProps;
        this.anthropicProps = anthropicProps;
    }

    public AgentResponse run(String goal) {
        List<Tool> anthropicTools = mcpClient.listTools().tools().stream()
                .map(this::toAnthropicTool)
                .toList();

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(goal).build());

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProps.maxIterations()) {
            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .model(anthropicProps.model())
                    .maxTokens(4096)
                    .messages(messages)
                    .toolChoice(ToolChoiceAuto.builder().build());
            anthropicTools.forEach(paramsBuilder::addTool);

            Message response = anthropic.messages().create(paramsBuilder.build());

            List<ContentBlockParam> assistantBlocks = response.content().stream()
                    .map(ContentBlock::toParam)
                    .filter(Objects::nonNull)
                    .toList();
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantBlocks)
                    .build());

            iterations++;

            List<ToolUseBlock> toolCalls = response.content().stream()
                    .filter(ContentBlock::isToolUse)
                    .map(ContentBlock::asToolUse)
                    .toList();

            if (toolCalls.isEmpty()) {
                String answer = response.content().stream()
                        .filter(ContentBlock::isText)
                        .map(ContentBlock::asText)
                        .map(TextBlock::text)
                        .collect(Collectors.joining(""));
                return new AgentResponse(answer, steps, iterations, false);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ToolUseBlock call : toolCalls) {
                Map<String, Object> args = call._input().convert(new TypeReference<Map<String, Object>>() {});
                if (args == null) {
                    args = Map.of();
                }
                McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(call.name(), args));
                String output = extractText(result);
                steps.add(new StepRecord(call.name(), args.toString(), output));
                toolResults.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(call.id())
                                .content(output)
                                .build()));
            }
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }

        return new AgentResponse("", steps, iterations, true);
    }

    private Tool toAnthropicTool(McpSchema.Tool mcpTool) {
        McpSchema.JsonSchema schema = mcpTool.inputSchema();
        Tool.InputSchema.Properties.Builder propsBuilder = Tool.InputSchema.Properties.builder();
        if (schema.properties() != null) {
            schema.properties().forEach((name, def) -> propsBuilder.putAdditionalProperty(name, JsonValue.from(def)));
        }
        Tool.InputSchema.Builder inputSchemaBuilder = Tool.InputSchema.builder().properties(propsBuilder.build());
        if (schema.required() != null) {
            inputSchemaBuilder.required(schema.required());
        }
        return Tool.builder()
                .name(mcpTool.name())
                .description(mcpTool.description() == null ? "" : mcpTool.description())
                .inputSchema(inputSchemaBuilder.build())
                .build();
    }

    private String extractText(McpSchema.CallToolResult result) {
        List<String> texts = new ArrayList<>();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                texts.add(textContent.text());
            }
        }
        return String.join("\n", texts);
    }
}

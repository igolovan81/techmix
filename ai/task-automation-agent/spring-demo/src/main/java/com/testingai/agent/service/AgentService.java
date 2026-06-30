package com.testingai.agent.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.testingai.agent.config.AgentProperties;
import com.testingai.agent.config.AnthropicProperties;
import com.testingai.agent.model.AgentResponse;
import com.testingai.agent.model.StepRecord;
import com.testingai.agent.tool.FetchPageTool;
import com.testingai.agent.tool.ToolExecutor;
import com.testingai.agent.tool.WebSearchTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private final AnthropicClient anthropic;
    private final ToolExecutor toolExecutor;
    private final WebSearchTool webSearchTool;
    private final FetchPageTool fetchPageTool;
    private final AgentProperties agentProps;
    private final AnthropicProperties anthropicProps;

    public AgentService(AnthropicClient anthropic,
                        ToolExecutor toolExecutor,
                        WebSearchTool webSearchTool,
                        FetchPageTool fetchPageTool,
                        AgentProperties agentProps,
                        AnthropicProperties anthropicProps) {
        this.anthropic = anthropic;
        this.toolExecutor = toolExecutor;
        this.webSearchTool = webSearchTool;
        this.fetchPageTool = fetchPageTool;
        this.agentProps = agentProps;
        this.anthropicProps = anthropicProps;
    }

    public AgentResponse run(String goal) {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(goal)
                .build());

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProps.maxIterations()) {
            Message response = anthropic.messages().create(
                    MessageCreateParams.builder()
                            .model(anthropicProps.model())
                            .maxTokens(4096)
                            .messages(messages)
                            .addTool(webSearchTool.definition())
                            .addTool(fetchPageTool.definition())
                            .toolChoice(ToolChoiceAuto.builder().build())
                            .build());

            // Add assistant response to history; toParam() may return null for mocked/unknown block types
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
                String output = toolExecutor.execute(call.name(), call._input());
                steps.add(new StepRecord(call.name(), call._input().toString(), output));
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
}

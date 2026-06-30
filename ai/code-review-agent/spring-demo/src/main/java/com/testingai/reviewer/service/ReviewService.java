package com.testingai.reviewer.service;

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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.reviewer.config.AnthropicProperties;
import com.testingai.reviewer.config.ReviewerProperties;
import com.testingai.reviewer.model.Finding;
import com.testingai.reviewer.model.ReviewResponse;
import com.testingai.reviewer.tool.CheckstyleTool;
import com.testingai.reviewer.tool.PmdTool;
import com.testingai.reviewer.tool.ToolExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private static final String SYSTEM_PROMPT = """
            You are a Java code reviewer. The unified diff below shows only the changed lines.
            Call run_checkstyle and run_pmd with the diff to get static analysis findings.
            Then synthesise ALL findings (deduplicated) into a JSON array:
            [{"severity":"ERROR|WARNING|INFO","file":"...","line":N,"message":"...","suggestion":"..."}]
            where suggestion is a concrete fix. Follow with a one-sentence summary on a new line.
            <diff>
            %s
            </diff>
            """;

    private final AnthropicClient anthropic;
    private final ToolExecutor toolExecutor;
    private final CheckstyleTool checkstyleTool;
    private final PmdTool pmdTool;
    private final AnthropicProperties anthropicProps;
    private final ReviewerProperties reviewerProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewService(AnthropicClient anthropic, ToolExecutor toolExecutor,
                         CheckstyleTool checkstyleTool, PmdTool pmdTool,
                         AnthropicProperties anthropicProps, ReviewerProperties reviewerProps) {
        this.anthropic = anthropic;
        this.toolExecutor = toolExecutor;
        this.checkstyleTool = checkstyleTool;
        this.pmdTool = pmdTool;
        this.anthropicProps = anthropicProps;
        this.reviewerProps = reviewerProps;
    }

    public ReviewResponse analyse(String diff) {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(SYSTEM_PROMPT.formatted(diff))
                .build());

        int iterations = 0;

        while (iterations < reviewerProps.maxIterations()) {
            Message response = anthropic.messages().create(
                    MessageCreateParams.builder()
                            .model(anthropicProps.model())
                            .maxTokens(4096)
                            .messages(messages)
                            .addTool(checkstyleTool.definition())
                            .addTool(pmdTool.definition())
                            .toolChoice(ToolChoiceAuto.builder().build())
                            .build());

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
                String text = response.content().stream()
                        .filter(ContentBlock::isText)
                        .map(ContentBlock::asText)
                        .map(TextBlock::text)
                        .collect(Collectors.joining(""));
                return parseResponse(text);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ToolUseBlock call : toolCalls) {
                String output = toolExecutor.execute(call.name(), call._input());
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

        return new ReviewResponse(List.of(), "Iteration cap reached without synthesis.");
    }

    private ReviewResponse parseResponse(String text) {
        try {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) {
                return new ReviewResponse(List.of(), text.trim());
            }
            String json = text.substring(start, end + 1);
            String summary = text.substring(end + 1).trim();
            List<Finding> findings = objectMapper.readValue(json, new TypeReference<>() {});
            return new ReviewResponse(findings, summary.isEmpty() ? "Analysis complete." : summary);
        } catch (Exception e) {
            return new ReviewResponse(List.of(), text.trim());
        }
    }
}

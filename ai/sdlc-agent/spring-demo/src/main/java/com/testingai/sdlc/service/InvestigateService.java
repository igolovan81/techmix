package com.testingai.sdlc.service;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.model.StepRecord;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import com.testingai.sdlc.tool.QueryLogsTool;
import com.testingai.sdlc.tool.ToolExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InvestigateService {

    private static final int MAX_TOKENS = 4096;
    private static final String INSTRUCTIONS = """
            You are investigating a production support ticket. Use the query_logs tool to search \
            production logs for evidence related to the ticket. You may call query_logs multiple \
            times - for example, a broad keyword search first, then a follow-up scoped to a \
            correlationId you spot in a promising result.

            Once you have enough evidence, respond with ONLY a JSON object (no other text, no \
            markdown code fences) matching this exact shape:
            {"summary": "...", "evidence": ["...matching log lines..."], "confidence": "high|medium|low", "suspectedFiles": ["..."]}""";

    private final AnthropicClient anthropic;
    private final TicketSource ticketSource;
    private final ToolExecutor toolExecutor;
    private final QueryLogsTool queryLogsTool;
    private final AgentProperties agentProperties;
    private final AnthropicProperties anthropicProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvestigateService(AnthropicClient anthropic, TicketSource ticketSource, ToolExecutor toolExecutor,
            QueryLogsTool queryLogsTool, AgentProperties agentProperties, AnthropicProperties anthropicProperties) {
        this.anthropic = anthropic;
        this.ticketSource = ticketSource;
        this.toolExecutor = toolExecutor;
        this.queryLogsTool = queryLogsTool;
        this.agentProperties = agentProperties;
        this.anthropicProperties = anthropicProperties;
    }

    public InvestigateResponse investigate(String ticketId) {
        Ticket ticket = ticketSource.fetch(ticketId);

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(buildInitialPrompt(ticket))
                .build());

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProperties.maxIterations()) {
            Message response = anthropic.messages().create(MessageCreateParams.builder()
                    .model(anthropicProperties.model()).maxTokens(MAX_TOKENS).messages(messages)
                    .addTool(queryLogsTool.definition()).toolChoice(ToolChoiceAuto.builder().build()).build());

            List<ContentBlockParam> assistantBlocks = response.content().stream().map(ContentBlock::toParam)
                    .filter(Objects::nonNull).toList();
            messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantBlocks).build());

            iterations++;

            List<ToolUseBlock> toolCalls = response.content().stream().filter(ContentBlock::isToolUse)
                    .map(ContentBlock::asToolUse).toList();

            if (toolCalls.isEmpty()) {
                String text = response.content().stream().filter(ContentBlock::isText).map(ContentBlock::asText)
                        .map(TextBlock::text).collect(Collectors.joining(""));
                return new InvestigateResponse(parseRootCause(text), iterations, steps, false);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ToolUseBlock call : toolCalls) {
                String output = toolExecutor.execute(call.name(), call._input());
                steps.add(new StepRecord(call.name(), call._input().toString(), output));
                toolResults.add(ContentBlockParam
                        .ofToolResult(ToolResultBlockParam.builder().toolUseId(call.id()).content(output).build()));
            }
            messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(toolResults)
                    .build());
        }

        return new InvestigateResponse(truncatedHypothesis(), iterations, steps, true);
    }

    private String buildInitialPrompt(Ticket ticket) {
        return INSTRUCTIONS + "\n\nTicket " + ticket.id() + " (" + ticket.service() + ", severity "
                + ticket.severity() + ", reported " + ticket.reportedAt() + "): " + ticket.title() + "\n"
                + ticket.description();
    }

    private RootCauseHypothesis parseRootCause(String text) {
        try {
            return objectMapper.readValue(stripCodeFence(text), RootCauseHypothesis.class);
        } catch (JsonProcessingException e) {
            return new RootCauseHypothesis(text, List.of(), "low", List.of());
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline == -1 || lastFence <= firstNewline) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, lastFence).trim();
    }

    private RootCauseHypothesis truncatedHypothesis() {
        return new RootCauseHypothesis("Investigation truncated: iteration limit reached before a conclusion.",
                List.of(), "low", List.of());
    }
}

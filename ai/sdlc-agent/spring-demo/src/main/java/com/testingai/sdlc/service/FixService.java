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
import com.testingai.sdlc.config.AgentProperties;
import com.testingai.sdlc.config.AnthropicProperties;
import com.testingai.sdlc.config.SandboxProperties;
import com.testingai.sdlc.model.FixResponse;
import com.testingai.sdlc.model.InvestigateResponse;
import com.testingai.sdlc.model.RootCauseHypothesis;
import com.testingai.sdlc.model.StepRecord;
import com.testingai.sdlc.sandbox.SandboxRepo;
import com.testingai.sdlc.ticket.Ticket;
import com.testingai.sdlc.ticket.TicketSource;
import com.testingai.sdlc.tool.FixToolDefinitions;
import com.testingai.sdlc.tool.FixToolExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FixService {

    private static final int MAX_TOKENS = 4096;
    private static final String INSTRUCTIONS = """
            You are fixing a production bug in a sandbox git repository, based on a root-cause \
            investigation. Use list_files and read_file to inspect the repository, write_file to \
            apply your fix, and finish by calling git_commit_branch exactly once with a branch \
            name like hotfix/<TICKET-ID> and a clear commit message. After committing, respond \
            with a short plain-text summary of what you changed and why.""";

    private final AnthropicClient anthropic;
    private final TicketSource ticketSource;
    private final InvestigationLoop investigationLoop;
    private final AgentProperties agentProperties;
    private final AnthropicProperties anthropicProperties;
    private final SandboxProperties sandboxProperties;

    public FixService(AnthropicClient anthropic, TicketSource ticketSource, InvestigationLoop investigationLoop,
            AgentProperties agentProperties, AnthropicProperties anthropicProperties,
            SandboxProperties sandboxProperties) {
        this.anthropic = anthropic;
        this.ticketSource = ticketSource;
        this.investigationLoop = investigationLoop;
        this.agentProperties = agentProperties;
        this.anthropicProperties = anthropicProperties;
        this.sandboxProperties = sandboxProperties;
    }

    public FixResponse fix(String ticketId) {
        Ticket ticket = ticketSource.fetch(ticketId);
        InvestigateResponse investigation = investigationLoop.investigate(ticket);
        RootCauseHypothesis rootCause = investigation.rootCause();

        SandboxRepo sandbox = SandboxRepo.create();
        try {
            return runFixLoop(rootCause, sandbox);
        } finally {
            if (sandboxProperties.cleanup()) {
                sandbox.cleanup();
            }
        }
    }

    private FixResponse runFixLoop(RootCauseHypothesis rootCause, SandboxRepo sandbox) {
        FixToolExecutor toolExecutor = new FixToolExecutor(sandbox);

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(buildInitialPrompt(rootCause))
                .build());

        List<StepRecord> steps = new ArrayList<>();
        int iterations = 0;

        while (iterations < agentProperties.maxIterations()) {
            Message response = anthropic.messages().create(MessageCreateParams.builder()
                    .model(anthropicProperties.model()).maxTokens(MAX_TOKENS).messages(messages)
                    .addTool(FixToolDefinitions.readFile()).addTool(FixToolDefinitions.listFiles())
                    .addTool(FixToolDefinitions.writeFile()).addTool(FixToolDefinitions.gitCommitBranch())
                    .toolChoice(ToolChoiceAuto.builder().build()).build());

            List<ContentBlockParam> assistantBlocks = response.content().stream().map(ContentBlock::toParam)
                    .filter(Objects::nonNull).toList();
            messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(assistantBlocks).build());

            iterations++;

            List<ToolUseBlock> toolCalls = response.content().stream().filter(ContentBlock::isToolUse)
                    .map(ContentBlock::asToolUse).toList();

            if (toolCalls.isEmpty()) {
                String summary = response.content().stream().filter(ContentBlock::isText).map(ContentBlock::asText)
                        .map(TextBlock::text).collect(Collectors.joining(""));
                return buildResponse(rootCause, summary, sandbox, steps, iterations, false);
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

        return buildResponse(rootCause, "Fix loop truncated: iteration limit reached before a summary was produced.",
                sandbox, steps, iterations, true);
    }

    private String buildInitialPrompt(RootCauseHypothesis rootCause) {
        return INSTRUCTIONS + "\n\nRoot cause: " + rootCause.summary() + "\nSuspected files: "
                + rootCause.suspectedFiles() + "\nEvidence: " + rootCause.evidence();
    }

    private FixResponse buildResponse(RootCauseHypothesis rootCause, String summary, SandboxRepo sandbox,
            List<StepRecord> steps, int iterations, boolean truncated) {
        String patch = sandbox.diffAgainstInitialCommit();
        boolean committed = sandbox.hasCommitted();
        String branchName = committed ? sandbox.currentBranch() : null;
        String commitSha = committed ? sandbox.currentCommitSha() : null;
        return new FixResponse(rootCause, summary, patch, branchName, commitSha, iterations, steps, truncated);
    }
}

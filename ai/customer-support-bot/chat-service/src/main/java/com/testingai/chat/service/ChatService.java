package com.testingai.chat.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.testingai.chat.config.AnthropicProperties;
import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.SearchResult;
import com.testingai.chat.model.SessionState;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.repository.OutcomeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  private static final String PERSONA = """
      You are a helpful customer support agent. Answer questions clearly and concisely.
      When you have fully resolved the customer's issue, end your response with \
      "Is there anything else I can help you with?"
      """;

  private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

  private final AnthropicClient anthropic;
  private final KnowledgeBaseClient kbClient;
  private final EscalationPolicy escalationPolicy;
  private final OutcomeRepository outcomeRepository;
  private final ChatProperties chatProps;
  private final AnthropicProperties anthropicProps;

  public ChatService(
      AnthropicClient anthropic,
      KnowledgeBaseClient kbClient,
      EscalationPolicy escalationPolicy,
      OutcomeRepository outcomeRepository,
      ChatProperties chatProps,
      AnthropicProperties anthropicProps) {
    this.anthropic = anthropic;
    this.kbClient = kbClient;
    this.escalationPolicy = escalationPolicy;
    this.outcomeRepository = outcomeRepository;
    this.chatProps = chatProps;
    this.anthropicProps = anthropicProps;
  }

  public StartResponse startSession() {
    String sessionId = UUID.randomUUID().toString();
    sessions.put(sessionId, new SessionState(sessionId));
    return new StartResponse(sessionId);
  }

  public MessageResponse sendMessage(String sessionId, String userText) {
    SessionState session = requireOpenSession(sessionId);

    List<SearchResult> chunks;
    try {
      chunks = kbClient.search(userText, 3);
    } catch (Exception e) {
      log.warn("KB search failed for session {}: {}", sessionId, e.getMessage());
      chunks = List.of();
    }
    String systemPrompt = buildSystemPrompt(chunks);

    session.addMessage(
        MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content(userText)
            .build());

    var response = anthropic.messages().create(
        MessageCreateParams.builder()
            .model(anthropicProps.model())
            .maxTokens(1024)
            .system(systemPrompt)
            .messages(session.getHistory())
            .build());

    String reply = response.content().stream()
        .filter(ContentBlock::isText)
        .map(ContentBlock::asText)
        .map(TextBlock::text)
        .collect(Collectors.joining());

    List<ContentBlockParam> assistantBlocks = response.content().stream()
        .map(ContentBlock::toParam)
        .filter(Objects::nonNull)
        .toList();
    session.addMessage(
        MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .contentOfBlockParams(assistantBlocks)
            .build());
    session.incrementTurnCount();

    boolean escalated = false;
    ConversationOutcome newOutcome = escalationPolicy.evaluate(session, userText);
    if (newOutcome != ConversationOutcome.OPEN) {
      session.setOutcome(newOutcome);
      escalated = (newOutcome == ConversationOutcome.ESCALATED);
      outcomeRepository.save(session, Instant.now());
    } else if (reply.toLowerCase().contains(chatProps.resolutionPhrase().toLowerCase())) {
      session.setOutcome(ConversationOutcome.RESOLVED);
      outcomeRepository.save(session, Instant.now());
    }

    return new MessageResponse(reply, session.getOutcome(), escalated);
  }

  public void closeSession(String sessionId) {
    SessionState session = requireSession(sessionId);
    if (session.getOutcome() == ConversationOutcome.OPEN) {
      session.setOutcome(ConversationOutcome.ABANDONED);
      outcomeRepository.save(session, Instant.now());
    }
  }

  private SessionState requireSession(String sessionId) {
    SessionState session = sessions.get(sessionId);
    if (session == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
    }
    return session;
  }

  private SessionState requireOpenSession(String sessionId) {
    SessionState session = requireSession(sessionId);
    if (session.getOutcome() != ConversationOutcome.OPEN) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Session is closed: " + sessionId);
    }
    return session;
  }

  private String buildSystemPrompt(List<SearchResult> chunks) {
    if (chunks.isEmpty()) {
      return PERSONA;
    }
    var context = new StringBuilder("Relevant product information:\n");
    for (int i = 0; i < chunks.size(); i++) {
      context
          .append(i + 1).append(". ")
          .append(chunks.get(i).title()).append(": ")
          .append(chunks.get(i).content()).append("\n");
    }
    return PERSONA + "\n" + context;
  }
}

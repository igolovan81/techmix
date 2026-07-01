package com.testingai.chat.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.testingai.chat.config.AnthropicProperties;
import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.SearchResult;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.repository.OutcomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private AnthropicClient anthropic;

  @Mock private KnowledgeBaseClient kbClient;
  @Mock private OutcomeRepository outcomeRepository;

  private EscalationPolicy escalationPolicy;
  private ChatService chatService;

  @BeforeEach
  void setUp() {
    ChatProperties props = new ChatProperties(
        10,
        List.of("angry", "lawsuit"),
        "is there anything else",
        "http://localhost:8087");
    escalationPolicy = new EscalationPolicy(props);
    chatService = new ChatService(
        anthropic, kbClient, escalationPolicy, outcomeRepository,
        props,
        new AnthropicProperties("test-key", "claude-sonnet-4-6"));
  }

  @Test
  void startSession_returnsUniqueSessionId() {
    StartResponse r1 = chatService.startSession();
    StartResponse r2 = chatService.startSession();

    assertThat(r1.sessionId()).isNotBlank();
    assertThat(r1.sessionId()).isNotEqualTo(r2.sessionId());
  }

  @Test
  void sendMessage_returnsReplyAndOpenOutcome() {
    when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
    when(anthropic.messages().create(any(MessageCreateParams.class)))
        .thenReturn(buildTextMessage("I can help with that."));

    String sessionId = chatService.startSession().sessionId();
    MessageResponse response = chatService.sendMessage(sessionId, "Where is my order?");

    assertThat(response.reply()).isEqualTo("I can help with that.");
    assertThat(response.outcome()).isEqualTo(ConversationOutcome.OPEN);
    assertThat(response.escalated()).isFalse();
  }

  @Test
  void sendMessage_marksResolved_whenReplyContainsResolutionPhrase() {
    when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
    when(anthropic.messages().create(any(MessageCreateParams.class)))
        .thenReturn(buildTextMessage(
            "Your order ships tomorrow. Is there anything else I can help you with?"));

    String sessionId = chatService.startSession().sessionId();
    MessageResponse response = chatService.sendMessage(sessionId, "When does my order ship?");

    assertThat(response.outcome()).isEqualTo(ConversationOutcome.RESOLVED);
    verify(outcomeRepository).save(any(), any());
  }

  @Test
  void sendMessage_escalates_whenKeywordDetected() {
    when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
    when(anthropic.messages().create(any(MessageCreateParams.class)))
        .thenReturn(buildTextMessage("I understand your frustration."));

    String sessionId = chatService.startSession().sessionId();
    MessageResponse response = chatService.sendMessage(sessionId, "This is ANGRY customer complaint!");

    assertThat(response.outcome()).isEqualTo(ConversationOutcome.ESCALATED);
    assertThat(response.escalated()).isTrue();
    verify(outcomeRepository).save(any(), any());
  }

  @Test
  void sendMessage_injectsKbChunksIntoSystemPrompt() {
    when(kbClient.search(anyString(), anyInt()))
        .thenReturn(List.of(new SearchResult("Shipping", "Ships in 3 days", 0.9)));
    when(anthropic.messages().create(any(MessageCreateParams.class)))
        .thenReturn(buildTextMessage("Ships in 3 days."));

    String sessionId = chatService.startSession().sessionId();
    chatService.sendMessage(sessionId, "When does shipping happen?");

    verify(anthropic.messages()).create(
        argThat((MessageCreateParams params) ->
            params.system().toString().contains("Ships in 3 days")));
  }

  @Test
  void sendMessage_throws404_forUnknownSession() {
    assertThatThrownBy(() -> chatService.sendMessage("nonexistent", "hello"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void sendMessage_throws409_forClosedSession() {
    when(kbClient.search(anyString(), anyInt())).thenReturn(List.of());
    when(anthropic.messages().create(any(MessageCreateParams.class)))
        .thenReturn(buildTextMessage("Is there anything else I can help you with?"));

    String sessionId = chatService.startSession().sessionId();
    chatService.sendMessage(sessionId, "done");

    assertThatThrownBy(() -> chatService.sendMessage(sessionId, "another message"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void closeSession_marksAbandoned_whenOpen() {
    String sessionId = chatService.startSession().sessionId();
    chatService.closeSession(sessionId);

    verify(outcomeRepository).save(any(), any());
  }

  // --- helpers ---

  private Message buildTextMessage(String text) {
    TextBlock textBlock = TextBlock.builder()
        .citations(Optional.empty())
        .text(text)
        .build();
    return buildMessage(List.of(ContentBlock.ofText(textBlock)));
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

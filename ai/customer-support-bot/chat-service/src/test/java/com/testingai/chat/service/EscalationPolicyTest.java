package com.testingai.chat.service;

import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationPolicyTest {

  private EscalationPolicy policy;

  @BeforeEach
  void setUp() {
    ChatProperties props = new ChatProperties(
        3,
        List.of("angry", "lawsuit", "fraud"),
        "is there anything else",
        "http://localhost:8087");
    policy = new EscalationPolicy(props);
  }

  @Test
  void evaluate_returnsOpen_whenBelowThresholdAndNoKeyword() {
    SessionState session = new SessionState("s1");
    session.incrementTurnCount();

    ConversationOutcome result = policy.evaluate(session, "I need help with my order.");

    assertThat(result).isEqualTo(ConversationOutcome.OPEN);
  }

  @Test
  void evaluate_returnsEscalated_whenTurnCountReachesMax() {
    SessionState session = new SessionState("s1");
    session.incrementTurnCount();
    session.incrementTurnCount();
    session.incrementTurnCount(); // turnCount == 3 == maxTurns

    ConversationOutcome result = policy.evaluate(session, "still no answer");

    assertThat(result).isEqualTo(ConversationOutcome.ESCALATED);
  }

  @Test
  void evaluate_returnsEscalated_whenUserTextContainsKeyword() {
    SessionState session = new SessionState("s1");

    ConversationOutcome result = policy.evaluate(session, "This is FRAUD!");

    assertThat(result).isEqualTo(ConversationOutcome.ESCALATED);
  }

  @Test
  void evaluate_isCaseInsensitiveForKeyword() {
    SessionState session = new SessionState("s1");

    ConversationOutcome result = policy.evaluate(session, "I'm ANGRY about this.");

    assertThat(result).isEqualTo(ConversationOutcome.ESCALATED);
  }
}

package com.testingai.chat.model;

import com.anthropic.models.messages.MessageParam;
import lombok.Getter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
public class SessionState {

  private final String sessionId;
  private final List<MessageParam> history = new ArrayList<>();
  private final Instant startedAt = Instant.now();
  private int turnCount;
  private ConversationOutcome outcome = ConversationOutcome.OPEN;

  public SessionState(String sessionId) {
    this.sessionId = sessionId;
  }

  public void addMessage(MessageParam param) {
    history.add(param);
  }

  public void incrementTurnCount() {
    turnCount++;
  }

  public void setOutcome(ConversationOutcome outcome) {
    this.outcome = outcome;
  }
}

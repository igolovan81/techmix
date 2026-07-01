package com.testingai.chat.service;

import com.testingai.chat.config.ChatProperties;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.SessionState;
import org.springframework.stereotype.Component;

@Component
public class EscalationPolicy {

  private final ChatProperties props;

  public EscalationPolicy(ChatProperties props) {
    this.props = props;
  }

  public ConversationOutcome evaluate(SessionState session, String userText) {
    if (session.getTurnCount() >= props.maxTurns()) {
      return ConversationOutcome.ESCALATED;
    }
    String lower = userText.toLowerCase();
    for (String keyword : props.escalationKeywords()) {
      if (lower.contains(keyword.toLowerCase())) {
        return ConversationOutcome.ESCALATED;
      }
    }
    return ConversationOutcome.OPEN;
  }
}

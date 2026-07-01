package com.testingai.chat.model;

public record MessageResponse(String reply, ConversationOutcome outcome, boolean escalated) {}

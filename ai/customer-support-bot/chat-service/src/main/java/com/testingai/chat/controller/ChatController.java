package com.testingai.chat.controller;

import com.testingai.chat.model.MessageRequest;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @PostMapping("/start")
  public StartResponse start() {
    return chatService.startSession();
  }

  @PostMapping("/{sessionId}/message")
  public MessageResponse message(
      @PathVariable String sessionId,
      @Valid @RequestBody MessageRequest request) {
    return chatService.sendMessage(sessionId, request.text());
  }

  @PostMapping("/{sessionId}/close")
  public ResponseEntity<Void> close(@PathVariable String sessionId) {
    chatService.closeSession(sessionId);
    return ResponseEntity.noContent().build();
  }
}

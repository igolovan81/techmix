package com.testingai.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.MessageRequest;
import com.testingai.chat.model.MessageResponse;
import com.testingai.chat.model.StartResponse;
import com.testingai.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private ChatService chatService;

  @Test
  void start_returnsSessionId() throws Exception {
    when(chatService.startSession()).thenReturn(new StartResponse("sess-123"));

    mockMvc.perform(post("/api/chat/start"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("sess-123"));
  }

  @Test
  void message_returnsReply() throws Exception {
    when(chatService.sendMessage("sess-123", "hello"))
        .thenReturn(new MessageResponse("Hi there!", ConversationOutcome.OPEN, false));

    mockMvc.perform(post("/api/chat/sess-123/message")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new MessageRequest("hello"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("Hi there!"))
        .andExpect(jsonPath("$.outcome").value("OPEN"))
        .andExpect(jsonPath("$.escalated").value(false));
  }

  @Test
  void message_returns400_whenTextBlank() throws Exception {
    mockMvc.perform(post("/api/chat/sess-123/message")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void message_returns404_whenSessionUnknown() throws Exception {
    when(chatService.sendMessage("bad-id", "hi"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

    mockMvc.perform(post("/api/chat/bad-id/message")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"hi\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void message_returns409_whenSessionClosed() throws Exception {
    when(chatService.sendMessage("closed-id", "hi"))
        .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "closed"));

    mockMvc.perform(post("/api/chat/closed-id/message")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"hi\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void close_returns204() throws Exception {
    mockMvc.perform(post("/api/chat/sess-123/close"))
        .andExpect(status().isNoContent());

    verify(chatService).closeSession("sess-123");
  }
}

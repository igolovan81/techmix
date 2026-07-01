package com.testingai.chat;

import com.testingai.chat.config.AnthropicProperties;
import com.testingai.chat.config.ChatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AnthropicProperties.class, ChatProperties.class})
public class ChatApplication {
  public static void main(String[] args) {
    SpringApplication.run(ChatApplication.class, args);
  }
}

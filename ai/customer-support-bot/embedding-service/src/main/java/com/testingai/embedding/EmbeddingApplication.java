package com.testingai.embedding;

import com.testingai.embedding.config.OpenAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenAiProperties.class)
public class EmbeddingApplication {
  public static void main(String[] args) {
    SpringApplication.run(EmbeddingApplication.class, args);
  }
}

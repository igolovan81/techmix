package com.testingai;

import com.testingai.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EntityScan(basePackages = {"com.testingai.entity"})
@EnableJpaAuditing
public class TestingAiGenerationApplication {

  private static final Logger logger =
      LoggerFactory.getLogger(TestingAiGenerationApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(TestingAiGenerationApplication.class, args);
  }

  @Bean
  public CommandLineRunner logAllPosts(PostRepository postRepository) {
    return args -> {
      logger.info("Logging all posts from the database:");
      postRepository.findAll().forEach(post -> logger.info(post.toString()));
    };
  }
}

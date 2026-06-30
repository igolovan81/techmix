package com.testingai.reviewer;

import com.testingai.reviewer.config.AnthropicProperties;
import com.testingai.reviewer.config.GitHubProperties;
import com.testingai.reviewer.config.ReviewerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AnthropicProperties.class, ReviewerProperties.class, GitHubProperties.class})
public class ReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewApplication.class, args);
    }
}

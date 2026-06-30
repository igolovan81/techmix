package com.testingai.agent;

import com.testingai.agent.config.AgentProperties;
import com.testingai.agent.config.AnthropicProperties;
import com.testingai.agent.config.TavilyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, AnthropicProperties.class, TavilyProperties.class})
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}

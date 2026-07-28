package com.testingai.mcpexplorer.server.config;

import com.testingai.mcpexplorer.server.tool.RepoRootResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class RepoRootConfig {

    @Bean
    public Path repoRoot() {
        return RepoRootResolver.resolve();
    }
}

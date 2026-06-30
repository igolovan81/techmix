package com.testingai.reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("reviewer")
public record ReviewerProperties(int maxIterations, String tempDir) {}

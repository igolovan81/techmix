package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "splunk")
public record SplunkProperties(String baseUrl, String apiToken, int searchTimeoutSeconds, boolean trustSelfSigned) {
}

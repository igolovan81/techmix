package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zendesk")
public record ZendeskProperties(String subdomain, String email, String apiToken, String serviceTagPrefix) {
}

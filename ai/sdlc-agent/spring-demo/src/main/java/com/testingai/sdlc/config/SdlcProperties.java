package com.testingai.sdlc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sdlc")
public record SdlcProperties(String ticketSource) {
}

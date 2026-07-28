package com.testingai.reactor.streaming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

	@Bean
	public WebClient upstreamWebClient(@Value("${upstream.base-url}") String upstreamBaseUrl) {
		return WebClient.builder().baseUrl(upstreamBaseUrl).build();
	}
}

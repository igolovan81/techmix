package com.testingai.sdlc.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private final SdlcProperties sdlc;
    private final AnthropicProperties anthropic;
    private final JiraProperties jira;
    private final ZendeskProperties zendesk;
    private final SplunkProperties splunk;

    public AppConfig(SdlcProperties sdlc, AnthropicProperties anthropic, JiraProperties jira,
            ZendeskProperties zendesk, SplunkProperties splunk) {
        this.sdlc = sdlc;
        this.anthropic = anthropic;
        this.jira = jira;
        this.zendesk = zendesk;
        this.splunk = splunk;
    }

    @PostConstruct
    public void validateApiKeys() {
        require(anthropic.apiKey(), "ANTHROPIC_API_KEY");
        require(splunk.apiToken(), "SPLUNK_API_TOKEN");
        if ("zendesk".equalsIgnoreCase(sdlc.ticketSource())) {
            require(zendesk.apiToken(), "ZENDESK_API_TOKEN");
        } else {
            require(jira.apiToken(), "JIRA_API_TOKEN");
        }
    }

    private void require(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " environment variable is not set");
        }
    }

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder().apiKey(anthropic.apiKey()).build();
    }

    @Bean
    public RestClient jiraRestClient() {
        return RestClient.builder().baseUrl(jira.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(jira.email(), jira.apiToken())).build();
    }

    @Bean
    public RestClient zendeskRestClient() {
        return RestClient.builder().baseUrl("https://" + zendesk.subdomain() + ".zendesk.com")
                .defaultHeaders(headers -> headers.setBasicAuth(zendesk.email() + "/token", zendesk.apiToken()))
                .build();
    }

    @Bean
    public RestClient splunkRestClient() {
        return RestClient.builder().baseUrl(splunk.baseUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(splunk.apiToken())).build();
    }
}

package com.testingai.sdlc.ticket;

import com.testingai.sdlc.config.JiraProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sdlc.ticket-source", havingValue = "jira", matchIfMissing = true)
public class JiraTicketSource implements TicketSource {

    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final RestClient restClient;
    private final JiraProperties jiraProperties;

    public JiraTicketSource(RestClient jiraRestClient, JiraProperties jiraProperties) {
        this.restClient = jiraRestClient;
        this.jiraProperties = jiraProperties;
    }

    @Override
    public Ticket fetch(String ticketId) {
        Map<String, Object> response = fetchIssue(ticketId);
        Map<String, Object> fields = castMap(response.get("fields"));
        Map<String, Object> priority = castMap(fields.get("priority"));

        Object serviceValue = fields.get(jiraProperties.serviceField());
        String service = serviceValue != null ? serviceValue.toString() : "unknown";
        String severity = priority.get("name") != null ? priority.get("name").toString() : "unknown";

        return new Ticket(String.valueOf(response.get("key")), String.valueOf(fields.get("summary")),
                AdfTextExtractor.extractText(fields.get("description")), severity, service,
                OffsetDateTime.parse(String.valueOf(fields.get("created")), JIRA_TIMESTAMP_FORMAT).toInstant());
    }

    private Map<String, Object> fetchIssue(String ticketId) {
        try {
            Map<String, Object> response = restClient.get().uri("/rest/api/3/issue/{key}", ticketId).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId);
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId, e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch Jira ticket: " + ticketId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}

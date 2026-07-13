package com.testingai.sdlc.ticket;

import com.testingai.sdlc.config.ZendeskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sdlc.ticket-source", havingValue = "zendesk")
public class ZendeskTicketSource implements TicketSource {

    private final RestClient restClient;
    private final ZendeskProperties zendeskProperties;

    public ZendeskTicketSource(RestClient zendeskRestClient, ZendeskProperties zendeskProperties) {
        this.restClient = zendeskRestClient;
        this.zendeskProperties = zendeskProperties;
    }

    @Override
    public Ticket fetch(String ticketId) {
        Map<String, Object> response = fetchTicket(ticketId);
        Map<String, Object> ticket = castMap(response.get("ticket"));

        String priority = ticket.get("priority") != null ? ticket.get("priority").toString() : "unknown";
        String service = extractServiceTag((List<?>) ticket.get("tags"), zendeskProperties.serviceTagPrefix());

        return new Ticket(String.valueOf(ticket.get("id")), String.valueOf(ticket.get("subject")),
                String.valueOf(ticket.get("description")), priority, service,
                Instant.parse(String.valueOf(ticket.get("created_at"))));
    }

    private Map<String, Object> fetchTicket(String ticketId) {
        try {
            Map<String, Object> response = restClient.get().uri("/api/v2/tickets/{id}.json", ticketId).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId);
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + ticketId, e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch Zendesk ticket: " + ticketId,
                    e);
        }
    }

    private static String extractServiceTag(List<?> tags, String prefix) {
        if (tags == null) {
            return "unknown";
        }
        for (Object tag : tags) {
            String candidate = String.valueOf(tag);
            if (prefix == null || prefix.isBlank() || candidate.startsWith(prefix)) {
                return candidate;
            }
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}

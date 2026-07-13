package com.testingai.sdlc.log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testingai.sdlc.config.SplunkProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class SplunkLogSource implements LogSource {

    private static final long POLL_INTERVAL_MILLIS = 500;

    private final RestClient restClient;
    private final SplunkProperties splunkProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SplunkLogSource(RestClient splunkRestClient, SplunkProperties splunkProperties) {
        this.restClient = splunkRestClient;
        this.splunkProperties = splunkProperties;
    }

    @Override
    public List<LogEntry> query(String service, Instant from, Instant to, String keyword, String correlationId) {
        String sid = createSearchJob(buildSearchString(service, keyword, correlationId), from, to);
        if (!waitForCompletion(sid)) {
            return List.of();
        }
        return fetchResults(sid, service);
    }

    // Note: correlationId is searched as free text (not a field-equality match) because the
    // seeded HEC events don't get "correlationId" promoted to a top-level indexed/extracted
    // Splunk field automatically — the only reliably queryable representation is the raw JSON
    // event body itself, which a plain quoted-string search matches against via full-text search.
    private String buildSearchString(String service, String keyword, String correlationId) {
        StringBuilder search = new StringBuilder("search index=main service=\"").append(service).append('"');
        if (keyword != null && !keyword.isBlank()) {
            search.append(" \"").append(keyword).append('"');
        }
        if (correlationId != null && !correlationId.isBlank()) {
            search.append(" \"").append(correlationId).append('"');
        }
        return search.toString();
    }

    private String createSearchJob(String search, Instant from, Instant to) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("search", search);
        form.add("earliest_time", from.toString());
        form.add("latest_time", to.toString());
        form.add("output_mode", "json");
        CreateJobResponse response = restClient.post().uri("/services/search/jobs")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                .body(CreateJobResponse.class);
        return response != null ? response.sid() : "";
    }

    private boolean waitForCompletion(String sid) {
        long deadline = System.currentTimeMillis() + splunkProperties.searchTimeoutSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            JobStatusResponse status = restClient.get().uri("/services/search/jobs/{sid}?output_mode=json", sid)
                    .retrieve().body(JobStatusResponse.class);
            if (status != null && !status.entry().isEmpty()
                    && "DONE".equals(status.entry().getFirst().content().dispatchState())) {
                return true;
            }
            sleep();
        }
        return false;
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<LogEntry> fetchResults(String sid, String service) {
        ResultsResponse response = restClient.get().uri("/services/search/jobs/{sid}/results?output_mode=json", sid)
                .retrieve().body(ResultsResponse.class);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream().map(r -> toLogEntry(r, service)).toList();
    }

    // Parses the seeded JSON payload back out of Splunk's _raw field, since Splunk does not
    // automatically promote arbitrary top-level JSON keys (level, correlationId) to searchable
    // result fields without explicit index-time field extraction configuration.
    private LogEntry toLogEntry(SplunkResult result, String service) {
        Map<String, Object> raw = parseRawEvent(result.raw());
        String level = raw.get("level") != null ? raw.get("level").toString() : "";
        String message = raw.get("message") != null ? raw.get("message").toString() : result.raw();
        String correlationId = raw.get("correlationId") != null ? raw.get("correlationId").toString() : null;
        return new LogEntry(Instant.parse(result.time()), service, level, message, correlationId);
    }

    private Map<String, Object> parseRawEvent(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    record CreateJobResponse(String sid) {
    }

    record JobStatusResponse(List<JobEntry> entry) {
    }

    record JobEntry(JobContent content) {
    }

    record JobContent(String dispatchState) {
    }

    record ResultsResponse(List<SplunkResult> results) {
    }

    record SplunkResult(@JsonProperty("_time") String time, @JsonProperty("_raw") String raw) {
    }
}

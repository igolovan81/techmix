package com.testingai.sdlc.log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.testingai.sdlc.config.SplunkProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

@Component
public class SplunkLogSource implements LogSource {

    private static final long POLL_INTERVAL_MILLIS = 500;

    private final RestClient restClient;
    private final SplunkProperties splunkProperties;

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

    private String buildSearchString(String service, String keyword, String correlationId) {
        StringBuilder search = new StringBuilder("search index=main service=\"").append(service).append('"');
        if (keyword != null && !keyword.isBlank()) {
            search.append(" \"").append(keyword).append('"');
        }
        if (correlationId != null && !correlationId.isBlank()) {
            search.append(" correlationId=\"").append(correlationId).append('"');
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
        return response.results().stream()
                .map(r -> new LogEntry(Instant.parse(r.time()), service, r.level(), r.raw(), r.correlationId()))
                .toList();
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

    record SplunkResult(@JsonProperty("_time") String time, @JsonProperty("_raw") String raw, String level,
            String correlationId) {
    }
}

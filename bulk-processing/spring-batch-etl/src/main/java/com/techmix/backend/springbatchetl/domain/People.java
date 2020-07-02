package com.techmix.backend.springbatchetl.domain;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "family"
})
public class People {

    @JsonProperty("family")
    private List<String> family = null;

    @JsonProperty("family")
    public List<String> getFamily() {
        return family;
    }

    @JsonProperty("family")
    public void setFamily(List<String> family) {
        this.family = family;
    }

    @Override
    public String toString() {
        return "People{" +
                "family=" + family +
                '}';
    }
}

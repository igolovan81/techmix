package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "corrected",
        "originals"
})
public class Documents {

    @JsonProperty("corrected")
    private List<Object> corrected = null;
    @JsonProperty("originals")
    private List<Object> originals = null;

    @JsonProperty("corrected")
    public List<Object> getCorrected() {
        return corrected;
    }

    @JsonProperty("corrected")
    public void setCorrected(List<Object> corrected) {
        this.corrected = corrected;
    }

    @JsonProperty("originals")
    public List<Object> getOriginals() {
        return originals;
    }

    @JsonProperty("originals")
    public void setOriginals(List<Object> originals) {
        this.originals = originals;
    }

}

package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "empty"
})
public class Step17 {

    @JsonProperty("empty")
    private String empty;

    @JsonProperty("empty")
    public String getEmpty() {
        return empty;
    }

    @JsonProperty("empty")
    public void setEmpty(String empty) {
        this.empty = empty;
    }

}

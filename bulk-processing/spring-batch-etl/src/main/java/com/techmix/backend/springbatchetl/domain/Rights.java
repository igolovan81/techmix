package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "1520358377459"
})
public class Rights {

    @JsonProperty("1520358377459")
    private RightsDetails rightsDetails;

    @JsonProperty("1520358377459")
    public RightsDetails get1520358377459() {
        return rightsDetails;
    }

    @JsonProperty("1520358377459")
    public void set1520358377459(RightsDetails rightsDetails) {
        this.rightsDetails = rightsDetails;
    }

}

package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "1585247482512"
})
public class Step6 {

    @JsonProperty("1585247482512")
    private Step6Details step6Details;

    @JsonProperty("1585247482512")
    public Step6Details getStep6Details() {
        return step6Details;
    }

    @JsonProperty("empty")
    public void setStep6Details(Step6Details step6Details) {
        this.step6Details = step6Details;
    }

}

package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "step_0",
        "step_1",
        "step_11",
        "step_2",
        "step_3",
        "step_17"
})
public class UnifiedSource {

    @JsonProperty("step_0")
    private Step0 step0;
    @JsonProperty("step_1")
    private Step1 step1;
    @JsonProperty("step_17")
    private Step17 step17;

    @JsonProperty("step_0")
    public Step0 getStep0() {
        return step0;
    }

    @JsonProperty("step_0")
    public void setStep0(Step0 step0) {
        this.step0 = step0;
    }

    @JsonProperty("step_1")
    public Step1 getStep1() {
        return step1;
    }

    @JsonProperty("step_1")
    public void setStep1(Step1 step1) {
        this.step1 = step1;
    }

    @JsonProperty("step_17")
    public Step17 getStep17() {
        return step17;
    }

    @JsonProperty("step_17")
    public void setStep17(Step17 step17) {
        this.step17 = step17;
    }

}

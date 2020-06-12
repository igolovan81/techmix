package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "declarationType",
        "declarationYear1"
})
public class Step0 {

    @JsonProperty("declarationType")
    private String declarationType;
    @JsonProperty("declarationYear1")
    private String declarationYear1;

    @JsonProperty("declarationType")
    public String getDeclarationType() {
        return declarationType;
    }

    @JsonProperty("declarationType")
    public void setDeclarationType(String declarationType) {
        this.declarationType = declarationType;
    }

    @JsonProperty("declarationYear1")
    public String getDeclarationYear1() {
        return declarationYear1;
    }

    @JsonProperty("declarationYear1")
    public void setDeclarationYear1(String declarationYear1) {
        this.declarationYear1 = declarationYear1;
    }

}

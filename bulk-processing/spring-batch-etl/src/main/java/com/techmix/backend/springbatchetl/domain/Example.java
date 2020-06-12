package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "infocard",
        "raw_source",
        "unified_source",
        "related_entities"
})
public class Example {

    @JsonProperty("infocard")
    private Infocard infocard;
    @JsonProperty("raw_source")
    private RawSource rawSource;
    @JsonProperty("unified_source")
    private UnifiedSource unifiedSource;
    @JsonProperty("related_entities")
    private RelatedEntities relatedEntities;

    @JsonProperty("infocard")
    public Infocard getInfocard() {
        return infocard;
    }

    @JsonProperty("infocard")
    public void setInfocard(Infocard infocard) {
        this.infocard = infocard;
    }

    @JsonProperty("raw_source")
    public RawSource getRawSource() {
        return rawSource;
    }

    @JsonProperty("raw_source")
    public void setRawSource(RawSource rawSource) {
        this.rawSource = rawSource;
    }

    @JsonProperty("unified_source")
    public UnifiedSource getUnifiedSource() {
        return unifiedSource;
    }

    @JsonProperty("unified_source")
    public void setUnifiedSource(UnifiedSource unifiedSource) {
        this.unifiedSource = unifiedSource;
    }

    @JsonProperty("related_entities")
    public RelatedEntities getRelatedEntities() {
        return relatedEntities;
    }

    @JsonProperty("related_entities")
    public void setRelatedEntities(RelatedEntities relatedEntities) {
        this.relatedEntities = relatedEntities;
    }

}

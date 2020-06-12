package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "owned",
        "related",
        "all"
})
public class Companies {

    @JsonProperty("owned")
    private List<Object> owned = null;
    @JsonProperty("related")
    private List<String> related = null;
    @JsonProperty("all")
    private List<String> all = null;

    @JsonProperty("owned")
    public List<Object> getOwned() {
        return owned;
    }

    @JsonProperty("owned")
    public void setOwned(List<Object> owned) {
        this.owned = owned;
    }

    @JsonProperty("related")
    public List<String> getRelated() {
        return related;
    }

    @JsonProperty("related")
    public void setRelated(List<String> related) {
        this.related = related;
    }

    @JsonProperty("all")
    public List<String> getAll() {
        return all;
    }

    @JsonProperty("all")
    public void setAll(List<String> all) {
        this.all = all;
    }

}

package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "people",
        "documents",
        "companies"
})
public class RelatedEntities {

    @JsonProperty("people")
    private People people;
    @JsonProperty("documents")
    private Documents documents;
    @JsonProperty("companies")
    private Companies companies;

    @JsonProperty("people")
    public People getPeople() {
        return people;
    }

    @JsonProperty("people")
    public void setPeople(People people) {
        this.people = people;
    }

    @JsonProperty("documents")
    public Documents getDocuments() {
        return documents;
    }

    @JsonProperty("documents")
    public void setDocuments(Documents documents) {
        this.documents = documents;
    }

    @JsonProperty("companies")
    public Companies getCompanies() {
        return companies;
    }

    @JsonProperty("companies")
    public void setCompanies(Companies companies) {
        this.companies = companies;
    }

    @Override
    public String toString() {
        return "RelatedEntities{" +
                "people=" + people +
                ", documents=" + documents +
                ", companies=" + companies +
                '}';
    }
}

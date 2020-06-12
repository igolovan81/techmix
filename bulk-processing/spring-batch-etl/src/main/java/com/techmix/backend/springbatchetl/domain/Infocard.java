package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "first_name",
        "patronymic",
        "last_name",
        "office",
        "position",
        "source",
        "id",
        "user_declarant_id",
        "url",
        "document_type",
        "is_corrected",
        "created_date",
        "declaration_year"
})
public class Infocard {

    @JsonProperty("first_name")
    private String firstName;
    @JsonProperty("patronymic")
    private String patronymic;
    @JsonProperty("last_name")
    private String lastName;
    @JsonProperty("office")
    private String office;
    @JsonProperty("position")
    private String position;
    @JsonProperty("source")
    private String source;
    @JsonProperty("id")
    private String id;
    @JsonProperty("user_declarant_id")
    private Integer userDeclarantId;
    @JsonProperty("url")
    private String url;
    @JsonProperty("document_type")
    private String documentType;
    @JsonProperty("is_corrected")
    private Boolean isCorrected;
    @JsonProperty("created_date")
    private String createdDate;
    @JsonProperty("declaration_year")
    private Integer declarationYear;

    @JsonProperty("first_name")
    public String getFirstName() {
        return firstName;
    }

    @JsonProperty("first_name")
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @JsonProperty("patronymic")
    public String getPatronymic() {
        return patronymic;
    }

    @JsonProperty("patronymic")
    public void setPatronymic(String patronymic) {
        this.patronymic = patronymic;
    }

    @JsonProperty("last_name")
    public String getLastName() {
        return lastName;
    }

    @JsonProperty("last_name")
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @JsonProperty("office")
    public String getOffice() {
        return office;
    }

    @JsonProperty("office")
    public void setOffice(String office) {
        this.office = office;
    }

    @JsonProperty("position")
    public String getPosition() {
        return position;
    }

    @JsonProperty("position")
    public void setPosition(String position) {
        this.position = position;
    }

    @JsonProperty("source")
    public String getSource() {
        return source;
    }

    @JsonProperty("source")
    public void setSource(String source) {
        this.source = source;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("user_declarant_id")
    public Integer getUserDeclarantId() {
        return userDeclarantId;
    }

    @JsonProperty("user_declarant_id")
    public void setUserDeclarantId(Integer userDeclarantId) {
        this.userDeclarantId = userDeclarantId;
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("document_type")
    public String getDocumentType() {
        return documentType;
    }

    @JsonProperty("document_type")
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    @JsonProperty("is_corrected")
    public Boolean getIsCorrected() {
        return isCorrected;
    }

    @JsonProperty("is_corrected")
    public void setIsCorrected(Boolean isCorrected) {
        this.isCorrected = isCorrected;
    }

    @JsonProperty("created_date")
    public String getCreatedDate() {
        return createdDate;
    }

    @JsonProperty("created_date")
    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    @JsonProperty("declaration_year")
    public Integer getDeclarationYear() {
        return declarationYear;
    }

    @JsonProperty("declaration_year")
    public void setDeclarationYear(Integer declarationYear) {
        this.declarationYear = declarationYear;
    }

}

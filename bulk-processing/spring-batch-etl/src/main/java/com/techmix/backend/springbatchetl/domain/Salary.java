package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "incomeSource",
        "iteration",
        "objectType",
        "otherObjectType",
        "person",
        "rights",
        "sizeIncome",
        "source_citizen",
        "source_eng_company_address",
        "source_eng_company_code",
        "source_eng_company_name",
        "source_eng_fullname",
        "source_ua_company_code",
        "source_ua_company_name",
        "source_ua_firstname",
        "source_ua_lastname",
        "source_ua_middlename",
        "source_ua_sameRegLivingAddress",
        "source_ukr_company_address",
        "source_ukr_company_name",
        "source_ukr_fullname",
        "dnt_sizeIncome_hidden",
        "dnt_objectType_encoded",
        "dnt_is_foreign"
})
public class Salary {

    @JsonProperty("incomeSource")
    private String incomeSource;
    @JsonProperty("iteration")
    private String iteration;
    @JsonProperty("objectType")
    private String objectType;
    @JsonProperty("otherObjectType")
    private String otherObjectType;
    @JsonProperty("person")
    private String person;
    @JsonProperty("rights")
    private Rights rights;
    @JsonProperty("sizeIncome")
    private Float sizeIncome;
    @JsonProperty("source_citizen")
    private String sourceCitizen;
    @JsonProperty("source_eng_company_address")
    private String sourceEngCompanyAddress;
    @JsonProperty("source_eng_company_code")
    private String sourceEngCompanyCode;
    @JsonProperty("source_eng_company_name")
    private String sourceEngCompanyName;
    @JsonProperty("source_eng_fullname")
    private String sourceEngFullname;
    @JsonProperty("source_ua_company_code")
    private String sourceUaCompanyCode;
    @JsonProperty("source_ua_company_name")
    private String sourceUaCompanyName;
    @JsonProperty("source_ua_firstname")
    private String sourceUaFirstname;
    @JsonProperty("source_ua_lastname")
    private String sourceUaLastname;
    @JsonProperty("source_ua_middlename")
    private String sourceUaMiddlename;
    @JsonProperty("source_ua_sameRegLivingAddress")
    private String sourceUaSameRegLivingAddress;
    @JsonProperty("source_ukr_company_address")
    private String sourceUkrCompanyAddress;
    @JsonProperty("source_ukr_company_name")
    private String sourceUkrCompanyName;
    @JsonProperty("source_ukr_fullname")
    private String sourceUkrFullname;
    @JsonProperty("dnt_sizeIncome_hidden")
    private Boolean dntSizeIncomeHidden;
    @JsonProperty("dnt_objectType_encoded")
    private String dntObjectTypeEncoded;
    @JsonProperty("dnt_is_foreign")
    private Boolean dntIsForeign;

    @JsonProperty("incomeSource")
    public String getIncomeSource() {
        return incomeSource;
    }

    @JsonProperty("incomeSource")
    public void setIncomeSource(String incomeSource) {
        this.incomeSource = incomeSource;
    }

    @JsonProperty("iteration")
    public String getIteration() {
        return iteration;
    }

    @JsonProperty("iteration")
    public void setIteration(String iteration) {
        this.iteration = iteration;
    }

    @JsonProperty("objectType")
    public String getObjectType() {
        return objectType;
    }

    @JsonProperty("objectType")
    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    @JsonProperty("otherObjectType")
    public String getOtherObjectType() {
        return otherObjectType;
    }

    @JsonProperty("otherObjectType")
    public void setOtherObjectType(String otherObjectType) {
        this.otherObjectType = otherObjectType;
    }

    @JsonProperty("person")
    public String getPerson() {
        return person;
    }

    @JsonProperty("person")
    public void setPerson(String person) {
        this.person = person;
    }

    @JsonProperty("rights")
    public Rights getRights() {
        return rights;
    }

    @JsonProperty("rights")
    public void setRights(Rights rights) {
        this.rights = rights;
    }

    @JsonProperty("sizeIncome")
    public Float getSizeIncome() {
        return sizeIncome;
    }

    @JsonProperty("sizeIncome")
    public void setSizeIncome(Float sizeIncome) {
        this.sizeIncome = sizeIncome;
    }

    @JsonProperty("source_citizen")
    public String getSourceCitizen() {
        return sourceCitizen;
    }

    @JsonProperty("source_citizen")
    public void setSourceCitizen(String sourceCitizen) {
        this.sourceCitizen = sourceCitizen;
    }

    @JsonProperty("source_eng_company_address")
    public String getSourceEngCompanyAddress() {
        return sourceEngCompanyAddress;
    }

    @JsonProperty("source_eng_company_address")
    public void setSourceEngCompanyAddress(String sourceEngCompanyAddress) {
        this.sourceEngCompanyAddress = sourceEngCompanyAddress;
    }

    @JsonProperty("source_eng_company_code")
    public String getSourceEngCompanyCode() {
        return sourceEngCompanyCode;
    }

    @JsonProperty("source_eng_company_code")
    public void setSourceEngCompanyCode(String sourceEngCompanyCode) {
        this.sourceEngCompanyCode = sourceEngCompanyCode;
    }

    @JsonProperty("source_eng_company_name")
    public String getSourceEngCompanyName() {
        return sourceEngCompanyName;
    }

    @JsonProperty("source_eng_company_name")
    public void setSourceEngCompanyName(String sourceEngCompanyName) {
        this.sourceEngCompanyName = sourceEngCompanyName;
    }

    @JsonProperty("source_eng_fullname")
    public String getSourceEngFullname() {
        return sourceEngFullname;
    }

    @JsonProperty("source_eng_fullname")
    public void setSourceEngFullname(String sourceEngFullname) {
        this.sourceEngFullname = sourceEngFullname;
    }

    @JsonProperty("source_ua_company_code")
    public String getSourceUaCompanyCode() {
        return sourceUaCompanyCode;
    }

    @JsonProperty("source_ua_company_code")
    public void setSourceUaCompanyCode(String sourceUaCompanyCode) {
        this.sourceUaCompanyCode = sourceUaCompanyCode;
    }

    @JsonProperty("source_ua_company_name")
    public String getSourceUaCompanyName() {
        return sourceUaCompanyName;
    }

    @JsonProperty("source_ua_company_name")
    public void setSourceUaCompanyName(String sourceUaCompanyName) {
        this.sourceUaCompanyName = sourceUaCompanyName;
    }

    @JsonProperty("source_ua_firstname")
    public String getSourceUaFirstname() {
        return sourceUaFirstname;
    }

    @JsonProperty("source_ua_firstname")
    public void setSourceUaFirstname(String sourceUaFirstname) {
        this.sourceUaFirstname = sourceUaFirstname;
    }

    @JsonProperty("source_ua_lastname")
    public String getSourceUaLastname() {
        return sourceUaLastname;
    }

    @JsonProperty("source_ua_lastname")
    public void setSourceUaLastname(String sourceUaLastname) {
        this.sourceUaLastname = sourceUaLastname;
    }

    @JsonProperty("source_ua_middlename")
    public String getSourceUaMiddlename() {
        return sourceUaMiddlename;
    }

    @JsonProperty("source_ua_middlename")
    public void setSourceUaMiddlename(String sourceUaMiddlename) {
        this.sourceUaMiddlename = sourceUaMiddlename;
    }

    @JsonProperty("source_ua_sameRegLivingAddress")
    public String getSourceUaSameRegLivingAddress() {
        return sourceUaSameRegLivingAddress;
    }

    @JsonProperty("source_ua_sameRegLivingAddress")
    public void setSourceUaSameRegLivingAddress(String sourceUaSameRegLivingAddress) {
        this.sourceUaSameRegLivingAddress = sourceUaSameRegLivingAddress;
    }

    @JsonProperty("source_ukr_company_address")
    public String getSourceUkrCompanyAddress() {
        return sourceUkrCompanyAddress;
    }

    @JsonProperty("source_ukr_company_address")
    public void setSourceUkrCompanyAddress(String sourceUkrCompanyAddress) {
        this.sourceUkrCompanyAddress = sourceUkrCompanyAddress;
    }

    @JsonProperty("source_ukr_company_name")
    public String getSourceUkrCompanyName() {
        return sourceUkrCompanyName;
    }

    @JsonProperty("source_ukr_company_name")
    public void setSourceUkrCompanyName(String sourceUkrCompanyName) {
        this.sourceUkrCompanyName = sourceUkrCompanyName;
    }

    @JsonProperty("source_ukr_fullname")
    public String getSourceUkrFullname() {
        return sourceUkrFullname;
    }

    @JsonProperty("source_ukr_fullname")
    public void setSourceUkrFullname(String sourceUkrFullname) {
        this.sourceUkrFullname = sourceUkrFullname;
    }

    @JsonProperty("dnt_sizeIncome_hidden")
    public Boolean getDntSizeIncomeHidden() {
        return dntSizeIncomeHidden;
    }

    @JsonProperty("dnt_sizeIncome_hidden")
    public void setDntSizeIncomeHidden(Boolean dntSizeIncomeHidden) {
        this.dntSizeIncomeHidden = dntSizeIncomeHidden;
    }

    @JsonProperty("dnt_objectType_encoded")
    public String getDntObjectTypeEncoded() {
        return dntObjectTypeEncoded;
    }

    @JsonProperty("dnt_objectType_encoded")
    public void setDntObjectTypeEncoded(String dntObjectTypeEncoded) {
        this.dntObjectTypeEncoded = dntObjectTypeEncoded;
    }

    @JsonProperty("dnt_is_foreign")
    public Boolean getDntIsForeign() {
        return dntIsForeign;
    }

    @JsonProperty("dnt_is_foreign")
    public void setDntIsForeign(Boolean dntIsForeign) {
        this.dntIsForeign = dntIsForeign;
    }

}

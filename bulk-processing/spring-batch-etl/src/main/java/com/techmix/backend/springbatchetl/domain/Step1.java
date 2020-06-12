package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "actual_cityType",
        "actual_country",
        "actual_postCode",
        "actual_street",
        "actual_streetType",
        "apartmentsNum_extendedstatus",
        "changedName",
        "city",
        "cityPath",
        "cityType",
        "corruptionAffected",
        "country",
        "countryPath",
        "district",
        "eng_actualAddress",
        "eng_actualPostCode",
        "eng_postCode",
        "eng_sameRegLivingAddress",
        "firstname",
        "houseNum_extendedstatus",
        "housePartNum_extendedstatus",
        "lastname",
        "middlename",
        "postCategory",
        "postCode",
        "postType",
        "previous_firstname",
        "previous_lastname",
        "previous_middlename",
        "region",
        "responsiblePosition",
        "sameRegLivingAddress",
        "street",
        "streetType",
        "ukr_actualAddress",
        "workPlace",
        "workPost",
        "dnt_organization_group"
})
public class Step1 {

    @JsonProperty("actual_cityType")
    private String actualCityType;
    @JsonProperty("actual_country")
    private String actualCountry;
    @JsonProperty("actual_postCode")
    private String actualPostCode;
    @JsonProperty("actual_street")
    private String actualStreet;
    @JsonProperty("actual_streetType")
    private String actualStreetType;
    @JsonProperty("apartmentsNum_extendedstatus")
    private String apartmentsNumExtendedstatus;
    @JsonProperty("changedName")
    private Boolean changedName;
    @JsonProperty("city")
    private String city;
    @JsonProperty("cityPath")
    private String cityPath;
    @JsonProperty("cityType")
    private String cityType;
    @JsonProperty("corruptionAffected")
    private String corruptionAffected;
    @JsonProperty("country")
    private String country;
    @JsonProperty("countryPath")
    private String countryPath;
    @JsonProperty("district")
    private String district;
    @JsonProperty("eng_actualAddress")
    private String engActualAddress;
    @JsonProperty("eng_actualPostCode")
    private String engActualPostCode;
    @JsonProperty("eng_postCode")
    private String engPostCode;
    @JsonProperty("eng_sameRegLivingAddress")
    private String engSameRegLivingAddress;
    @JsonProperty("firstname")
    private String firstname;
    @JsonProperty("houseNum_extendedstatus")
    private String houseNumExtendedstatus;
    @JsonProperty("housePartNum_extendedstatus")
    private String housePartNumExtendedstatus;
    @JsonProperty("lastname")
    private String lastname;
    @JsonProperty("middlename")
    private String middlename;
    @JsonProperty("postCategory")
    private String postCategory;
    @JsonProperty("postCode")
    private String postCode;
    @JsonProperty("postType")
    private String postType;
    @JsonProperty("previous_firstname")
    private String previousFirstname;
    @JsonProperty("previous_lastname")
    private String previousLastname;
    @JsonProperty("previous_middlename")
    private String previousMiddlename;
    @JsonProperty("region")
    private String region;
    @JsonProperty("responsiblePosition")
    private String responsiblePosition;
    @JsonProperty("sameRegLivingAddress")
    private Boolean sameRegLivingAddress;
    @JsonProperty("street")
    private String street;
    @JsonProperty("streetType")
    private String streetType;
    @JsonProperty("ukr_actualAddress")
    private String ukrActualAddress;
    @JsonProperty("workPlace")
    private String workPlace;
    @JsonProperty("workPost")
    private String workPost;
    @JsonProperty("dnt_organization_group")
    private String dntOrganizationGroup;

    @JsonProperty("actual_cityType")
    public String getActualCityType() {
        return actualCityType;
    }

    @JsonProperty("actual_cityType")
    public void setActualCityType(String actualCityType) {
        this.actualCityType = actualCityType;
    }

    @JsonProperty("actual_country")
    public String getActualCountry() {
        return actualCountry;
    }

    @JsonProperty("actual_country")
    public void setActualCountry(String actualCountry) {
        this.actualCountry = actualCountry;
    }

    @JsonProperty("actual_postCode")
    public String getActualPostCode() {
        return actualPostCode;
    }

    @JsonProperty("actual_postCode")
    public void setActualPostCode(String actualPostCode) {
        this.actualPostCode = actualPostCode;
    }

    @JsonProperty("actual_street")
    public String getActualStreet() {
        return actualStreet;
    }

    @JsonProperty("actual_street")
    public void setActualStreet(String actualStreet) {
        this.actualStreet = actualStreet;
    }

    @JsonProperty("actual_streetType")
    public String getActualStreetType() {
        return actualStreetType;
    }

    @JsonProperty("actual_streetType")
    public void setActualStreetType(String actualStreetType) {
        this.actualStreetType = actualStreetType;
    }

    @JsonProperty("apartmentsNum_extendedstatus")
    public String getApartmentsNumExtendedstatus() {
        return apartmentsNumExtendedstatus;
    }

    @JsonProperty("apartmentsNum_extendedstatus")
    public void setApartmentsNumExtendedstatus(String apartmentsNumExtendedstatus) {
        this.apartmentsNumExtendedstatus = apartmentsNumExtendedstatus;
    }

    @JsonProperty("changedName")
    public Boolean getChangedName() {
        return changedName;
    }

    @JsonProperty("changedName")
    public void setChangedName(Boolean changedName) {
        this.changedName = changedName;
    }

    @JsonProperty("city")
    public String getCity() {
        return city;
    }

    @JsonProperty("city")
    public void setCity(String city) {
        this.city = city;
    }

    @JsonProperty("cityPath")
    public String getCityPath() {
        return cityPath;
    }

    @JsonProperty("cityPath")
    public void setCityPath(String cityPath) {
        this.cityPath = cityPath;
    }

    @JsonProperty("cityType")
    public String getCityType() {
        return cityType;
    }

    @JsonProperty("cityType")
    public void setCityType(String cityType) {
        this.cityType = cityType;
    }

    @JsonProperty("corruptionAffected")
    public String getCorruptionAffected() {
        return corruptionAffected;
    }

    @JsonProperty("corruptionAffected")
    public void setCorruptionAffected(String corruptionAffected) {
        this.corruptionAffected = corruptionAffected;
    }

    @JsonProperty("country")
    public String getCountry() {
        return country;
    }

    @JsonProperty("country")
    public void setCountry(String country) {
        this.country = country;
    }

    @JsonProperty("countryPath")
    public String getCountryPath() {
        return countryPath;
    }

    @JsonProperty("countryPath")
    public void setCountryPath(String countryPath) {
        this.countryPath = countryPath;
    }

    @JsonProperty("district")
    public String getDistrict() {
        return district;
    }

    @JsonProperty("district")
    public void setDistrict(String district) {
        this.district = district;
    }

    @JsonProperty("eng_actualAddress")
    public String getEngActualAddress() {
        return engActualAddress;
    }

    @JsonProperty("eng_actualAddress")
    public void setEngActualAddress(String engActualAddress) {
        this.engActualAddress = engActualAddress;
    }

    @JsonProperty("eng_actualPostCode")
    public String getEngActualPostCode() {
        return engActualPostCode;
    }

    @JsonProperty("eng_actualPostCode")
    public void setEngActualPostCode(String engActualPostCode) {
        this.engActualPostCode = engActualPostCode;
    }

    @JsonProperty("eng_postCode")
    public String getEngPostCode() {
        return engPostCode;
    }

    @JsonProperty("eng_postCode")
    public void setEngPostCode(String engPostCode) {
        this.engPostCode = engPostCode;
    }

    @JsonProperty("eng_sameRegLivingAddress")
    public String getEngSameRegLivingAddress() {
        return engSameRegLivingAddress;
    }

    @JsonProperty("eng_sameRegLivingAddress")
    public void setEngSameRegLivingAddress(String engSameRegLivingAddress) {
        this.engSameRegLivingAddress = engSameRegLivingAddress;
    }

    @JsonProperty("firstname")
    public String getFirstname() {
        return firstname;
    }

    @JsonProperty("firstname")
    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    @JsonProperty("houseNum_extendedstatus")
    public String getHouseNumExtendedstatus() {
        return houseNumExtendedstatus;
    }

    @JsonProperty("houseNum_extendedstatus")
    public void setHouseNumExtendedstatus(String houseNumExtendedstatus) {
        this.houseNumExtendedstatus = houseNumExtendedstatus;
    }

    @JsonProperty("housePartNum_extendedstatus")
    public String getHousePartNumExtendedstatus() {
        return housePartNumExtendedstatus;
    }

    @JsonProperty("housePartNum_extendedstatus")
    public void setHousePartNumExtendedstatus(String housePartNumExtendedstatus) {
        this.housePartNumExtendedstatus = housePartNumExtendedstatus;
    }

    @JsonProperty("lastname")
    public String getLastname() {
        return lastname;
    }

    @JsonProperty("lastname")
    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    @JsonProperty("middlename")
    public String getMiddlename() {
        return middlename;
    }

    @JsonProperty("middlename")
    public void setMiddlename(String middlename) {
        this.middlename = middlename;
    }

    @JsonProperty("postCategory")
    public String getPostCategory() {
        return postCategory;
    }

    @JsonProperty("postCategory")
    public void setPostCategory(String postCategory) {
        this.postCategory = postCategory;
    }

    @JsonProperty("postCode")
    public String getPostCode() {
        return postCode;
    }

    @JsonProperty("postCode")
    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    @JsonProperty("postType")
    public String getPostType() {
        return postType;
    }

    @JsonProperty("postType")
    public void setPostType(String postType) {
        this.postType = postType;
    }

    @JsonProperty("previous_firstname")
    public String getPreviousFirstname() {
        return previousFirstname;
    }

    @JsonProperty("previous_firstname")
    public void setPreviousFirstname(String previousFirstname) {
        this.previousFirstname = previousFirstname;
    }

    @JsonProperty("previous_lastname")
    public String getPreviousLastname() {
        return previousLastname;
    }

    @JsonProperty("previous_lastname")
    public void setPreviousLastname(String previousLastname) {
        this.previousLastname = previousLastname;
    }

    @JsonProperty("previous_middlename")
    public String getPreviousMiddlename() {
        return previousMiddlename;
    }

    @JsonProperty("previous_middlename")
    public void setPreviousMiddlename(String previousMiddlename) {
        this.previousMiddlename = previousMiddlename;
    }

    @JsonProperty("region")
    public String getRegion() {
        return region;
    }

    @JsonProperty("region")
    public void setRegion(String region) {
        this.region = region;
    }

    @JsonProperty("responsiblePosition")
    public String getResponsiblePosition() {
        return responsiblePosition;
    }

    @JsonProperty("responsiblePosition")
    public void setResponsiblePosition(String responsiblePosition) {
        this.responsiblePosition = responsiblePosition;
    }

    @JsonProperty("sameRegLivingAddress")
    public Boolean getSameRegLivingAddress() {
        return sameRegLivingAddress;
    }

    @JsonProperty("sameRegLivingAddress")
    public void setSameRegLivingAddress(Boolean sameRegLivingAddress) {
        this.sameRegLivingAddress = sameRegLivingAddress;
    }

    @JsonProperty("street")
    public String getStreet() {
        return street;
    }

    @JsonProperty("street")
    public void setStreet(String street) {
        this.street = street;
    }

    @JsonProperty("streetType")
    public String getStreetType() {
        return streetType;
    }

    @JsonProperty("streetType")
    public void setStreetType(String streetType) {
        this.streetType = streetType;
    }

    @JsonProperty("ukr_actualAddress")
    public String getUkrActualAddress() {
        return ukrActualAddress;
    }

    @JsonProperty("ukr_actualAddress")
    public void setUkrActualAddress(String ukrActualAddress) {
        this.ukrActualAddress = ukrActualAddress;
    }

    @JsonProperty("workPlace")
    public String getWorkPlace() {
        return workPlace;
    }

    @JsonProperty("workPlace")
    public void setWorkPlace(String workPlace) {
        this.workPlace = workPlace;
    }

    @JsonProperty("workPost")
    public String getWorkPost() {
        return workPost;
    }

    @JsonProperty("workPost")
    public void setWorkPost(String workPost) {
        this.workPost = workPost;
    }

    @JsonProperty("dnt_organization_group")
    public String getDntOrganizationGroup() {
        return dntOrganizationGroup;
    }

    @JsonProperty("dnt_organization_group")
    public void setDntOrganizationGroup(String dntOrganizationGroup) {
        this.dntOrganizationGroup = dntOrganizationGroup;
    }

}

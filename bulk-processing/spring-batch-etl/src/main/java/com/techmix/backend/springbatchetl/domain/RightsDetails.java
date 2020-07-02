package com.techmix.backend.springbatchetl.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "citizen",
        "eng_company_address",
        "eng_company_code",
        "eng_company_name",
        "eng_firstname",
        "eng_fullname",
        "eng_lastname",
        "eng_middlename",
        "eng_middlename_extendedstatus",
        "eng_postCode",
        "eng_postCode_extendedstatus",
        "otherOwnership",
        "ownershipType",
        "percent-ownership",
        "postCode",
        "rightBelongs",
        "rights_cityPath",
        "ua_apartmentsNum_extendedstatus",
        "ua_city",
        "ua_company_code",
        "ua_company_name",
        "ua_firstname",
        "ua_houseNum_extendedstatus",
        "ua_housePartNum_extendedstatus",
        "ua_lastname",
        "ua_middlename",
        "ua_middlename_extendedstatus",
        "ua_postCode",
        "ua_postCode_extendedstatus",
        "ua_street",
        "ua_streetType",
        "ua_street_extendedstatus",
        "ukr_actualAddress",
        "ukr_company_address",
        "ukr_company_name",
        "ukr_firstname",
        "ukr_fullname",
        "ukr_lastname",
        "ukr_middlename",
        "ukr_middlename_extendedstatus"
})
public class RightsDetails {

    @JsonProperty("citizen")
    private String citizen;
    @JsonProperty("eng_company_address")
    private String engCompanyAddress;
    @JsonProperty("eng_company_code")
    private String engCompanyCode;
    @JsonProperty("eng_company_name")
    private String engCompanyName;
    @JsonProperty("eng_firstname")
    private String engFirstname;
    @JsonProperty("eng_fullname")
    private String engFullname;
    @JsonProperty("eng_lastname")
    private String engLastname;
    @JsonProperty("eng_middlename")
    private String engMiddlename;
    @JsonProperty("eng_middlename_extendedstatus")
    private String engMiddlenameExtendedstatus;
    @JsonProperty("eng_postCode")
    private String engPostCode;
    @JsonProperty("eng_postCode_extendedstatus")
    private String engPostCodeExtendedstatus;
    @JsonProperty("otherOwnership")
    private String otherOwnership;
    @JsonProperty("ownershipType")
    private String ownershipType;
    @JsonProperty("percent-ownership")
    private String percentOwnership;
    @JsonProperty("postCode")
    private String postCode;
    @JsonProperty("rightBelongs")
    private String rightBelongs;
    @JsonProperty("rights_cityPath")
    private String rightsCityPath;
    @JsonProperty("ua_apartmentsNum_extendedstatus")
    private String uaApartmentsNumExtendedstatus;
    @JsonProperty("ua_city")
    private String uaCity;
    @JsonProperty("ua_company_code")
    private String uaCompanyCode;
    @JsonProperty("ua_company_name")
    private String uaCompanyName;
    @JsonProperty("ua_firstname")
    private String uaFirstname;
    @JsonProperty("ua_houseNum_extendedstatus")
    private String uaHouseNumExtendedstatus;
    @JsonProperty("ua_housePartNum_extendedstatus")
    private String uaHousePartNumExtendedstatus;
    @JsonProperty("ua_lastname")
    private String uaLastname;
    @JsonProperty("ua_middlename")
    private String uaMiddlename;
    @JsonProperty("ua_middlename_extendedstatus")
    private String uaMiddlenameExtendedstatus;
    @JsonProperty("ua_postCode")
    private String uaPostCode;
    @JsonProperty("ua_postCode_extendedstatus")
    private String uaPostCodeExtendedstatus;
    @JsonProperty("ua_street")
    private String uaStreet;
    @JsonProperty("ua_streetType")
    private String uaStreetType;
    @JsonProperty("ua_street_extendedstatus")
    private String uaStreetExtendedstatus;
    @JsonProperty("ukr_actualAddress")
    private String ukrActualAddress;
    @JsonProperty("ukr_company_address")
    private String ukrCompanyAddress;
    @JsonProperty("ukr_company_name")
    private String ukrCompanyName;
    @JsonProperty("ukr_firstname")
    private String ukrFirstname;
    @JsonProperty("ukr_fullname")
    private String ukrFullname;
    @JsonProperty("ukr_lastname")
    private String ukrLastname;
    @JsonProperty("ukr_middlename")
    private String ukrMiddlename;
    @JsonProperty("ukr_middlename_extendedstatus")
    private String ukrMiddlenameExtendedstatus;

    @JsonProperty("citizen")
    public String getCitizen() {
        return citizen;
    }

    @JsonProperty("citizen")
    public void setCitizen(String citizen) {
        this.citizen = citizen;
    }

    @JsonProperty("eng_company_address")
    public String getEngCompanyAddress() {
        return engCompanyAddress;
    }

    @JsonProperty("eng_company_address")
    public void setEngCompanyAddress(String engCompanyAddress) {
        this.engCompanyAddress = engCompanyAddress;
    }

    @JsonProperty("eng_company_code")
    public String getEngCompanyCode() {
        return engCompanyCode;
    }

    @JsonProperty("eng_company_code")
    public void setEngCompanyCode(String engCompanyCode) {
        this.engCompanyCode = engCompanyCode;
    }

    @JsonProperty("eng_company_name")
    public String getEngCompanyName() {
        return engCompanyName;
    }

    @JsonProperty("eng_company_name")
    public void setEngCompanyName(String engCompanyName) {
        this.engCompanyName = engCompanyName;
    }

    @JsonProperty("eng_firstname")
    public String getEngFirstname() {
        return engFirstname;
    }

    @JsonProperty("eng_firstname")
    public void setEngFirstname(String engFirstname) {
        this.engFirstname = engFirstname;
    }

    @JsonProperty("eng_fullname")
    public String getEngFullname() {
        return engFullname;
    }

    @JsonProperty("eng_fullname")
    public void setEngFullname(String engFullname) {
        this.engFullname = engFullname;
    }

    @JsonProperty("eng_lastname")
    public String getEngLastname() {
        return engLastname;
    }

    @JsonProperty("eng_lastname")
    public void setEngLastname(String engLastname) {
        this.engLastname = engLastname;
    }

    @JsonProperty("eng_middlename")
    public String getEngMiddlename() {
        return engMiddlename;
    }

    @JsonProperty("eng_middlename")
    public void setEngMiddlename(String engMiddlename) {
        this.engMiddlename = engMiddlename;
    }

    @JsonProperty("eng_middlename_extendedstatus")
    public String getEngMiddlenameExtendedstatus() {
        return engMiddlenameExtendedstatus;
    }

    @JsonProperty("eng_middlename_extendedstatus")
    public void setEngMiddlenameExtendedstatus(String engMiddlenameExtendedstatus) {
        this.engMiddlenameExtendedstatus = engMiddlenameExtendedstatus;
    }

    @JsonProperty("eng_postCode")
    public String getEngPostCode() {
        return engPostCode;
    }

    @JsonProperty("eng_postCode")
    public void setEngPostCode(String engPostCode) {
        this.engPostCode = engPostCode;
    }

    @JsonProperty("eng_postCode_extendedstatus")
    public String getEngPostCodeExtendedstatus() {
        return engPostCodeExtendedstatus;
    }

    @JsonProperty("eng_postCode_extendedstatus")
    public void setEngPostCodeExtendedstatus(String engPostCodeExtendedstatus) {
        this.engPostCodeExtendedstatus = engPostCodeExtendedstatus;
    }

    @JsonProperty("otherOwnership")
    public String getOtherOwnership() {
        return otherOwnership;
    }

    @JsonProperty("otherOwnership")
    public void setOtherOwnership(String otherOwnership) {
        this.otherOwnership = otherOwnership;
    }

    @JsonProperty("ownershipType")
    public String getOwnershipType() {
        return ownershipType;
    }

    @JsonProperty("ownershipType")
    public void setOwnershipType(String ownershipType) {
        this.ownershipType = ownershipType;
    }

    @JsonProperty("percent-ownership")
    public String getPercentOwnership() {
        return percentOwnership;
    }

    @JsonProperty("percent-ownership")
    public void setPercentOwnership(String percentOwnership) {
        this.percentOwnership = percentOwnership;
    }

    @JsonProperty("postCode")
    public String getPostCode() {
        return postCode;
    }

    @JsonProperty("postCode")
    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    @JsonProperty("rightBelongs")
    public String getRightBelongs() {
        return rightBelongs;
    }

    @JsonProperty("rightBelongs")
    public void setRightBelongs(String rightBelongs) {
        this.rightBelongs = rightBelongs;
    }

    @JsonProperty("rights_cityPath")
    public String getRightsCityPath() {
        return rightsCityPath;
    }

    @JsonProperty("rights_cityPath")
    public void setRightsCityPath(String rightsCityPath) {
        this.rightsCityPath = rightsCityPath;
    }

    @JsonProperty("ua_apartmentsNum_extendedstatus")
    public String getUaApartmentsNumExtendedstatus() {
        return uaApartmentsNumExtendedstatus;
    }

    @JsonProperty("ua_apartmentsNum_extendedstatus")
    public void setUaApartmentsNumExtendedstatus(String uaApartmentsNumExtendedstatus) {
        this.uaApartmentsNumExtendedstatus = uaApartmentsNumExtendedstatus;
    }

    @JsonProperty("ua_city")
    public String getUaCity() {
        return uaCity;
    }

    @JsonProperty("ua_city")
    public void setUaCity(String uaCity) {
        this.uaCity = uaCity;
    }

    @JsonProperty("ua_company_code")
    public String getUaCompanyCode() {
        return uaCompanyCode;
    }

    @JsonProperty("ua_company_code")
    public void setUaCompanyCode(String uaCompanyCode) {
        this.uaCompanyCode = uaCompanyCode;
    }

    @JsonProperty("ua_company_name")
    public String getUaCompanyName() {
        return uaCompanyName;
    }

    @JsonProperty("ua_company_name")
    public void setUaCompanyName(String uaCompanyName) {
        this.uaCompanyName = uaCompanyName;
    }

    @JsonProperty("ua_firstname")
    public String getUaFirstname() {
        return uaFirstname;
    }

    @JsonProperty("ua_firstname")
    public void setUaFirstname(String uaFirstname) {
        this.uaFirstname = uaFirstname;
    }

    @JsonProperty("ua_houseNum_extendedstatus")
    public String getUaHouseNumExtendedstatus() {
        return uaHouseNumExtendedstatus;
    }

    @JsonProperty("ua_houseNum_extendedstatus")
    public void setUaHouseNumExtendedstatus(String uaHouseNumExtendedstatus) {
        this.uaHouseNumExtendedstatus = uaHouseNumExtendedstatus;
    }

    @JsonProperty("ua_housePartNum_extendedstatus")
    public String getUaHousePartNumExtendedstatus() {
        return uaHousePartNumExtendedstatus;
    }

    @JsonProperty("ua_housePartNum_extendedstatus")
    public void setUaHousePartNumExtendedstatus(String uaHousePartNumExtendedstatus) {
        this.uaHousePartNumExtendedstatus = uaHousePartNumExtendedstatus;
    }

    @JsonProperty("ua_lastname")
    public String getUaLastname() {
        return uaLastname;
    }

    @JsonProperty("ua_lastname")
    public void setUaLastname(String uaLastname) {
        this.uaLastname = uaLastname;
    }

    @JsonProperty("ua_middlename")
    public String getUaMiddlename() {
        return uaMiddlename;
    }

    @JsonProperty("ua_middlename")
    public void setUaMiddlename(String uaMiddlename) {
        this.uaMiddlename = uaMiddlename;
    }

    @JsonProperty("ua_middlename_extendedstatus")
    public String getUaMiddlenameExtendedstatus() {
        return uaMiddlenameExtendedstatus;
    }

    @JsonProperty("ua_middlename_extendedstatus")
    public void setUaMiddlenameExtendedstatus(String uaMiddlenameExtendedstatus) {
        this.uaMiddlenameExtendedstatus = uaMiddlenameExtendedstatus;
    }

    @JsonProperty("ua_postCode")
    public String getUaPostCode() {
        return uaPostCode;
    }

    @JsonProperty("ua_postCode")
    public void setUaPostCode(String uaPostCode) {
        this.uaPostCode = uaPostCode;
    }

    @JsonProperty("ua_postCode_extendedstatus")
    public String getUaPostCodeExtendedstatus() {
        return uaPostCodeExtendedstatus;
    }

    @JsonProperty("ua_postCode_extendedstatus")
    public void setUaPostCodeExtendedstatus(String uaPostCodeExtendedstatus) {
        this.uaPostCodeExtendedstatus = uaPostCodeExtendedstatus;
    }

    @JsonProperty("ua_street")
    public String getUaStreet() {
        return uaStreet;
    }

    @JsonProperty("ua_street")
    public void setUaStreet(String uaStreet) {
        this.uaStreet = uaStreet;
    }

    @JsonProperty("ua_streetType")
    public String getUaStreetType() {
        return uaStreetType;
    }

    @JsonProperty("ua_streetType")
    public void setUaStreetType(String uaStreetType) {
        this.uaStreetType = uaStreetType;
    }

    @JsonProperty("ua_street_extendedstatus")
    public String getUaStreetExtendedstatus() {
        return uaStreetExtendedstatus;
    }

    @JsonProperty("ua_street_extendedstatus")
    public void setUaStreetExtendedstatus(String uaStreetExtendedstatus) {
        this.uaStreetExtendedstatus = uaStreetExtendedstatus;
    }

    @JsonProperty("ukr_actualAddress")
    public String getUkrActualAddress() {
        return ukrActualAddress;
    }

    @JsonProperty("ukr_actualAddress")
    public void setUkrActualAddress(String ukrActualAddress) {
        this.ukrActualAddress = ukrActualAddress;
    }

    @JsonProperty("ukr_company_address")
    public String getUkrCompanyAddress() {
        return ukrCompanyAddress;
    }

    @JsonProperty("ukr_company_address")
    public void setUkrCompanyAddress(String ukrCompanyAddress) {
        this.ukrCompanyAddress = ukrCompanyAddress;
    }

    @JsonProperty("ukr_company_name")
    public String getUkrCompanyName() {
        return ukrCompanyName;
    }

    @JsonProperty("ukr_company_name")
    public void setUkrCompanyName(String ukrCompanyName) {
        this.ukrCompanyName = ukrCompanyName;
    }

    @JsonProperty("ukr_firstname")
    public String getUkrFirstname() {
        return ukrFirstname;
    }

    @JsonProperty("ukr_firstname")
    public void setUkrFirstname(String ukrFirstname) {
        this.ukrFirstname = ukrFirstname;
    }

    @JsonProperty("ukr_fullname")
    public String getUkrFullname() {
        return ukrFullname;
    }

    @JsonProperty("ukr_fullname")
    public void setUkrFullname(String ukrFullname) {
        this.ukrFullname = ukrFullname;
    }

    @JsonProperty("ukr_lastname")
    public String getUkrLastname() {
        return ukrLastname;
    }

    @JsonProperty("ukr_lastname")
    public void setUkrLastname(String ukrLastname) {
        this.ukrLastname = ukrLastname;
    }

    @JsonProperty("ukr_middlename")
    public String getUkrMiddlename() {
        return ukrMiddlename;
    }

    @JsonProperty("ukr_middlename")
    public void setUkrMiddlename(String ukrMiddlename) {
        this.ukrMiddlename = ukrMiddlename;
    }

    @JsonProperty("ukr_middlename_extendedstatus")
    public String getUkrMiddlenameExtendedstatus() {
        return ukrMiddlenameExtendedstatus;
    }

    @JsonProperty("ukr_middlename_extendedstatus")
    public void setUkrMiddlenameExtendedstatus(String ukrMiddlenameExtendedstatus) {
        this.ukrMiddlenameExtendedstatus = ukrMiddlenameExtendedstatus;
    }

}

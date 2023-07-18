package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * Details of an Service Level Agreement (SLA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceLevelAgreement extends Version {

    public String kind = "ServiceLevelAgreement";

    int one;
    int two;
    boolean test;
}

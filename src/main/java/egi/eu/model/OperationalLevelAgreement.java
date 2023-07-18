package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * Details of an Operational Level Agreement (OLA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationalLevelAgreement extends Version {

    public String kind = "OperationalLevelAgreement";

    int one;
    int two;
    boolean test;
}

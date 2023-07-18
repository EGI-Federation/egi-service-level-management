package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * Details of an Underpinning Agreement (UA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnderpinningAgreement extends Version {

    public String kind = "UnderpinningAgreement";

    int one;
    int two;
    boolean test;
}

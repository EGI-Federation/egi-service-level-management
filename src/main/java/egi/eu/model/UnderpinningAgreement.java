package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of an Underpinning Agreement (UA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnderpinningAgreement extends Version {

    @Schema(enumeration={ "UnderpinningAgreement" })
    public String kind = "UnderpinningAgreement";

    @Schema(description="ID of the agreement, assigned on creation")
    int id;

    int one;
    int two;
    boolean test;
}

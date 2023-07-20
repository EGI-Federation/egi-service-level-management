package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

import egi.eu.History;


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

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfUnderpinningAgreement history = null;

    /***
     * History of an SLA
     */
    public class HistoryOfUnderpinningAgreement extends History<UnderpinningAgreement> {
        public HistoryOfUnderpinningAgreement() { super(); }
        public HistoryOfUnderpinningAgreement(int size) { super(size); }
    }
}

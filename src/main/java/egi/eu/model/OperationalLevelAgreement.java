package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

import egi.eu.History;


/**
 * Details of an Operational Level Agreement (OLA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationalLevelAgreement extends Version {

    @Schema(enumeration={ "OperationalLevelAgreement" })
    public String kind = "OperationalLevelAgreement";

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
    HistoryOfOperationalLevelAgreement history = null;

    /***
     * History of an OLA
     */
    public class HistoryOfOperationalLevelAgreement extends History<OperationalLevelAgreement> {
        public HistoryOfOperationalLevelAgreement() { super(); }
        public HistoryOfOperationalLevelAgreement(int size) { super(size); }
    }
}

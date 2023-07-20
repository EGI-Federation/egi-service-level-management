package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

import egi.eu.History;


/**
 * Details of an Service Level Agreement (SLA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceLevelAgreement extends Version {

    @Schema(enumeration={ "ServiceLevelAgreement" })
    public String kind = "ServiceLevelAgreement";

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
    HistoryOfServiceLevelAgreement history = null;

    /***
     * History of an SLA
     */
    public class HistoryOfServiceLevelAgreement extends History<ServiceLevelAgreement> {
        public HistoryOfServiceLevelAgreement() { super(); }
        public HistoryOfServiceLevelAgreement(int size) { super(size); }
    }
}

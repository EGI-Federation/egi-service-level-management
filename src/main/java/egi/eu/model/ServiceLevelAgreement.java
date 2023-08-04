package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of an Service Level Agreement (SLA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceLevelAgreement extends Version {

    @Schema(enumeration={ "ServiceLevelAgreement" })
    public String kind = "ServiceLevelAgreement";

    @Schema(description="ID of the agreement, assigned on creation")
    long id;

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
     * History of the SLA
     */
    public class HistoryOfServiceLevelAgreement extends History<ServiceLevelAgreement> {
        public HistoryOfServiceLevelAgreement() { super(); }
    }
}

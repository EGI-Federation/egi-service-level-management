package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of an Operational Level Agreement (OLA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationalLevelAgreement extends Version<OperationalLevelAgreement> {

    @Schema(enumeration={ "OperationalLevelAgreement" })
    public String kind = "OperationalLevelAgreement";

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
    HistoryOfOperationalLevelAgreement history = null;

    /***
     * History of the OLA
     */
    public static class HistoryOfOperationalLevelAgreement extends History<OperationalLevelAgreement> {
        public HistoryOfOperationalLevelAgreement() { super(); }
    }
}

package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of an Underpinning Agreement (UA)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnderpinningAgreement extends Version<UnderpinningAgreement> {

    @Schema(enumeration={ "UnderpinningAgreement" })
    public String kind = "UnderpinningAgreement";

    @Schema(description="ID of the agreement, assigned on creation")
    public Long id;

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
     * History of the UA
     */
    public static class HistoryOfUnderpinningAgreement extends History<UnderpinningAgreement> {
        public HistoryOfUnderpinningAgreement() { super(); }
    }
}

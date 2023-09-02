package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * An approval or a rejection
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Approval {

    public final static String OPERATION_APPROVE = "Approve";
    public final static String OPERATION_REJECT = "Reject";


    @Schema(enumeration={ "approve", "reject" })
    public String operation;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String changeDescription;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public User approver;
}

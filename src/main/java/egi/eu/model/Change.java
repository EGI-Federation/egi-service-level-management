package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * An entity change (e.g. approval/rejection of an approval request)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Change {

    public final static String OPERATION_APPROVE = "Approve";
    public final static String OPERATION_REJECT = "Reject";

    @Schema(enumeration={ "approve", "reject" })
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String operation;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String changeDescription;
}

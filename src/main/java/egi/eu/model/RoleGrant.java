package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;


/**
 * A role assignment or revocation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleGrant {

    @Schema(hidden = true)
    public Boolean assign;

    @Parameter(description = "The role to assign or revoke")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String role;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public User roleHolder;

    @Schema(hidden = true)
    public User changeBy;


    /***
     * Constructor
     */
    public RoleGrant() {}

    /***
     * Constructor with caller identity
     */
    public RoleGrant(User changeBy) {
        this.changeBy = changeBy;
    }
}

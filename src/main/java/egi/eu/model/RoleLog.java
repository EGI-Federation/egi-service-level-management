package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import egi.eu.entity.RoleLogEntity;


/***
 * Log of a role assignment or revocation
 */
public class RoleLog {

    @Schema(enumeration={ "RoleLog" })
    public String kind = "RoleLog";

    public String role; // One of the Role constants

    @Schema(description="Assigned or revoked")
    public boolean assigned;

    @Schema(description="User with this role")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public User user;

    @Schema(description="Date and time of the role assignment. Assigned automatically, you should never send this.\n" +
                        "Always returned as UTC date and time.")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
    public LocalDateTime changedOn; // UTC

    @Schema(description="User who assigned/revoked the role")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public User changeBy;


    /***
     * Constructor
     */
    public RoleLog() {}

    /***
     * Copy constructor
     * @param roleAssign The entity to copy
     */
    public RoleLog(RoleLogEntity roleAssign) {

        this.role = roleAssign.role;
        this.assigned = roleAssign.assigned;
        this.changedOn = (null == roleAssign.changedOn) ? null :
                roleAssign.changedOn
                          .atZone(ZoneId.systemDefault())
                          .withZoneSameInstant(ZoneOffset.UTC)
                          .toLocalDateTime();
        if(null != roleAssign.user)
            this.user = new User(roleAssign.user);
        if(null != roleAssign.changeBy)
            this.changeBy = new User(roleAssign.changeBy);
    }
}

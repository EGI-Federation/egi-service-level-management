package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.ArrayList;

import egi.checkin.model.UserInfo;


/***
 * The roles that will govern access to the features
 * in section Service Level Management
 */
public class Role {

    public final static String PROCESS_OWNER = "process-owner";
    public final static String PROCESS_MANAGER = "process-manager";
    public final static String CATALOG_MANAGER = "catalog-manager";
    public final static String REPORT_OWNER = "report-owner";
    public final static String UA_OWNER = "ua-owner";
    public final static String OLA_OWNER = "ola-owner";
    public final static String SLA_OWNER = "sla-owner";

    // Pseudo-roles that can be used to authorize access to the API endpoints,
    // but are not considered/returned by the user-related endpoints
    public final static String ISM_USER = "ism";        // Marks membership in the VO
    public final static String ISM_ADMIN = "admin";     // Marks being a VO manager
    public final static String PROCESS_MEMBER = "slm";  // Marks membership in the group


    @Schema(enumeration={ "Role" })
    public String kind = "Role";

    public String role;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<UserInfo> users;


    /***
     * Constructor
     */
    public Role() {}

    /***
     * Construct with a role
     */
    public Role(String role) { this.role = role; }

    /***
     * Record that a user has this role assigned
     * @param user The user the role is assigned to
     */
    public Role addUser(UserInfo user) {
        if(null == this.users)
            this.users = new ArrayList<>();

        this.users.add(user);

        return this;
    }
}

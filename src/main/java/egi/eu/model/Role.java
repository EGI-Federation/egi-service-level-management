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

    // Pseudo-roles that can be used in API endpoint annotations to define access,
    // but are not considered/returned by the user-related endpoints
    public final static String IMS_USER = "ims";        // Marks membership in the VO
    public final static String IMS_ADMIN = "admin";     // Marks being an IMS admin (owner/manager/coordinator)
    public final static String PROCESS_MEMBER = "slm";  // Marks membership in the group


    @Schema(enumeration={ "Role" })
    public String kind = "Role";

    @Schema(description="ID of the role, assigned on creation")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    long id;

    public String role;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String tasks;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<UserInfo> users;

    // The fields below are linking this role to a global IMS role
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    long globalRoleId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String globalRoleName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String globalRoleTasks;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfRole history = null;


    /***
     * History of a role
     */
    public static class HistoryOfRole extends History<Role> {
        public HistoryOfRole() { super(); }
    }


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

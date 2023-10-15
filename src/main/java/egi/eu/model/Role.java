package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.CheckinUser;
import egi.eu.entity.RoleEntity;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;


/***
 * The roles that will govern access to the features in section Service Level Management
 */
public class Role extends VersionInfo {

    public final static String PROCESS_OWNER = "process-owner";
    public final static String PROCESS_MANAGER = "process-manager";
    public final static String PROCESS_DEVELOPER = "process-developer";
    public final static String CATALOG_OWNER = "catalog-owner";
    public final static String REPORT_OWNER = "report-owner";
    public final static String UA_OWNER = "ua-owner";
    public final static String OLA_OWNER = "ola-owner";
    public final static String SLA_OWNER = "sla-owner";

    // Pseudo-roles that can be used in API endpoint annotations to define access,
    // but are not considered/returned by the API endpoints nor stored in Check-in
    public final static String IMS_USER = "ims";        // Marks membership in the VO
    public final static String IMS_ADMIN = "admin";     // Marks being an IMS admin (owner/manager)
    public final static String PROCESS_MEMBER = "process-staff";  // Marks membership in the group

    public enum RoleStatus {
        DRAFT(0),
        IMPLEMENTED(1),
        DEPRECATED(2);

        private final int value;
        private RoleStatus(int value) { this.value = value; }
        public int getValue() { return value; }
        public static RoleStatus of(int value) {
            return switch(value) {
                case 1 -> IMPLEMENTED;
                case 2 -> DEPRECATED;
                default -> DRAFT;
            };
        }
    }

    @Schema(enumeration={ "Role" })
    public String kind = "Role";

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Long id;

    public String role; // One of the constants from above

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String name; // Human-readable version of the role field

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String tasks; // Markdown

    @Schema(description="Users that hold this role")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<User> users;

    // The fields below are linking this role to a global IMS role
    @Schema(description="ID of a global role to inherit tasks from")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Long globalRoleId;

    @Schema(description="Name of a global role to inherit tasks from")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String globalRoleName;

    @Schema(description="Inherited tasks")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String globalRoleTasks; // Markdown

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public RoleStatus status = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public HistoryOfRole history = null;


    /***
     * History of a role
     */
    public static class HistoryOfRole extends History<Role> {
        public HistoryOfRole() { super(); }
        public HistoryOfRole(List<Role> olderVersions) { super(olderVersions); }
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
     * Copy constructor
     * @param role The entity to copy
     */
    public Role(RoleEntity role) {

        this.id = role.id;
        this.role = role.role;
        this.name = role.name;
        this.tasks = role.tasks;
        this.globalRoleId = role.globalRoleId;
        this.globalRoleName = role.globalRoleName;
        this.globalRoleTasks = role.globalRoleTasks;
        this.status = RoleStatus.of(role.status);

        this.version = role.version;
        this.changedOn = role.changedOn;
        this.changeDescription = role.changeDescription;
        if(null != role.changeBy)
            this.changeBy = new User(role.changeBy);
    }

    /***
     * Construct from history.
     * @param roleVersions The list of versions, should start with the latest version.
     */
    public Role(List<RoleEntity> roleVersions) {
        // Head of the list as the current version
        this(roleVersions.get(0));

        // The rest of the list as the history of this entity
        var olderVersions = roleVersions.stream().skip(1).map(Role::new).toList();
        if(!olderVersions.isEmpty())
            this.history = new HistoryOfRole(olderVersions);
    }
}

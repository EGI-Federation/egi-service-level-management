package egi.checkin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;

import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.net.URL;
import java.net.MalformedURLException;
import java.time.Instant;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import egi.eu.IntegratedManagementSystemConfig;
import egi.eu.ActionException;
import egi.eu.model.RoleInfo;
import egi.checkin.model.*;


/**
 * Check-in AAI
 */
public class Checkin {

    private static final Logger log = Logger.getLogger(Checkin.class);
    private static CheckinService checkin;
    private static Map<String, CheckinUser> voMembers;  // Does not cache assigned roles
    private static long voMembersUpdatedAt = 0;         // Milliseconds since epoch
    private static CheckinRoleList roleRecords;
    private static long rolesUpdatedAt = 0;             // Milliseconds since epoch

    private CheckinConfig checkinConfig;
    private IntegratedManagementSystemConfig imsConfig;

    // Pass to service in header x-test-stub, selects mock stub in unit/integration tests
    private String stub;


    /***
     * Check if the VO members are cached, and the cache is not stale
     * @return True if VO members are available in the cache
     */
    private boolean voMembersCached() {
        if(null == Checkin.voMembers)
            return false;

        final long millisecondsSinceEpoch = Instant.now().toEpochMilli();
        final boolean stale = Checkin.voMembersUpdatedAt + this.checkinConfig.cacheMembers() < millisecondsSinceEpoch;
        return !stale;
    }

    /***
     * Check if the role records are cached, and the cache is not stale
     * @return True if role records are available in the cache
     */
    private boolean rolesCached() {
        if(null == roleRecords)
            return false;

        final long millisecondsSinceEpoch = Instant.now().toEpochMilli();
        final boolean stale = rolesUpdatedAt + this.checkinConfig.cacheRoles() < millisecondsSinceEpoch;
        return !stale;
    }

    /***
     * Ensure no role records are cached (need to reload them on next API call)
     */
    private void invalidateCachedRoleRecords() {
       Checkin.roleRecords = null;
       Checkin.rolesUpdatedAt = 0;
    }

    /**
     * Prepare REST client for EGI Check-in.
     * @return true on success
     */
    public boolean init(CheckinConfig checkinConfig, IntegratedManagementSystemConfig imsConfig, String stub) {

        this.stub = stub;

        if(null != checkin)
            return true;

        this.checkinConfig = checkinConfig;
        this.imsConfig = imsConfig;

        MDC.put("checkinServer", this.checkinConfig.server());

        log.debug("Obtaining REST client for EGI Check-in");

        // Check if OIDC authentication URL is valid
        URL urlCheckin;
        try {
            urlCheckin = new URL(this.checkinConfig.server());
            urlCheckin = new URL(urlCheckin.getProtocol(), urlCheckin.getHost(), urlCheckin.getPort(), "");
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for EGI Check-in
            var rcb = RestClientBuilder.newBuilder().baseUrl(urlCheckin);
            checkin = rcb.build(CheckinService.class);

            MDC.remove("checkinServer");

            return true;
        }
        catch(IllegalStateException ise) {
            log.error(ise.getMessage());
        }
        catch (RestClientDefinitionException rcde) {
            log.error(rcde.getMessage());
        }

        return false;
    }

    /***
     * Retrieve information about authenticated user.
     * @param token Check-in access token
     * @return User information
     */
    public Uni<CheckinUser> getUserInfoAsync(String token) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        return checkin.getUserInfoAsync(token, stub);
    }

    /***
     * List all groups and virtual organizations (VOs).
     * The configured Check-in credentials are usually scoped to just one VO.
     * @return List of all groups
     */
    public Uni<CheckinGroupList> listGroupsAsync() {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        var header = getBasicAuthHeader();
        var coId = checkinConfig.coId();
        return checkin.listAllGroupsAsync(header, stub, coId);
    }

    /***
     * List all members of a virtual organization (VO).
     * Although multiple membership records can exist for a user, e.g. with different
     * start/until dates and different statuses, this function returns just one
     * {@link CheckinUser} per user.
     * @return List of all active VO members
     */
    public Uni<List<CheckinUser>> listVoMembersAsync(String voName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        MDC.put("voName", voName);
        MDC.put("coId", this.checkinConfig.coId());

        // First check if we have them cached
        if(voMembersCached()) {
            // We have a cache, and it's not stale
            log.info("Using cached VO members");
            var users = Checkin.voMembers;
            List<CheckinUser> userList = new ArrayList<>(users.values());
            return Uni.createFrom().item(userList);
        }

        Uni<List<CheckinUser>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                log.info("Getting VO members");
                return getGroupMembersAndRolesAsync(voName);
            })
            .chain(voRoles -> {
                // Got VO role records, keep just the membership ones
                var members = filterList(voRoles.records, role -> role.role.equals("member"));

                Map<String, CheckinUser> users = new HashMap<>();
                for(var role : members) {
                    if(role.deleted || !role.status.equalsIgnoreCase("Active"))
                        // Inactive membership record, skip
                        continue;

                    var user = new CheckinUser(role);
                    if(!users.containsKey(user.checkinUserId))
                        users.put(user.checkinUserId, user);
                }

                if(this.checkinConfig.traceRoles())
                    logGroupMembers(members, users, false);

                // Cache VO member list
                Checkin.voMembers = users;
                Checkin.voMembersUpdatedAt = Instant.now().toEpochMilli();

                // Return VO members
                List<CheckinUser> userList = new ArrayList<>(users.values());
                return Uni.createFrom().item(userList);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get VO members");
            });

        return result;
    }

    /***
     * List all members of a group or virtual organization (VO).
     * Although multiple membership records can exist for a user, e.g. with different
     * start/until dates and different statuses, this function returns just one per user.
     * @return List of all active group members, see also {@link CheckinUser}
     */
    public Uni<List<CheckinUser>> listGroupMembersAsync(String groupName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        Uni<List<CheckinUser>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Check-in does not enforce that users in a group are enrolled in the group's parent VO.
                // Therefore, we must check ourselves and only return group members that are also
                // members of the VO. This means we need the list of VO members, even if we are being
                // called just to list members of the configured group.
                return listVoMembersAsync(this.imsConfig.vo());
            })
            .chain(vom -> {
                // VO members are now cached, get group role records
                log.info("Getting members of group " + groupName);
                return getGroupMembersAndRolesAsync(groupName);
            })
            .chain(groupRoles -> {
                // Got group role records, keep just the membership ones
                var members = filterToGroupMembers(groupRoles, this.checkinConfig.traceRoles());
                if(null == members)
                    return Uni.createFrom().failure(new ActionException("notReady"));

                // Return group members
                List<CheckinUser> userList = new ArrayList<>(members.values());
                return Uni.createFrom().item(userList);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get group members");
            });

        return result;
    }

    /***
     * Filter records to the ones that indicate membership in the group.
     * @param groupRoles List of Check-in role records
     * @param logRecords Whether to dump the membership records in the log
     * @return List of member users, null on error
     */
    private Map<String, CheckinUser> filterToGroupMembers(CheckinRoleList groupRoles, boolean logRecords) {
        if(!voMembersCached()) {
            // We need the VO members to be already cached
            log.error("Cannot filter group members, VO members not loaded");
            return null;
        }

        // Keep just the records that mean membership in the group
        var members = filterList(groupRoles.records, role -> role.role.equals("member"));

        Map<String, CheckinUser> users = new HashMap<>();
        for(var role : members) {
            if(role.deleted || null == role.status || !role.status.equalsIgnoreCase("Active"))
                // Not an active membership record, skip
                continue;

            // Only include users that are members of the configured VO
            var user = new CheckinUser(role);
            if(null == user.checkinUserId || user.checkinUserId.isBlank())
                // Skip invalid users too
                continue;

            if(null != Checkin.voMembers && Checkin.voMembers.containsKey(user.checkinUserId) &&
                    !users.containsKey(user.checkinUserId))
                // This is a membership record, not a role record
                users.put(user.checkinUserId, user);
        }

        if(logRecords)
            logGroupMembers(members, users, true);

        return users;
    }

    /***
     * Add a user to a group.
     * @param checkinUserId The Id of the user to add to the group
     * @param groupName The group to add the user to
     * @return Details of membership record, see also {@link CheckinObject}
     */
    public Uni<CheckinObject> addUserToGroupAsync(String checkinUserId, String groupName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        final var coId = checkinConfig.coId();

        MDC.put("coId", coId);
        MDC.put("groupName", groupName);
        MDC.put("userId", checkinUserId);

        final var header = getBasicAuthHeader();
        final var deletedRoles = new ArrayList<CheckinRole>();

        Uni<CheckinObject> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Check-in allows multiple role membership records for the same role.
                // However, once there are multiple records, with (at least) one being marked deleted,
                // attempts to remove the role from the user (by marking it deleted) will fail,
                // as there is already a record marked deleted for the role. Therefore, before we
                // add a new record for the role, we must check whether there is a record for this role
                // that is marked deleted, and if so restore that instead of adding a new one.
                // First, get the group membership records.
                return getGroupMembersAndRolesAsync(groupName);
            })
            .chain(roles -> {
                // Got group membership records
                var deleted = filterList(roles.records,
                                         role -> checkinUserId.equals(role.person.checkinUserId()) &&
                                                 role.role.equals("member") &&
                                                 (role.deleted || role.status.equalsIgnoreCase("Deleted")) &&
                                                 null == role.from && null == role.until);

                if(!deleted.isEmpty()) {
                    // Deleted membership record found, restore it
                    var deletedRole = deleted.get(0);
                    MDC.put("roleId", deletedRole.roleId);

                    log.info("Restore membership record");

                    // Unlike CheckinService::addUserRoleAsync, CheckinService::updateUserRoleAsync will not
                    // return a CheckinObject. Save the details of the deleted role, so we can construct one.
                    deletedRoles.add(deletedRole);

                    // Restore group membership
                    var restoreRoles = new CheckinRoleList(checkinUserId, groupName, coId, "member", "Active");
                    return checkin.updateUserRoleAsync(header, stub, deletedRole.roleId, restoreRoles);
                }

                // Signal that we need to add a new membership record
                return Uni.createFrom().nullItem();
            })
            .chain(updated -> {
                // Got the empty response from the role update or null,
                // which means we need to add a new membership record
                if(null == updated) {
                    // No deleted membership record found, add a new one
                    log.info("Add membership record");

                    var addRoles = new CheckinRoleList(checkinUserId, groupName, coId, "member", "Active");
                    return checkin.addUserRoleAsync(header, stub, addRoles);
                }

                // Uniformize the response of the add and update Check-in endpoints
                var deletedRole = deletedRoles.get(0);
                var updatedObject = new CheckinObject();
                updatedObject.kind = "UpdatedObject";
                updatedObject.type = "CoPersonRole";
                updatedObject.Id = Long.toString(deletedRole.roleId);

                return Uni.createFrom().item(updatedObject);
            })
            .chain(updated -> {
                // Success, invalidate cached role records
                invalidateCachedRoleRecords();
                return Uni.createFrom().item(updated);
            })
            .onFailure().recoverWithUni(e -> {
                log.errorf("Failed to %s membership record", deletedRoles.isEmpty() ? "add" : "restore");

                // Check if this is a 400 error, if so pass the messages
                // detailing what is wrong with which field to the caller
                var fieldErrors = getBadRequestFieldErrors(e);
                if(null != fieldErrors) {
                    // Use the first field error as the description
                    String description = null;
                    String field = null;
                    for (var entry : fieldErrors.entrySet()) {
                        field = entry.getKey();
                        description = entry.getValue();
                        break;
                    }

                    if(null != field && null != description) {
                        var ae = new ActionException("badRequest", description, Tuple2.of("field", field));
                        return Uni.createFrom().failure(ae);
                    }
                }

                return Uni.createFrom().failure(e);
            });

        return result;
    }

    /***
     * Remove a user from a group.
     * @param checkinUserId The Id of the user to remove from the group
     * @param groupName The group to remove the user from
     * @return True on success
     */
    public Uni<Boolean> removeUserFromGroupAsync(String checkinUserId, String groupName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        final var coId = checkinConfig.coId();

        MDC.put("coId", coId);
        MDC.put("groupName", groupName);
        MDC.put("userId", checkinUserId);

        final var header = getBasicAuthHeader();
        final var deletedRoles = new ArrayList<CheckinRole>();

        Uni<Boolean> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // First, get the group membership records
                return getGroupMembersAndRolesAsync(groupName);
            })
            .chain(roles -> {
                // Got group membership records
                var active = filterList(roles.records,
                                        role -> checkinUserId.equals(role.person.checkinUserId()) &&
                                                role.role.equals("member") &&
                                                !role.deleted && !role.status.equalsIgnoreCase("Deleted") &&
                                                null == role.from && null == role.until);

                if(!active.isEmpty()) {
                    // Active membership record found, delete it
                    var activeRole = active.get(0);
                    MDC.put("roleId", activeRole.roleId);

                    log.info("Delete membership record");

                    // Delete group membership
                    var deleteRoles = new CheckinRoleList(checkinUserId, groupName, coId, "member", "Deleted");
                    return checkin.updateUserRoleAsync(header, stub, activeRole.roleId, deleteRoles);
                }

                // Nothing to do
                return Uni.createFrom().item("");
            })
            .chain(updated -> {
                // Membership record marked deleted, success
                // Invalidate cached role records
                invalidateCachedRoleRecords();
                return Uni.createFrom().item(true);
            })
            .onFailure().recoverWithUni(e -> {
                log.error("Failed to delete membership record");

                // Check if this is a 400 error, if so pass the messages
                // detailing what is wrong with which field to the caller
                var fieldErrors = getBadRequestFieldErrors(e);
                if(null != fieldErrors) {
                    // Use the first field error as the description
                    String description = null;
                    String field = null;
                    for (var entry : fieldErrors.entrySet()) {
                        field = entry.getKey();
                        description = entry.getValue();
                        break;
                    }

                    if(null != field && null != description) {
                        var ae = new ActionException("badRequest", description, Tuple2.of("field", field));
                        return Uni.createFrom().failure(ae);
                    }
                }

                return Uni.createFrom().failure(e);
            });

        return result;
    }

    /***
     * List all users with assigned roles in a group.
     * @param groupName The group under which assigned roles are considered
     * @param roleName Only return users holding roles that match this expression.
     *                 If empty or null, all users holding roles are returned.
     *                 Note: Using this parameter means the returned users will not have
     *                 all their roles reported, just the ones matching this expression.
     * @return List of all users holding effective roles in the group, see also {@link CheckinUser}
     */
    public Uni<List<CheckinUser>> listUsersWithGroupRolesAsync(String groupName, String roleName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        Uni<List<CheckinUser>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Check-in does not enforce that users in a group are enrolled in the group's parent VO.
                // Therefore, we must check ourselves and only return group members that are also
                // members of the VO. This means we need the list of VO members, even if we are being
                // called just to list members of the configured group.
                return listVoMembersAsync(this.imsConfig.vo());
            })
            .chain(vom -> {
                // VO members are now cached, get group role records
                log.info("Getting users with roles in group " + groupName);
                return getGroupMembersAndRolesAsync(groupName);
            })
            .chain(groupRoles -> {
                // Got group role records, keep just the role ones
                var usersWithRoles = filterToUsersWithGroupRoles(groupRoles, roleName, this.checkinConfig.traceRoles());
                if(null == usersWithRoles)
                    return Uni.createFrom().failure(new ActionException("notReady"));

                // Return users with roles
                List<CheckinUser> users = usersWithRoles.values().stream().toList();
                return Uni.createFrom().item(users);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get users with roles");
            });

        return result;
    }

    /***
     * Filter records to the ones that indicate assigned roles in the group.
     * @param groupRoles List of Check-in role records
     * @param roleName Only return users holding roles that match this expression.
     *                 If empty or null, all users holding roles are returned.
     *                 Note: Using this parameter means the returned users will not have
     *                 all their roles reported, just the ones matching this expression.
     * @param logRecords Whether to dump the records in the log
     * @return List of users holding roles in the group, null on error.
     *         Unlike the cached list of VO members, the users in the returned list
     *         will have their <b>roles</b> field filled.
     */
    private Map<String, CheckinUser> filterToUsersWithGroupRoles(CheckinRoleList groupRoles, String roleName, boolean logRecords) {
        if(!voMembersCached()) {
            // We need the VO members to be already cached
            log.error("Cannot filter group roles, VO members not loaded");
            return null;
        }

        // When determining assigned roles, ensure the user is a member in both the VO and the group
        // The VO members are cached, but the group members we need to calculate
        var members = filterToGroupMembers(groupRoles, false);

        // Keep just the records that mean assigned role in the group
        // If an expression is specified, consider just matching roles
        final String rexRole = null != roleName ? roleName.replace("-", "\\-") + ".*" : null;
        var records = filterList(groupRoles.records, role -> {
            if(role.checkIfRole()) {
                return null == roleName || roleName.isBlank() || role.role.matches(rexRole);
            }

            return false;
        });

        Map<String, CheckinUser> users = new HashMap<>();    // Users with assigned roles
        for(var role : records) {
            if(role.deleted || !role.status.equalsIgnoreCase("Active"))
                // Not an active role record, skip
                continue;

            // Only include users that are members of both the VO and the group
            var user = new CheckinUser(role);
            if(null == user.checkinUserId || user.checkinUserId.isBlank())
                // Skip invalid users too
                continue;

            if(null != Checkin.voMembers && Checkin.voMembers.containsKey(user.checkinUserId) &&
                    members.containsKey(user.checkinUserId)) {
                // The user mentioned in this role record is both a VO and group member
                if(users.containsKey(user.checkinUserId)) {
                    // This user already holds some role, add this role too
                    var existingUser = users.get(user.checkinUserId);
                    if(null == existingUser) {
                        log.error("User should already hold some role(s)");
                        continue;
                    }

                    // Store role name in the user
                    existingUser.addRole(role.role);
                    continue;
                }

                // This user does not yet hold any role, add it
                user.addRole(role.role);
                users.put(user.checkinUserId, user);
            }
        }

        if(logRecords)
            logGroupRoles(records, users);

        return users;
    }

    /***
     * List all roles currently assigned in a group.
     * @param groupName The group under which assigned roles are considered
     * @param roleName Only return roles that begin with this string fragment.
     *                 If empty or null, all users holding roles are returned.
     *                 Note: Using this parameter means the returned users will not have
     *                 all their roles reported, just the ones matching this expression.
     * @return List of all users holding effective roles in the group, see also {@link CheckinUser}
     */
    public Uni<List<RoleInfo>> listGroupRolesAsync(String groupName, String roleName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        Uni<List<RoleInfo>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Check-in does not enforce that users in a group are enrolled in the group's parent VO.
                // Therefore, we must check ourselves and only return group members that are also
                // members of the VO. This means we need the list of VO members, even if we are being
                // called just to list members of the configured group.
                return listVoMembersAsync(this.imsConfig.vo());
            })
            .chain(vom -> {
                // VO members are now cached, get group role records
                log.info("Getting assigned roles in group " + groupName);
                return getGroupMembersAndRolesAsync(groupName);
            })
            .chain(groupRoles -> {
                // Got group role records, keep just the role ones
                var rolesWithUsers = filterToGroupRoles(groupRoles, roleName, this.checkinConfig.traceRoles());
                if(null == rolesWithUsers)
                    return Uni.createFrom().failure(new ActionException("notReady"));

                // Return roles
                List<RoleInfo> roles = rolesWithUsers.values().stream().toList();
                return Uni.createFrom().item(roles);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get assigned roles");
            });

        return result;
    }

    /***
     * Filter records to the ones that indicate assigned roles in the group.
     * @param groupRoles List of Check-in role records
     * @param roleName Role name fragment or regular expression
     * @param logRecords Whether to dump the records in the log
     * @return Roles currently assigned in the group, null on error.
     *         Unlike the cached list of VO members, the users in the returned roles
     *         will have their <b>roles</b> field filled.
     */
    private Map<String, RoleInfo> filterToGroupRoles(CheckinRoleList groupRoles, String roleName, boolean logRecords) {
        if (!voMembersCached()) {
            // We need the VO members to be already cached
            log.error("Cannot filter group roles, VO members not loaded");
            return null;
        }

        // When determining assigned roles, ensure the user is a member in both the VO and the group
        // The VO members are cached, but the group members we need to calculate
        var members = filterToGroupMembers(groupRoles, false);

        // Keep just the records that mean assigned role in the group
        // If an expression is specified, consider just matching roles
        final String rexRole = null != roleName ? roleName.replace("-", "\\-") + ".*" : null;
        var records = filterList(groupRoles.records, role -> {
            if(role.checkIfRole()) {
                return null == roleName || roleName.isBlank() || role.role.matches(rexRole);
            }

            return false;
        });

        Map<String, CheckinUser> users = new HashMap<>(); // Users with assigned roles
        Map<String, RoleInfo> roles = new HashMap<>();   // Roles assigned in the group
        Map<String, Set<String>> roleUsers = new HashMap<>(); // Tracks which role is assigned to which users
        for(var roleRecord : records) {
            if(roleRecord.deleted || !roleRecord.status.equalsIgnoreCase("Active"))
                // Not an active role record, skip
                continue;

            // Only include users that are members of both the VO and the group
            var user = new CheckinUser(roleRecord);
            if(null == user.checkinUserId || user.checkinUserId.isBlank())
                // Skip invalid users too
                continue;

            if(null != Checkin.voMembers && Checkin.voMembers.containsKey(user.checkinUserId) &&
                    members.containsKey(user.checkinUserId)) {
                // The user mentioned in this role record is both a VO and group member
                RoleInfo role = null;
                Set<String> usersWithRole = null;
                if(roles.containsKey(roleRecord.role)) {
                    role = roles.get(roleRecord.role);
                    usersWithRole = roleUsers.get(roleRecord.role);
                }
                else {
                    // This is a role we see for the first time
                    role = new RoleInfo(roleRecord.role);
                    roles.put(roleRecord.role, role);

                    usersWithRole = new HashSet<>();
                    roleUsers.put(roleRecord.role, usersWithRole);
                }

                if(users.containsKey(user.checkinUserId)) {
                    // This user already holds some role, add this role too
                    var existingUser = users.get(user.checkinUserId);
                    if(null == existingUser) {
                        log.error("User should already hold some role(s)");
                        continue;
                    }

                    // Store role name in the user
                    existingUser.addRole(role.role);

                    if(!usersWithRole.contains(existingUser.checkinUserId))
                        // Store the user in the role
                        role.addUser(existingUser);

                    // Remember that we added this user to this role, as the role's user collection allows duplicates
                    usersWithRole.add(existingUser.checkinUserId);
                    continue;
                }

                // This user does not hold any role yet, add it
                users.put(user.checkinUserId, user);

                // Store role name in the user
                user.addRole(roleRecord.role);

                if(!usersWithRole.contains(user.checkinUserId))
                    // Store the user in the role
                    role.addUser(user);

                // Remember that we added this user to this role, as the role's user collection allows duplicates
                usersWithRole.add(user.checkinUserId);
            }
        }

        if(logRecords)
            logGroupRoles(records, users);

        return roles;
    }

    /***
     * Assign a role to a user.
     * @param checkinUserId The Id of the user to assign the role to
     * @param groupName The group in which the role is assigned.
     *                  The user must be included this group before the operation is allowed.
     * @return Details of membership record, see also {@link CheckinObject}
     */
    public Uni<CheckinObject> assignUserRoleAsync(String checkinUserId, String groupName, String roleName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        if(voMembersCached()) {
            if(!Checkin.voMembers.containsKey(checkinUserId)) {
                log.error("Unknown user");
                return Uni.createFrom().failure(new ActionException("notFound", "Unknown user"));
            }
        }

        final var coId = checkinConfig.coId();

        MDC.put("coId", coId);
        MDC.put("groupName", groupName);
        MDC.put("roleName", roleName);
        MDC.put("userId", checkinUserId);

        final var header = getBasicAuthHeader();
        final var deletedRoles = new ArrayList<CheckinRole>();

        Uni<CheckinObject> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // We need the list of VO members to be able to determine group membership
                return listVoMembersAsync(this.imsConfig.vo());
            })
            .chain(vom -> {
                // Check-in allows multiple role records for the same role.
                // However, once there are multiple records, with (at least) one being marked deleted,
                // attempts to remove the role from the user (by marking it deleted) will fail,
                // as there is already a record marked deleted for the role. Therefore, before we
                // add a new record for the role, we must check whether there is a record for this role
                // that is marked deleted, and if so restore that instead of adding a new one.
                // First, get the group role records.
                return getGroupMembersAndRolesAsync(groupName);
            })
            .chain(roles -> {
                // Got group role records, check if the user is a member of the group
                var members = filterToGroupMembers(roles, false);
                if(null == members || !members.containsKey(checkinUserId)) {
                    // The user is not member of the group, cannot assign roles
                    log.error("User not member of group, cannot assign role");
                    return Uni.createFrom().failure(new ActionException("badRequest", "Cannot assign role to non-member"));
                }

                return Uni.createFrom().item(roles);
            })
            .chain(roles -> {
                // Got group role records
                var deleted = filterList(roles.records,
                        role -> checkinUserId.equals(role.person.checkinUserId()) &&
                                role.checkIfRole() && role.role.equalsIgnoreCase(roleName) &&
                                (role.deleted || role.status.equalsIgnoreCase("Deleted")) &&
                                null == role.from && null == role.until);

                if(!deleted.isEmpty()) {
                    // Deleted role record found, restore it
                    var deletedRole = deleted.get(0);
                    MDC.put("roleId", deletedRole.roleId);

                    log.info("Restore role record");

                    // Unlike CheckinService::addUserRoleAsync, CheckinService::updateUserRoleAsync will not
                    // return a CheckinObject. Save the details of the deleted role, so we can construct one.
                    deletedRoles.add(deletedRole);

                    // Restore role
                    var restoreRoles = new CheckinRoleList(checkinUserId, groupName, coId, roleName, "member", "Active");
                    return checkin.updateUserRoleAsync(header, stub, deletedRole.roleId, restoreRoles);
                }

                // Signal that we need to add a new role record
                return Uni.createFrom().nullItem();
            })
            .chain(updated -> {
                // Got the empty response from the role update or null,
                // which means we need to add a new role record
                if(null == updated) {
                    // No deleted role record found, add a new one
                    log.info("Add role record");

                    var addRoles = new CheckinRoleList(checkinUserId, groupName, coId, roleName, "member", "Active");
                    return checkin.addUserRoleAsync(header, stub, addRoles);
                }

                // Uniformize the response of the add and update Check-in endpoints
                var deletedRole = deletedRoles.get(0);
                var updatedObject = new CheckinObject();
                updatedObject.kind = "UpdatedObject";
                updatedObject.type = "CoPersonRole";
                updatedObject.Id = Long.toString(deletedRole.roleId);

                return Uni.createFrom().item(updatedObject);
            })
            .chain(updated -> {
                // Success, invalidate role cache
                invalidateCachedRoleRecords();
                return Uni.createFrom().item(updated);
            })
            .onFailure().recoverWithUni(e -> {
                log.errorf("Failed to %s role record", deletedRoles.isEmpty() ? "add" : "restore");

                // Check if this is a 400 error, if so pass the messages
                // detailing what is wrong with which field to the caller
                var fieldErrors = getBadRequestFieldErrors(e);
                if(null != fieldErrors) {
                    // Use the first field error as the description
                    String description = null;
                    String field = null;
                    for (var entry : fieldErrors.entrySet()) {
                        field = entry.getKey();
                        description = entry.getValue();
                        break;
                    }

                    if(null != field && null != description) {
                        var ae = new ActionException("badRequest", description, Tuple2.of("field", field));
                        return Uni.createFrom().failure(ae);
                    }
                }

                return Uni.createFrom().failure(e);
            });

        return result;
    }

    /***
     * Revoke a role from a user.
     * @param checkinUserId The Id of the user to revoke the role from
     * @param groupName The group in which the role is assigned
     * @return True on success
     */
    public Uni<Boolean> revokeUserRoleAsync(String checkinUserId, String groupName, String roleName) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ActionException("notReady"));
        }

        final var coId = checkinConfig.coId();

        MDC.put("coId", coId);
        MDC.put("groupName", groupName);
        MDC.put("roleName", roleName);
        MDC.put("userId", checkinUserId);

        final var header = getBasicAuthHeader();
        final var deletedRoles = new ArrayList<CheckinRole>();

        Uni<Boolean> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // First, get the group membership records
                return getGroupMembersAndRolesAsync(groupName);
            })
            .chain(roles -> {
                // Got group membership records
                var active = filterList(roles.records,
                        role -> checkinUserId.equals(role.person.checkinUserId()) &&
                                role.checkIfRole() && role.role.equalsIgnoreCase(roleName) &&
                                !role.deleted && !role.status.equalsIgnoreCase("Deleted") &&
                                null == role.from && null == role.until);

                if(!active.isEmpty()) {
                    // Active role record found, delete it
                    var activeRole = active.get(0);
                    MDC.put("roleId", activeRole.roleId);

                    log.info("Delete role record");

                    // Delete role
                    var deleteRoles = new CheckinRoleList(checkinUserId, groupName, coId, roleName, "member", "Deleted");
                    return checkin.updateUserRoleAsync(header, stub, activeRole.roleId, deleteRoles);
                }

                // Nothing to do
                return Uni.createFrom().item("");
            })
            .chain(updated -> {
                // Role record marked deleted, success
                // Invalidate role cache
                invalidateCachedRoleRecords();
                return Uni.createFrom().item(true);
            })
            .onFailure().recoverWithUni(e -> {
                log.error("Failed to delete role record");

                // Check if this is a 400 error, if so pass the messages
                // detailing what is wrong with which field to the caller
                var fieldErrors = getBadRequestFieldErrors(e);
                if(null != fieldErrors) {
                    // Use the first field error as the description
                    String description = null;
                    String field = null;
                    for (var entry : fieldErrors.entrySet()) {
                        field = entry.getKey();
                        description = entry.getValue();
                        break;
                    }

                    if(null != field && null != description) {
                        var ae = new ActionException("badRequest", description, Tuple2.of("field", field));
                        return Uni.createFrom().failure(ae);
                    }
                }

                return Uni.createFrom().failure(e);
            });

        return result;
    }

    /***
     * List all role records for a group or virtual organization (VO).
     * Computes the role field.
     * @param groupName The group or VO to list records of.
     * @return List of records, see also {@link CheckinRoleList}
     */
    private Uni<CheckinRoleList> getGroupMembersAndRolesAsync(final String groupName) {

        final var coId = checkinConfig.coId();

        MDC.put("groupName", groupName);
        MDC.put("coId", coId);

        // First check if we have them cached
        final var group = this.imsConfig.group();
        if(null != group && !group.isBlank() && group.equals(groupName) && rolesCached()) {
            // We have a cache, and it's not stale
            log.info("Using cached group role records");
            return Uni.createFrom().item(roleRecords);
        }

        Uni<CheckinRoleList> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get role records
                log.debug("Getting Check-in records");
                var header = getBasicAuthHeader();
                return checkin.listGroupMembersAsync(header, stub, coId, groupName);
            })
            .chain(roles -> {
                // Got role records
                if(null != roles.records) {
                    // A role record can represent membership in a group or VO
                    // (when title is not set) or a role (when title is set to the role name
                    // and affiliation is "member")
                    for (var role : roles.records) {
                        // Set the role field to be "member" to signal membership in the group/VO
                        // and the name of the role otherwise, making this field easier to use than
                        // the affiliation/title pair.
                        if(null != role.title && !role.title.isBlank())
                            role.role = role.title.toLowerCase();
                        else
                            role.role = null != role.affiliation ? role.affiliation.toLowerCase() : null;

                        // Check consistency of deleted flag with the status
                        if(role.deleted && !role.status.equalsIgnoreCase("Deleted")) {
                            MDC.put("roleId", role.roleId);
                            MDC.put("roleStatus", role.status);

                            log.warn("Check-in record is marked deleted but has inconsistent status");
                        }
                    }

                    MDC.remove("roleId");
                    MDC.remove("roleStatus");
                }

                // Success
                if(!this.checkinConfig.traceRoles())
                    log.debug("Got Check-in records");

                if(null != group && !group.isBlank() && group.equals(groupName)) {
                    // Cache group role records
                    roleRecords = roles;
                    rolesUpdatedAt = Instant.now().toEpochMilli();
                }

                return Uni.createFrom().item(roles);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get Check-in records");
            });

        return result;
    }

    /***
     * Log all membership records of a Check-in group or VO.
     * @param records The Check-in membership records for the group or VO
     * @param users The users identified to be members
     * @param onlyGroup Whether logging records only for users included in the configured group
     *                  or for all members of the configured VO.
     */
    private void logGroupMembers(List<CheckinRole> records, Map<String, CheckinUser> users, boolean onlyGroup) {

        log.infof("Found %d active members in %s %s", users.size(),
                onlyGroup ? "group" : "VO",
                onlyGroup ? this.imsConfig.group() : "");

        Format formatter = new SimpleDateFormat("yyyy-MM-dd");

        for(var role : records) {
            var checkinUserId = role.person.checkinUserId();
            if(null == checkinUserId)
                continue;

            var trace = "userId:" + checkinUserId;
            var user = users.get(checkinUserId);
            if(null == user && onlyGroup && voMembersCached())
                user = Checkin.voMembers.get(checkinUserId);

            MDC.put("roleId", role.roleId);
            MDC.put("roleStatus", role.status);
            MDC.put("userId", checkinUserId);

            if(null != user) {
                MDC.put("userFullName", user.fullName);
                trace = user.fullName + ", " + trace;
            }
            else
                MDC.remove("userFullName");

            if(null != role.from) {
                MDC.put("roleFrom", role.from);
                MDC.put("roleUntil", role.until);
                trace += String.format(" from:%s until:%s",
                        formatter.format(role.from),
                        formatter.format(role.until));
            }
            else {
                MDC.remove("roleFrom");
                MDC.remove("roleUntil");
            }

            log.infof("recordId:%d %s -> %s", role.roleId, role.status, trace);
        }

        MDC.remove("roleId");
        MDC.remove("roleStatus");
        MDC.remove("userId");
        MDC.remove("userFullName");
        MDC.remove("roleFrom");
        MDC.remove("roleUntil");
    }

    /***
     * Log all role records of a Check-in group.
     * @param records The Check-in role records for the group
     * @param users The users identified to hold roles in the group
     */
    private void logGroupRoles(List<CheckinRole> records, Map<String, CheckinUser> users) {

        log.infof("Found %d users with matching role(s) in group %s", users.size(), this.imsConfig.group());

        Format formatter = new SimpleDateFormat("yyyy-MM-dd");

        for(var role : records) {
            var checkinUserId = role.person.checkinUserId();
            if(null == checkinUserId)
                continue;

            var trace = "userId:" + checkinUserId;
            var user = users.get(checkinUserId);
            if(null == user && voMembersCached())
                user = Checkin.voMembers.get(checkinUserId);

            MDC.put("roleId", role.roleId);
            MDC.put("roleName", role.role);
            MDC.put("roleStatus", role.status);
            MDC.put("userId", role.person.Id);

            if(null != user) {
                MDC.put("userFullName", user.fullName);
                trace = user.fullName + ", " + trace;
            }
            else
                MDC.remove("userFullName");

            if(null != role.from) {
                MDC.put("roleFrom", role.from);
                MDC.put("roleUntil", role.until);
                trace += String.format(" from:%s until:%s",
                        formatter.format(role.from),
                        formatter.format(role.until));
            }
            else {
                MDC.remove("roleFrom");
                MDC.remove("roleUntil");
            }

            log.infof("recordId:%d %s:%s -> %s", role.roleId, role.status, role.role, trace);
        }

        MDC.remove("roleId");
        MDC.remove("roleName");
        MDC.remove("roleStatus");
        MDC.remove("userId");
        MDC.remove("userFullName");
        MDC.remove("roleFrom");
        MDC.remove("roleUntil");
    }

    /***
     * Check if exception is a bad request.
     * @param e The exception
     * @return Error messages for the fields that had errors
     */
    private HashMap<String, String> getBadRequestFieldErrors(Throwable e) {

        final var cause = e.getCause();
        if(cause instanceof CheckinServiceException) {
            final var ce = (CheckinServiceException)cause;
            final var response = ce.getResponse();
            final var status = Status.fromStatusCode(response.getStatus());

            if(Status.BAD_REQUEST == status) {
                // Extract field errors
                CheckinObject badRequest = null;
                try {
                    badRequest = new ObjectMapper().readValue(ce.responseBody(), CheckinObject.class);
                } catch (JsonProcessingException ex) {
                    return null;
                }

                var errorDetails = new HashMap<String, String>();
                if(null != badRequest.fieldErrors && !badRequest.fieldErrors.isEmpty()) {
                    for(var field : badRequest.fieldErrors.keySet()) {
                        for(var error : badRequest.fieldErrors.get(field)) {
                            // Keep only one error per field
                            errorDetails.put(field, error);
                            break;
                        }
                    }

                    return errorDetails;
                }
            }
        }

        return null;
    }

    /***
     * Filter a list with a predicate.
     * @param criteria The predicate to apply to each element
     * @param list The list to filter
     * @param <T> Type of list elements
     * @return Another list containing just the matching elements
     */
    private<T> List<T> filterList(List<T> list, Predicate<T> criteria) {
        return list.stream().filter(criteria).collect(Collectors.<T>toList());
    }

    /***
     * Build HTTP header for Basic Authentication.
     * @return Authorization HTTP header for the configured Check-in credentials
     */
    private String getBasicAuthHeader() {
        Base64.Encoder encoder = Base64.getEncoder();
        String credentials = checkinConfig.username() + ":" + checkinConfig.password();
        String encoded = encoder.encodeToString(credentials.getBytes());

        return "Basic "+ encoded;
    }
}

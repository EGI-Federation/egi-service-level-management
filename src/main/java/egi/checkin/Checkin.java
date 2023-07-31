package egi.checkin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
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

import egi.checkin.model.*;
import egi.eu.IntegratedManagementSystemConfig;
import egi.eu.ServiceException;
import egi.eu.ActionException;


/**
 * Check-in AAI
 */
public class Checkin {

    private static final Logger log = Logger.getLogger(Checkin.class);
    private static CheckinService checkin;
    private static Map<Long, UserInfo> voMembers;
    private static long voMembersUpdatedAt = 0; // milliseconds since epoch
    private CheckinConfig checkinConfig;
    private IntegratedManagementSystemConfig imsConfig;


    /***
     * Check if the VO members are cached, and the cache is not stale
     * @return True if VO members are available in the cache
     */
    private boolean voMembersCached() {
        if(null == voMembers)
            return false;

        final long millisecondsSinceEpoch = Instant.now().toEpochMilli();
        final boolean stale = voMembersUpdatedAt + this.checkinConfig.cacheMembers() < millisecondsSinceEpoch;
        return !stale;
    }

    /**
     * Prepare REST client for EGI Check-in.
     * @return true on success
     */
    public boolean init(CheckinConfig checkinConfig, IntegratedManagementSystemConfig imsConfig) {

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
     * Retrieve information about authenticated user
     * @param token Check-in access token
     * @return User information
     */
    public Uni<UserInfo> getUserInfoAsync(String token) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ServiceException("notReady"));
        }

        return checkin.getUserInfoAsync(token);
    }

    /***
     * List all groups and virtual organizations (VOs).
     * The configured Check-in credentials are usually scoped to just one VO.
     * @return List of all groups
     */
    public Uni<CheckinGroupList> listGroupsAsync() {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ServiceException("notReady"));
        }

        var header = getBasicAuthHeader();
        var coId = checkinConfig.coId();
        return checkin.listAllGroupsAsync(header, coId);
    }

    /***
     * List all members of a virtual organization (VO).
     * Although multiple membership records can exist for a user, e.g. with different
     * start/until dates and different statuses, this function returns just one
     * {@link UserInfo} per user.
     * @return List of all active VO members
     */
    public Uni<List<UserInfo>> listVoMembersAsync() {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ServiceException("notReady"));
        }

        MDC.put("vo", this.imsConfig.vo());
        MDC.put("coId", this.checkinConfig.coId());

        // First check if we have them cached
        if(voMembersCached()) {
            // We have a cache, and it's not stale
            log.info("Returning cached VO members");
            List<UserInfo> userList = new ArrayList<>(voMembers.values());
            return Uni.createFrom().item(userList);
        }

        Uni<List<UserInfo>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                log.info("Getting VO members");
                return getGroupMembersAsync(this.imsConfig.vo());
            })
            .chain(voRoles -> {
                // Got VO role records, keep just the membership ones
                var members = filterList(voRoles.records, role -> role.role.equals("member"));

                Map<Long, UserInfo> users = new HashMap<>();
                for(var role : members) {
                    if(role.deleted || !role.status.equalsIgnoreCase("Active"))
                        // Inactive membership record, skip
                        continue;

                    var user = new UserInfo(role);
                    if (!users.containsKey(user.checkinUserId))
                        users.put(user.checkinUserId, user);
                }

                // Cache VO member list
                voMembers = users;
                voMembersUpdatedAt = Instant.now().toEpochMilli();

                if(this.imsConfig.traceRoles())
                    logGroupMemberships(members, users, false);

                // Return VO members
                List<UserInfo> userList = new ArrayList<>(voMembers.values());
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
     * start/until dates and different statuses, this function returns just one
     * {@link UserInfo} per user.
     * @return List of all active group members
     */
    public Uni<List<UserInfo>> listGroupMembersAsync() {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ServiceException("notReady"));
        }

        Uni<List<UserInfo>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Check-in does not enforce that users in a group are enrolled in the group's parent VO.
                // Therefore, we must check ourselves and only return group members that are also
                // members of the VO. This means we need the list of VO members, even if we are being
                // called just to list members of the configured group.
                return listVoMembersAsync();
            })
            .chain(voMembers -> {
                // VO members are now cached, get group role records
                log.info("Getting members of group " + this.imsConfig.group());
                return getGroupMembersAsync(this.imsConfig.group());
            })
            .chain(groupRoles -> {
                // Got group role records, keep just the membership ones
                var members = filterList(groupRoles.records, role -> role.role.equals("member"));

                Map<Long, UserInfo> users = new HashMap<>();
                for (var role : members) {
                    if (role.deleted || !role.status.equalsIgnoreCase("Active"))
                        // Not an active membership record, skip
                        continue;

                    var user = new UserInfo(role);
                    if (voMembers.containsKey(user.checkinUserId) && !users.containsKey(user.checkinUserId))
                        // This is a membership record, not a role record
                        users.put(user.checkinUserId, user);
                }

                if(this.imsConfig.traceRoles())
                    logGroupMemberships(members, users, true);

                // Return group members
                List<UserInfo> userList = new ArrayList<>(users.values());
                return Uni.createFrom().item(userList);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get group members");
            });

        return result;
    }

    /***
     * List all members of a group or virtual organization (VO).
     * Computes the role field.
     * @param groupName The group or VO to list members of.
     * @return List of member users
     */
    private Uni<CheckinRoleList> getGroupMembersAsync(final String groupName) {

        final var coId = checkinConfig.coId();

        MDC.put("groupName", groupName);
        MDC.put("coId", coId);

        log.info("Getting membership records");

        Uni<CheckinRoleList> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get membership records
                var header = getBasicAuthHeader();
                return checkin.listGroupMembersAsync(header, coId, groupName);
            })
            .chain(roles -> {
                // Got membership records
                if(null != roles.records) {
                    // A membership record can represent membership in a group or VO
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

                            log.warn("Membership record is marked deleted but has inconsistent status");
                        }
                    }

                    MDC.remove("roleId");
                    MDC.remove("roleStatus");
                }

                // Success
                if(!this.imsConfig.traceRoles())
                    log.info("Got membership records");

                return Uni.createFrom().item(roles);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get membership records");
            });

        return result;
    }

    /***
     * Add a user to a group or virtual organization (VO).
     * @return Details of membership record
     */
    public Uni<CheckinObject> addUserToGroupAsync(long checkinUserId) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ServiceException("notReady"));
        }

        final var coId = checkinConfig.coId();

        MDC.put("coId", coId);
        MDC.put("checkinUserId", checkinUserId);

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
                // This means we need the list of group members first.
                return getGroupMembersAsync(this.imsConfig.group());
            })
            .chain(roles -> {
                // Got group membership records
                var deleted = filterList(roles.records,
                                         role -> checkinUserId == role.person.Id &&
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
                    var restoreRoles = new CheckinRoleList(checkinUserId, imsConfig.group(), coId, "member", "Active");
                    return checkin.updateUserRoleAsync(header, deletedRole.roleId, restoreRoles);
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

                    var addRoles = new CheckinRoleList(checkinUserId, imsConfig.group(), coId, "member", "Active");
                    return checkin.addUserRoleAsync(header, addRoles);
                }

                // Fake an updated object to uniformize the response of the add and update Check-in endpoints
                var deletedRole = deletedRoles.get(0);
                var updatedObject = new CheckinObject();
                updatedObject.kind = "UpdatedObject";
                updatedObject.type = "CoPersonRole";
                updatedObject.Id = Long.toString(deletedRole.roleId);

                return Uni.createFrom().item(updatedObject);
            })
            .onFailure().recoverWithUni(e -> {
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

                    if(null != field && null != description)
                        return Uni.createFrom().failure(new ActionException("badRequest", description,
                                                                Tuple2.of("field", field)));
                }

                return Uni.createFrom().failure(e);
            });

        return result;
    }

    /***
     * Check if exception is a bad request
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
     * Log all membership records of a Check-in group or VO
     * @param roleRecords The Check-in membership records for the group or VO
     * @param users The users identified to be members
     * @param onlyGroup Whether logging records only for users included in the configured group
     *                  or for all members of the configured VO.
     */
    private void logGroupMemberships(List<CheckinRole> roleRecords, Map<Long, UserInfo> users, boolean onlyGroup) {

        log.infof("Found %d active members in %s %s", users.size(),
                onlyGroup ? "group" : "VO",
                onlyGroup ? this.imsConfig.group() : "");

        Format formatter = new SimpleDateFormat("yyyy-MM-dd");

        for(var role : roleRecords) {
            var trace = "userId:" + role.person.Id;
            var user = users.get(role.person.Id);
            if(null == user && onlyGroup && voMembersCached())
                user = voMembers.get(role.person.Id);

            MDC.put("roleId", role.roleId);
            MDC.put("roleStatus", role.status);
            MDC.put("checkinUserId", role.person.Id);

            if(null != user) {
                trace = user.fullName + ", " + trace;

                MDC.put("userFullName", user.fullName);
                if(null != user.userId)
                    MDC.put("userId", user.userId);
            }

            if(null != role.from) {
                MDC.put("roleFrom", role.from);
                MDC.put("roleUntil", role.until);
                trace += String.format(" from:%s until:%s",
                        formatter.format(role.from),
                        formatter.format(role.until));
            }

            log.infof("recordId:%d %s -> %s", role.roleId, role.status, trace);
        }

        MDC.remove("roleId");
        MDC.remove("roleStatus");
        MDC.remove("checkinUserId");
        MDC.remove("userId");
        MDC.remove("userFullName");
        MDC.remove("roleFrom");
        MDC.remove("roleUntil");
    }

    /***
     * Filter a list with a predicate
     * @param criteria The predicate to apply to each element
     * @param list The list to filter
     * @param <T> Type of list elements
     * @return Another list containing just the matching elements
     */
    public<T> List<T> filterList(List<T> list, Predicate<T> criteria) {
        return list.stream().filter(criteria).collect(Collectors.<T>toList());
    }

    /***
     * Build HTTP header for Basic Authentication
     * @return Authorization HTTP header for the configured Check-in credentials
     */
    private String getBasicAuthHeader() {
        Base64.Encoder encoder = Base64.getEncoder();
        String credentials = checkinConfig.username() + ":" + checkinConfig.password();
        String encoded = encoder.encodeToString(credentials.getBytes());

        return "Basic "+ encoded;
    }
}

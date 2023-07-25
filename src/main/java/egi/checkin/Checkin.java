package egi.checkin;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import egi.checkin.model.*;
import egi.eu.IntegratedManagementSystemConfig;
import egi.eu.ServiceException;


/**
 * Check-in AAI
 */
public class Checkin {

    private static final Logger log = Logger.getLogger(Checkin.class);
    private static CheckinService checkin;
    private static Map<Integer, UserInfo> voMembers;
    private static long voMembersUpdatedAt = 0; // milliseconds since epoch
    private String instance;
    private CheckinConfig checkinConfig;
    private IntegratedManagementSystemConfig imsConfig;


    /***
     * Return URL to the configured Check-in instance
     * @return URL to Check-in instance
     */
    public String instance() { return instance; }

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

        instance = ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String.class);

        MDC.put("oidc-server", instance);

        log.debug("Obtaining REST client for EGI Check-in");

        // Check if OIDC authentication URL is valid
        URL urlCheckin;
        try {
            urlCheckin = new URL(instance);
            urlCheckin = new URL(urlCheckin.getProtocol(), urlCheckin.getHost(), urlCheckin.getPort(), "");
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for EGI Check-in
            var rcb = RestClientBuilder.newBuilder().baseUrl(urlCheckin);
            checkin = rcb.build(CheckinService.class);

            MDC.remove("oidc-server");

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
     * List all members of a group or virtual organization (VO).
     * Although multiple membership records can exist for a user, e.g. with different
     * start/until dates and different statuses, this function returns just one
     * {@link BasicUserInfo} per user.
     * @param onlyGroup Whether to return only users included in the configured group
     *                  or all members of the configured VO.
     * @return List of all active members
     */
    public Uni<List<UserInfo>> listGroupMembersAsync(boolean onlyGroup) {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ServiceException("notReady"));
        }

        final var coId = checkinConfig.coId();
        final var traceRoles = this.imsConfig.traceRoles();

        MDC.put("coId", coId);
        MDC.put("onlyGroup", onlyGroup);

        // If we were asked for VO members, first check if we have them cached
        if(!onlyGroup && voMembersCached()) {
            // We have a cache, and it's not stale
            log.info("Returning cached VO members");
            List<UserInfo> userList = new ArrayList<>(voMembers.values());
            return Uni.createFrom().item(userList);
        }

        Uni<List<UserInfo>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Check-in does not enforce that users in a group are enrolled in the group's parent VO.
                // Therefore, we must check ourselves and only return group members that are also
                // members of the VO. This means we need the list of VO members, even if we are being
                // called just to list members of the configured group.
                if(!voMembersCached()) {
                    log.info("Getting VO members");
                    return getGroupMembersAsync(this.imsConfig.vo());
                }

                return Uni.createFrom().nullItem();
            })
            .chain(voRoles -> {
                if(null != voRoles) {
                    // Got VO role records, keep just the membership ones
                    Map<Integer, UserInfo> users = new HashMap<>();

                    var members = filterList(voRoles.records, role -> role.role.equals("member"));

                    for(var role : members) {
                        if(role.deleted || !role.status.equalsIgnoreCase("Active"))
                            // Not an active membership record, skip
                            continue;

                        var user = new UserInfo(role);
                        if(!users.containsKey(user.checkinUserId)) {
                            // This is a membership record, not a role record
                            users.put(user.checkinUserId, user);
                        }
                    }

                    // Cache VO member list
                    voMembers = users;
                    voMembersUpdatedAt = Instant.now().toEpochMilli();

                    if(traceRoles)
                        logGroupMemberships(members, users, false);

                    if(!onlyGroup)
                        return Uni.createFrom().nullItem();
                }

                // Get membership records
                log.info("Getting members of group " + imsConfig.group());
                return getGroupMembersAsync(imsConfig.group());
            })
            .chain(groupRoles -> {
                if(null != groupRoles) {
                    // Got group role records, keep just the membership ones
                    Map<Integer, UserInfo> users = new HashMap<>();

                    var members = filterList(groupRoles.records, role -> role.role.equals("member"));

                    for (var role : members) {
                        if (role.deleted || !role.status.equalsIgnoreCase("Active"))
                            // Not an active membership record, skip
                            continue;

                        var user = new UserInfo(role);
                        if (voMembers.containsKey(user.checkinUserId) && !users.containsKey(user.checkinUserId)) {
                            // This is a membership record, not a role record
                            users.put(user.checkinUserId, user);
                        }
                    }

                    if(traceRoles)
                        logGroupMemberships(members, users, true);

                    List<UserInfo> userList = new ArrayList<>(users.values());
                    return Uni.createFrom().item(userList);
                }

                // If we get here, then we need to return the VO members
                // And those were loaded in the previous step
                List<UserInfo> userList = new ArrayList<>(voMembers.values());
                return Uni.createFrom().item(userList);
            })
            .onFailure().invoke(e -> {
                log.errorf("Failed to get %s members", onlyGroup ? "group" : "VO");
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

        log.info("Getting membership records");

        Uni<CheckinRoleList> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get membership records
                var header = getBasicAuthHeader();
                return checkin.listGroupMembersAsync(header, coId, groupName);
            })
            .chain(roles -> {
                // Got membership records, success
                log.info("Got membership records");

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

                return Uni.createFrom().item(roles);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get membership records");
            });

        return result;
    }

    /***
     * Log all membership records of a Check-in group or VO
     * @param roleRecords The Check-in membership records for the group or VO
     * @param users The users identified to be members
     * @param onlyGroup Whether logging records only for users included in the configured group
     *                  or for all members of the configured VO.
     */
    private void logGroupMemberships(List<CheckinRole> roleRecords, Map<Integer, UserInfo> users, boolean onlyGroup) {

        log.infof("Found %d active members in %s %s", users.size(),
                onlyGroup ? "group" : "VO",
                onlyGroup ? this.imsConfig.group() : "");

        Format formatter = new SimpleDateFormat("yyyy-MM-dd");

        for(var role : roleRecords) {
            var user = users.containsKey(role.person.Id) ? users.get(role.person.Id) : null;
            var trace = String.format("userId:%d recordId:%d", role.person.Id, role.roleId);

            MDC.put("roleId", role.roleId);
            MDC.put("roleStatus", role.status);
            MDC.put("checkinUserId", role.person.Id);

            if(null != user) {
                trace = user.fullName + " " + trace;

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

            log.info(role.status + ": " + trace);
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

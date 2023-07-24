package egi.checkin;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.Format;
import java.text.SimpleDateFormat;
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
    private String instance;
    private CheckinConfig checkinConfig;
    private IntegratedManagementSystemConfig imsConfig;


    /***
     * Return URL to the configured Check-in instance
     * @return URL to Check-in instance
     */
    public String instance() { return instance; }

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
    public Uni<CheckinGroupList> listAllGroupsAsync() {
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
     * @return List of all active members
     */
    public Uni<List<BasicUserInfo>> listGroupMembersAsync() {
        if(null == checkin) {
            log.error("Check-in not ready, call init() first");
            return Uni.createFrom().failure(new ServiceException("notReady"));
        }

        final var coId = checkinConfig.coId();
        final var groupName = this.imsConfig.group();
        final var traceRoles = this.imsConfig.traceRoles();

        MDC.put("coId", coId);
        MDC.put("groupName", groupName);

        log.infof("Getting group members");

        Uni<List<BasicUserInfo>> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get membership records
                return getMemberRecordsAsync();
            })
            .chain(roles -> {
                // Got membership records, keep just membership ones
                List<BasicUserInfo> users = new ArrayList<>();
                Map<Integer, BasicUserInfo> um = new HashMap<>();

                var members = filterList(roles.records, role -> role.role.equals("member"));

                for(var role : members) {
                    if(role.deleted || !role.status.equalsIgnoreCase("Active"))
                        // Not an active membership record, skip
                        continue;

                    var user = new BasicUserInfo(role);
                    if(!um.containsKey(user.checkinUserId)) {
                        // This is a membership record, not a role record
                        users.add(user);

                        // Remember the user, so we include the user only once
                        um.put(user.checkinUserId, user);
                    }
                }

                if(traceRoles)
                    logGroupMembers(members, um);

                return Uni.createFrom().item(users);
            })
            .onFailure().invoke(e -> {
                log.error("Failed to get group members");
            });

        return result;
    }

    /***
     * List all members of a group or virtual organization (VO)
     * Computes the role field
     * @return List of all member users
     */
    private Uni<CheckinRoleList> getMemberRecordsAsync() {

        final var coId = checkinConfig.coId();
        final var groupName = this.imsConfig.group();

        MDC.put("coId", coId);
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
     * Log members of a Check-in group
     * @param members The Check-in membership records for the group
     * @param users The identified users that are members
     */
    private void logGroupMembers(List<CheckinRole> members, Map<Integer, BasicUserInfo> users) {

        log.infof("Found %d active members of group %s", users.size(), this.imsConfig.group());

        Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for(var role : members) {
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
                trace += String.format(" from:(%s) until:(%s)",
                        formatter.format(role.from),
                        formatter.format(user.lastName));
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

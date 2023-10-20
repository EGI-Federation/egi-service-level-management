package egi.eu;

import egi.eu.entity.RoleLogEntity;
import egi.eu.model.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.logging.Logger;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import egi.checkin.CheckinConfig;
import egi.checkin.model.CheckinUser;
import egi.eu.entity.UserEntity;
import egi.eu.entity.RoleEntity;


/***
 * Resource for user queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class Users extends BaseResource {

    private static final Logger log = Logger.getLogger(Users.class);

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

    @Inject
    CheckinConfig checkinConfig;

    @Inject
    IntegratedManagementSystemConfig imsConfig;

    @Inject
    Mutiny.SessionFactory sf;

    // Parameter(s) to add to all endpoints
    @RestHeader(TEST_STUB)
    @Parameter(hidden = true)
    @Schema(defaultValue = "default")
    String stub;


    /***
     * Page of users
     */
    public static class PageOfUsers extends Page<UserInfo, Long> {
        public PageOfUsers(String baseUri, long from, int limit, List<CheckinUser> checkinUsers) {
            super();

            var users = checkinUsers.stream().map(UserInfo::new).collect(Collectors.toList());
            populate(baseUri, from, limit, users, true);
        }
    }

    /***
     * Page of roles
     */
    public static class PageOfRoles extends Page<Role, Long> {
        public PageOfRoles(String baseUri, long from, int limit, List<Role> roles) {
            // Always loads all (from database)
            super(baseUri, from, limit, roles, true); }
    }

    /***
     * Page of role infos
     */
    public static class PageOfRoleInfos extends Page<RoleInfo, Long> {
        public PageOfRoleInfos(String baseUri, long from, int limit, List<RoleInfo> roles) {
            // Always loads all (from Check-in)
            super(baseUri, from, limit, roles, true); }
    }

    /***
     * Page of role assignment logs
     */
    public static class PageOfRoleLogs extends Page<RoleLog, LocalDateTime> {
        public PageOfRoleLogs(String baseUri, LocalDateTime from, int limit, List<RoleLogEntity> logs_) {
            super();

            var logs = logs_.stream().map(RoleLog::new).collect(Collectors.toList());
            populate(baseUri, from, limit, logs, false);
        }
    }


    /***
     * Constructor
     */
    public Users() { super(log); }

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

    /**
     * Retrieve information about current user.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess({@link UserInfo}) or an ActionError entity
     */
    @GET
    @Path("/user/info")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "getUserInfo",  summary = "Retrieve information about authenticated user")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UserInfo.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> getUserInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));

        log.info("Getting user info");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // Get user info
                return this.checkin.getUserInfoAsync(auth);
            })
            .chain(checkinUser -> {
                // Got user info, success
                log.info("Got user info");

                var user = new UserInfo(checkinUser);
                var roles = identity.getRoles();
                if(null != roles && !roles.isEmpty()) {
                    user.roles = new HashSet<>(roles);

                    // Do not return pseudo roles
                    user.roles.remove(Role.IMS_USER);
                    user.roles.remove(Role.IMS_ADMIN);
                    user.roles.remove(Role.PROCESS_MEMBER);
                }

                return Uni.createFrom().item(Response.ok(user).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get user info");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * List users that are members of the configured VO.
     * @param auth The access token needed to call the service.
     * @param onlyProcess Filter out users that are not included in the configured Check-in group
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfUsers}) or an ActionError entity
     */
    @GET
    @Path("/users")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.IMS_USER})
    @Operation(operationId = "listUsers",  summary = "List IMS users")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfUsers.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listUsers(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                   @Context UriInfo uriInfo,
                                   @Context HttpHeaders httpHeaders,

                                   @RestQuery("onlyProcess")
                                   @Parameter(description = "Return only members of the SLM process")
                                   @Schema(defaultValue = "false")
                                   boolean onlyProcess,

                                   @RestQuery("from")
                                   @Parameter(description = "Skip the first given number of results")
                                   @Schema(defaultValue = "0")
                                   long from,

                                   @RestQuery("limit")
                                   @Parameter(description = "Restrict the number of results returned")
                                   @Schema(defaultValue = "100")
                                   int limit_)
    {
        final int limit = (0 == limit_) ? 100 : limit_;

        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("onlyProcess", onlyProcess);
        addToDC("from", from);
        addToDC("limit", limit);

        log.info("Listing users");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // List users
                return onlyProcess ?
                        checkin.listGroupMembersAsync(this.imsConfig.group()) :
                        checkin.listVoMembersAsync(this.imsConfig.vo());
            })
            .chain(users -> {
                // Got users, success
                log.info("Got user list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfUsers(uri.toString(), from, limit, users);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list users");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * Add user to the configured group.
     * @param auth The access token needed to call the service.
     * @param userId The Check-in Id of the user to add to the group (unused).
     * @param user The user to add to the group.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/process/{userId}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "addUserToGroup",  summary = "Include user in the SLM process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Included",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> addUserToGroup(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                        @RestPath("userId")
                                        @Parameter(description = "Id of user to include in the process")
                                        String userId,

                                        User user)
    {
        var grant = new RoleGrant(new User(
            (String)identity.getAttribute(CheckinUser.ATTR_USERID),
            (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
            (String)identity.getAttribute(CheckinUser.ATTR_EMAIL)
        ));
        grant.assign = true;
        grant.role = Role.PROCESS_MEMBER;

        addToDC("userIdCaller", grant.changeBy.checkinUserId);
        addToDC("userNameCaller", grant.changeBy.fullName);
        addToDC("user", user);

        log.info("Adding user to group");

        if(null == user || null == user.checkinUserId || user.checkinUserId.isBlank()) {
            // User must be specified
            var ae = new ActionError("badRequest", "User is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        var added = new ArrayList<Boolean>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // Add user
                return checkin.addUserToGroupAsync(user.checkinUserId, this.imsConfig.group());
            })
            .chain(addedOrUpdated -> {
                // Added user, log it
                added.add(true);
                return logRoleAssignment(grant);
            })
            .chain(unused -> {
                // Added user logged, success
                log.info("Added user to group");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Included")).build());
            })
            .onFailure().recoverWithItem(e -> {
                if(!added.isEmpty()) {
                    // User added, but not logged
                    log.warn("Added user to group, but failed to log it");
                    return Response.ok(new ActionSuccess("Included, but not logged"))
                            .status(Response.Status.CREATED)
                            .build();
                }

                log.error("Failed to add user to group");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * Remove user to the configured group.
     * @param auth The access token needed to call the service.
     * @param userId The Check-in Id of the user to remove from the group (unused).
     * @param user The user to remove from the group.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/process/{userId}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "removeUserFromGroup",  summary = "Exclude user from the SLM process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Excluded",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> removeUserFromGroup(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                             @RestPath("userId")
                                             @Parameter(description = "Id of user to exclude from the process")
                                             String userId,

                                             User user)
    {
        var grant = new RoleGrant(new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL)
        ));
        grant.assign = false;
        grant.role = Role.PROCESS_MEMBER;

        addToDC("userIdCaller", grant.changeBy.checkinUserId);
        addToDC("userNameCaller", grant.changeBy.fullName);
        addToDC("user", user);

        log.info("Removing user from group");

        if(null == user || null == user.checkinUserId || user.checkinUserId.isBlank()) {
            // User must be specified
            var ae = new ActionError("badRequest", "User is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        var removed = new ArrayList<Boolean>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // Remove user
                return checkin.removeUserFromGroupAsync(user.checkinUserId, this.imsConfig.group());
            })
            .chain(success -> {
                // Removed user, log it
                removed.add(true);
                return logRoleAssignment(grant);
            })
            .chain(unused -> {
                // Removed user logged, success
                log.info("Removed user from group");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Excluded")).build());
            })
            .onFailure().recoverWithItem(e -> {
                if(!removed.isEmpty()) {
                    // User removed, but not logged
                    log.warn("Removed user from group, but failed to log it");
                    return Response.ok(new ActionSuccess("Excluded, but not logged"))
                            .status(Response.Status.CREATED)
                            .build();
                }

                log.error("Failed to remove user from group");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * List users that hold roles in the configured group.
     * Note: Membership in the group is not considered a role, but a prerequisite to holding a role.
     * @param auth The access token needed to call the service.
     * @param roleNameFragment Only return users holding this role. If empty or null,
     *                         all users holding roles are returned.
     *                         Note: Using this parameter means the returned users will not have
     *                               all their roles reported, just the ones matching this expression.
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfUsers}) or an ActionError entity
     */
    @GET
    @Path("/users/roles")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.IMS_USER })
    @Operation(operationId = "listUsersWithRoles",  summary = "List users with roles in the SLM process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfUsers.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listUsersWithRoles(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                            @Context UriInfo uriInfo,
                                            @Context HttpHeaders httpHeaders,

                                            @RestQuery("role")
                                            @Parameter(description = "Return only users holding roles matching this expression")
                                            String roleNameFragment,

                                            @RestQuery("from")
                                            @Parameter(description = "Skip the first given number of results")
                                            @Schema(defaultValue = "0")
                                            long from,

                                            @RestQuery("limit")
                                            @Parameter(description = "Restrict the number of results returned")
                                            @Schema(defaultValue = "100")
                                            int limit_)
    {
        final int limit = (0 == limit_) ? 100 : limit_;

        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("roleNameFragment", roleNameFragment);
        addToDC("from", from);
        addToDC("limit", limit);

        log.info("Listing users with roles");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // List users holding roles
                return checkin.listUsersWithGroupRolesAsync(this.imsConfig.group(), roleNameFragment);
            })
            .chain(users -> {
                // Got users holding roles, success
                log.info("Got users with roles");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfUsers(uri.toString(), from, limit, users);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list users with roles");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /***
     * Log the assignment/revocation of a role.
     * @param grant The role that was assigned/revoked and the users involved
     * @return True on success
     */
    private Uni<Void> logRoleAssignment(RoleGrant grant) {

        Uni<Void> result = sf.withTransaction((session, tx) -> { return
                // Find the users involved in this log entry
                UserEntity.findByCheckinUserIds(Arrays.asList(
                                grant.roleHolder.checkinUserId,
                                grant.changeBy.checkinUserId))
                    .chain(users -> {
                        // Got users with the specified Ids
                        var roleHolderL = filterList(users, user -> user.checkinUserId.equals(grant.roleHolder.checkinUserId));
                        var changeByL = filterList(users, user -> user.checkinUserId.equals(grant.changeBy.checkinUserId));

                        var roleHolder = roleHolderL.isEmpty() ? new UserEntity(grant.roleHolder) : roleHolderL.get(0);
                        var changeBy = changeByL.isEmpty() ? new UserEntity(grant.changeBy) : changeByL.get(0);

                        // Create new role assignment log entry
                        var newRoleLog = new RoleLogEntity(grant.role, grant.assign, roleHolder, changeBy);
                        return session.persist(newRoleLog);
                    });
            })
            .chain(unused -> {
                // Role grant logged, success
                return Uni.createFrom().voidItem();
            });

        return result;
    }

    /**
     * Assign a role to a user.
     * @param auth The access token needed to call the service.
     * @param userId The Check-in Id of the user to assign the role to (unused).
     * @param grant The role to assign and the user to assign to.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/role/{userId}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "assignRoleToUser",  summary = "Assign a role to a user",
               description ="To assign roles to a user, the user must be included in the SLM process.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Assigned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> assignRoleToUser(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                          @RestPath("userId")
                                          @Parameter(description = "Id of user to assign the role to")
                                          String userId,

                                          RoleGrant grant)
    {
        grant.changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", grant.changeBy.checkinUserId);
        addToDC("userNameCaller", grant.changeBy.fullName);
        addToDC("grant", grant);

        log.info("Assigning role to user");

        if(null == grant) {
            var ae = new ActionError("badRequest", "Missing role grant");
            return Uni.createFrom().item(ae.toResponse());
        }
        if(null == grant.roleHolder || null == grant.roleHolder.checkinUserId || grant.roleHolder.checkinUserId.isBlank()) {
            // Assignee must be specified
            var ae = new ActionError("badRequest", "Role holder is required");
            return Uni.createFrom().item(ae.toResponse());
        }
        if(null == grant.role || grant.role.isEmpty()) {
            // Role must be specified
            var ae = new ActionError("badRequest", "Role constant is required");
            return Uni.createFrom().item(ae.toResponse());
        }
        if(!grant.role.equalsIgnoreCase(Role.PROCESS_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.PROCESS_MANAGER) &&
           !grant.role.equalsIgnoreCase(Role.PROCESS_DEVELOPER) &&
           !grant.role.equalsIgnoreCase(Role.CATALOG_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.REPORT_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.UA_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.OLA_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.SLA_OWNER) ) {
            var ae = new ActionError("badRequest", "Unknown role", Tuple2.of("role", grant.role));
            return Uni.createFrom().item(ae.toResponse());
        }

        grant.assign = true;

        var assigned = new ArrayList<Boolean>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // Assign role
                return checkin.assignUserRoleAsync(grant.roleHolder.checkinUserId, this.imsConfig.group(), grant.role);
            })
            .chain(addedOrUpdated -> {
                // Assigned role, log it
                assigned.add(true);
                return logRoleAssignment(grant);
            })
            .chain(unused -> {
                // Role assignment logged, success
                log.info("Assigned role to user");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Assigned, logged")).build());
            })
            .onFailure().recoverWithItem(e -> {
                if(!assigned.isEmpty()) {
                    // Role assigned, but not logged
                    log.error("Assigned role, but failed to log it");
                    return Response.ok(new ActionSuccess("Assigned, but not logged"))
                                   .status(Response.Status.CREATED)
                                   .build();
                }

                log.error("Failed to assign role to user");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * Revoke a role from a user.
     * @param auth The access token needed to call the service.
     * @param userId The Check-in Id of the user to revoke the role from (unused).
     * @param grant The role to revoke and the user to revoke it from.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/role/{userId}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "revokeRoleFromUser",  summary = "Revoke a role from a user")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Revoked",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="User not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> revokeRoleFromUser(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                            @RestPath("userId")
                                            @Schema(title = "Id of user to revoke the role from")
                                            String userId,

                                            RoleGrant grant)
    {
        grant.changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", grant.changeBy.checkinUserId);
        addToDC("userNameCaller", grant.changeBy.fullName);
        addToDC("grant", grant);

        log.info("Revoking role from user");

        if(null == grant) {
            var ae = new ActionError("badRequest", "Missing role grant");
            return Uni.createFrom().item(ae.toResponse());
        }
        if(null == grant.roleHolder || null == grant.roleHolder.checkinUserId || grant.roleHolder.checkinUserId.isEmpty()) {
            // Assignee must be specified
            var ae = new ActionError("badRequest", "Role holder is required");
            return Uni.createFrom().item(ae.toResponse());
        }
        if(null == grant.role || grant.role.isEmpty()) {
            // Role must be specified
            var ae = new ActionError("badRequest", "Role constant is required");
            return Uni.createFrom().item(ae.toResponse());
        }
        if(!grant.role.equalsIgnoreCase(Role.PROCESS_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.PROCESS_MANAGER) &&
           !grant.role.equalsIgnoreCase(Role.PROCESS_DEVELOPER) &&
           !grant.role.equalsIgnoreCase(Role.CATALOG_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.REPORT_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.UA_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.OLA_OWNER) &&
           !grant.role.equalsIgnoreCase(Role.SLA_OWNER) ) {
            var ae = new ActionError("badRequest", "Unknown role", Tuple2.of("role", grant.role));
            return Uni.createFrom().item(ae.toResponse());
        }

        grant.assign = false;

        var revoked = new ArrayList<Boolean>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // Revoke role
                return checkin.revokeUserRoleAsync(grant.roleHolder.checkinUserId, this.imsConfig.group(), grant.role);
            })
            .chain(success -> {
                // Revoked role, log it
                revoked.add(true);
                return logRoleAssignment(grant);
            })
            .chain(unused -> {
                // Revoked role, success
                log.info("Revoked role from user");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Revoked")).build());
            })
            .onFailure().recoverWithItem(e -> {
                if(!revoked.isEmpty()) {
                    // Role assigned, but not logged
                    log.error("Revoked role, but failed to log it");
                    return Response.ok(new ActionSuccess("Revoked, but not logged"))
                            .status(Response.Status.CREATED)
                            .build();
                }

                log.error("Failed to revoke role from user");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * List assigned roles in the configured group.
     * Note: Membership in the group is not considered a role, but a prerequisite to holding a role.
     * @param auth The access token needed to call the service.
     * @param roleName Only return role matching this expression. If empty or null, all roles are returned.
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfRoleInfos}) or an ActionError entity
     */
    @GET
    @Path("/roles/assigned")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.IMS_USER })
    @Operation(operationId = "listAssignedRoles",  summary = "List assigned roles in the SLM process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfRoleInfos.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listAssignedRoles(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                            @Context UriInfo uriInfo,
                                            @Context HttpHeaders httpHeaders,

                                            @RestQuery("role")
                                            @Parameter(description = "Return only roles matching this expression")
                                            String roleName,

                                            @RestQuery("from")
                                            @Parameter(description = "Skip the first given number of results")
                                            @Schema(defaultValue = "0")
                                            long from,

                                            @RestQuery("limit")
                                            @Parameter(description = "Restrict the number of results returned")
                                            @Schema(defaultValue = "100")
                                            int limit_)
    {
        final int limit = (0 == limit_) ? 100 : limit_;

        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("roleName", roleName);
        addToDC("from", from);
        addToDC("limit", limit);

        log.info("Listing assigned roles");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // List roles
                return checkin.listGroupRolesAsync(this.imsConfig.group(), roleName);
            })
            .chain(roles -> {
                // Got roles, success
                log.info("Got assigned roles");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfRoleInfos(uri.toString(), from, limit, roles);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list assigned roles");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * List defined roles in the process.
     * @param auth The access token needed to call the service.
     * @param role Only return role matching this expression. If empty or null, all roles are returned.
     * @return API Response, wraps an ActionSuccess({@link PageOfRoles}) or an ActionError entity
     */
    @GET
    @Path("/role/definitions")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "listRoles",  summary = "List role definitions")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfRoles.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listRoles(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                   @RestQuery("role")
                                   @Parameter(description = "Return only this role")
                                   @Schema(enumeration = {
                                           Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.PROCESS_DEVELOPER,
                                           Role.CATALOG_OWNER, Role.REPORT_OWNER, Role.UA_OWNER,
                                           Role.OLA_OWNER, Role.SLA_OWNER, Role.PROCESS_MEMBER })
                                   String role)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("roleName", role);

        log.info("Listing role definitions");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return null != role && !role.isBlank() ?
                    sf.withSession(session -> RoleEntity.getRoleAllVersions(role.trim().toLowerCase())) :
                    sf.withSession(session -> RoleEntity.getAllRoles());
            })
            .chain(roles -> {
                // Got roles, success
                log.info("Got role definitions");

                if(null == roles || roles.isEmpty()) {
                    var ae = new ActionError("notFound", "Unknown role", Tuple2.of("role", role));
                    return Uni.createFrom().item(ae.toResponse());
                }

                var roleList = new ArrayList<Role>();
                if(null == role) {
                    // These are role records for multiple roles, we need to group them
                    var roleMap = RoleEntity.groupRoles(roles);
                    for(var entry : roleMap.entrySet()) {
                        var roleWithHistory = new Role(entry.getValue());
                        roleList.add(roleWithHistory);
                    }

                    roleList.sort(new Comparator<Role>() {
                        @Override
                        public int compare(Role lhs, Role rhs) {
                            // -1 means lhs < rhs, 1 means lhs > rhs, 0 means equal for ascending sort
                            if(null == lhs.globalRoleId && null != rhs.globalRoleId)
                                return 1;
                            else if(null != lhs.globalRoleId && null == rhs.globalRoleId)
                                return -1;

                            return lhs.name.compareTo(rhs.name);
                        }
                    });
                }
                else {
                    // These are role records (versions) of a single role
                    var roleWithHistory = new Role(roles);
                    roleList.add(roleWithHistory);
                }

                var page = new PageOfRoles("#", 0, 100, roleList);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list role definitions");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * Add new role definition.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/role/definition")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "addRole",  summary = "Add new role")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Added",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> addRole(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Role role)
    {
        role.changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", role.changeBy.checkinUserId);
        addToDC("userNameCaller", role.changeBy.fullName);
        addToDC("role", role);

        log.info("Adding role");

        if(null == role.role || role.role.isEmpty()) {
            // Role must be specified
            var ae = new ActionError("badRequest", "Role constant is required");
            return Uni.createFrom().item(ae.toResponse());
        }
        if(null == role.name || role.name.isEmpty()) {
            // Role name be specified
            var ae = new ActionError("badRequest", "Role name is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest role version
                    RoleEntity.getRoleAllVersions(role.role.toLowerCase())
                    .chain(roleVersions -> {
                        // Got all versions of a role with the specified name
                        if(null != roleVersions && !roleVersions.isEmpty())
                            return Uni.createFrom().failure(new ActionException("badRequest", "Role must be unique"));

                        return UserEntity.findByCheckinUserId(role.changeBy.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got caller user, if it exists in the database
                        // Create new role
                        var newRole = new RoleEntity(role, null, existingUser);
                        return session.persist(newRole);
                    });
                });
            })
            .chain(unused -> {
                // Add complete, success
                log.info("Added role");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Added"))
                                                     .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to add role");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update role definition.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PUT
    @Path("/role/definition")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "updateRole",  summary = "Update role definition")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> updateRole(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Role role)
    {
        role.changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", role.changeBy.checkinUserId);
        addToDC("userNameCaller", role.changeBy.fullName);
        addToDC("role", role);

        log.info("Updating role");

        if(null == role.role || role.role.isEmpty()) {
            // Role must be specified
            var ae = new ActionError("badRequest", "Role constant is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        var latest = new ArrayList<RoleEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest role version
                    RoleEntity.getRoleLastVersion(role.role.toLowerCase())
                    .chain(latestRole -> {
                        // Got the latest version
                        if(null == latestRole)
                            return Uni.createFrom().failure(new ActionException("notFound", "Unknown role"));

                        final var latestStatus = Role.RoleStatus.of(latestRole.status);
                        if(Role.RoleStatus.DEPRECATED == latestStatus)
                            // Cannot update deprecated entities
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot update deprecated role"));

                        latest.add(latestRole);

                        // Check if caller user already exist in the database
                        UserEntity existingUser = null;
                        if(null != latestRole.changeBy && role.changeBy.checkinUserId.equals(latestRole.changeBy.checkinUserId))
                            existingUser = latestRole.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(role.changeBy.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got caller user, if it exists in the database
                        // Create new role version
                        var latestRole = latest.get(0);
                        var newRole = new RoleEntity(role, latestRole, existingUser);
                        return session.persist(newRole);
                    });
                });
            })
            .chain(unused -> {
                // Update complete, success
                log.info("Updated role");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Updated"))
                                                     .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update role");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Mark role as implemented.
     * @param auth The access token needed to call the service.
     * @param role The role to implement.
     * @param change Contains the change description.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/role/definition/{role}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_DEVELOPER })
    @Operation(operationId = "implementRole",  summary = "Implement role")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Implemented",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> implementRole(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                       @RestPath("role")
                                       @Parameter(description = "The role to implement")
                                       String role,

                                       Change change)
    {
        var changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", changeBy.checkinUserId);
        addToDC("userNameCaller", changeBy.fullName);
        addToDC("roleName", role);
        addToDC("change", change);

        log.info("Implementing role");

        if(null == role || role.isEmpty()) {
            // Role must be specified
            var ae = new ActionError("badRequest", "Role is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        var latest = new ArrayList<RoleEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest role version
                    RoleEntity.getRoleLastVersion(role.toLowerCase())
                    .chain(latestRole -> {
                        // Got the latest version
                        if(null == latestRole)
                            return Uni.createFrom().failure(new ActionException("notFound", "Unknown role"));

                        final var latestStatus = Role.RoleStatus.of(latestRole.status);
                        if(Role.RoleStatus.DRAFT != latestStatus)
                            // Can only implement draft entities
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot implement in this status"));

                        latest.add(latestRole);

                        // Check if caller user already exist in the database
                        UserEntity existingUser = null;
                        if(null != latestRole.changeBy && changeBy.checkinUserId.equals(latestRole.changeBy.checkinUserId))
                            existingUser = latestRole.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(changeBy.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got caller user, if it exists in the database
                        // Create new role version
                        var latestRole = latest.get(0);
                        var newRole = new RoleEntity(latestRole, Role.RoleStatus.IMPLEMENTED);
                        newRole.changeBy = existingUser;
                        newRole.changeDescription = change.changeDescription;
                        return session.persist(newRole);
                    });
                });
            })
            .chain(unused -> {
                // Update complete, success
                log.info("Implemented role");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Implemented"))
                                                     .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to implement role");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Deprecate role.
     * @param auth The access token needed to call the service.
     * @param role The role to deprecate.
     * @param change Contains the change description.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/role/definition/{role}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER })
    @Operation(operationId = "deprecateRole",  summary = "Deprecate role")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Deprecated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> deprecateRole(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                       @RestPath("role")
                                       @Parameter(description = "The role to deprecate")
                                       String role,

                                       Change change)
    {
        var changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", changeBy.checkinUserId);
        addToDC("userNameCaller", changeBy.fullName);
        addToDC("roleName", role);
        addToDC("change", change);

        log.info("Deprecating role");

        if(null == role || role.isEmpty()) {
            // Role must be specified
            var ae = new ActionError("badRequest", "Role is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        var latest = new ArrayList<RoleEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest role version
                    RoleEntity.getRoleLastVersion(role.toLowerCase())
                    .chain(latestRole -> {
                        // Got the latest version
                        if(null == latestRole)
                            return Uni.createFrom().failure(new ActionException("notFound", "Unknown role"));

                        final var latestStatus = Role.RoleStatus.of(latestRole.status);
                        if(Role.RoleStatus.IMPLEMENTED != latestStatus)
                            // Can only deprecate implemented entities
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot deprecate in this status"));

                        latest.add(latestRole);

                        // Check if caller user already exist in the database
                        UserEntity existingUser = null;
                        if(null != latestRole.changeBy && changeBy.checkinUserId.equals(latestRole.changeBy.checkinUserId))
                            existingUser = latestRole.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(changeBy.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got caller user, if it exists in the database
                        // Create new role version
                        var latestRole = latest.get(0);
                        var newRole = new RoleEntity(latestRole, Role.RoleStatus.DEPRECATED);
                        newRole.changeBy = existingUser;
                        newRole.changeDescription = change.changeDescription;
                        return session.persist(newRole);
                    });
                });
            })
            .chain(unused -> {
                // Deprecation complete, success
                log.info("Deprecated role");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Deprecated"))
                                                     .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to deprecate role");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * List assignment logs for a role.
     * @param auth The access token needed to call the service.
     * @param role The to return assignment logs for.
     * @param from_ The first element to return
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfRoleLogs}) or an ActionError entity
     */
    @GET
    @Path("/role/logs")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "listRoleLogs",
               summary = "List role assignment logs",
               description = "Returns logs in reverse chronological order")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfRoleLogs.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listRoleLogs(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @Context UriInfo uriInfo,
                                      @Context HttpHeaders httpHeaders,

                                      @RestQuery("role")
                                      @Parameter(description = "Return assignment logs for this role")
                                      @Schema(required = true, enumeration = {
                                              Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.PROCESS_DEVELOPER,
                                              Role.CATALOG_OWNER, Role.REPORT_OWNER, Role.UA_OWNER,
                                              Role.OLA_OWNER, Role.SLA_OWNER, Role.PROCESS_MEMBER })
                                      String role,

                                      @RestQuery("from")
                                      @Parameter(description = "Only return logs before this date and time")
                                      @Schema(format = "yyyy-mm-ddThh:mm:ss", defaultValue = "now")
                                      String from_,

                                      @RestQuery("limit")
                                      @Parameter(description = "Restrict the number of results returned")
                                      @Schema(defaultValue = "100")
                                      int limit_)
    {
        final int limit = (0 == limit_) ? 100 : limit_;

        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("roleName", role);
        addToDC("from", from_);
        addToDC("limit", limit);

        log.info("Listing role assignment logs");

        if(null == role || role.isEmpty()) {
            // Role must be specified
            var ae = new ActionError("badRequest", "Role constant is required");
            return Uni.createFrom().item(ae.toResponse());
        }

        LocalDateTime from = null;
        try {
            if((null == from_ || from_.isBlank() || from_.equalsIgnoreCase("now")))
                from = LocalDateTime.now();
            else
                from = LocalDateTime.parse(from_);
        }
        catch(DateTimeParseException e) {
            var ae = new ActionError("badRequest", "Invalid parameter from");
            return Uni.createFrom().item(ae.toResponse());
        }

        LocalDateTime finalFrom = from;
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withSession(session -> RoleLogEntity.getRoleAssignments(role.trim().toLowerCase(), finalFrom, limit));
            })
            .chain(logs -> {
                // Got role logs, success
                log.info("Got role assignment logs");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfRoleLogs(uri.toString(), finalFrom, limit, logs);
                var logCount = logs.size();
                if(!logs.isEmpty() && logCount == limit) {
                    var lastLog = logs.get(logCount - 1);
                    page.setNextPage(lastLog.changedOn, limit);
                }

                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list role assignment logs");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }
}

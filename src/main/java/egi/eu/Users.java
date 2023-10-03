package egi.eu;

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

import java.util.*;
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
    public static class PageOfUsers extends Page<UserInfo> {
        public PageOfUsers(String baseUri, long offset, long limit, List<CheckinUser> checkinUsers) {
            super();

            var users = checkinUsers.stream().map(UserInfo::new).collect(Collectors.toList());
            populate(baseUri, offset, limit, users);
        }
    }

    /***
     * Page of roles
     */
    public static class PageOfRoles extends Page<Role> {
        public PageOfRoles(String baseUri, long offset, long limit, List<Role> roles) {
            super(baseUri, offset, limit, roles); }
    }

    /***
     * Page of role infos
     */
    public static class PageOfRoleInfos extends Page<RoleInfo> {
        public PageOfRoleInfos(String baseUri, long offset, long limit, List<RoleInfo> roles) {
            super(baseUri, offset, limit, roles); }
    }


    /***
     * Constructor
     */
    public Users() { super(log); }

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
    public Uni<Response> getUserInfo(@RestHeader(HttpHeaders.AUTHORIZATION) String auth) {

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));

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
     * @param offset The number of elements to skip
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

                                   @RestQuery("offset")
                                   @Parameter(description = "Skip the first given number of results")
                                   @Schema(defaultValue = "0")
                                   long offset,

                                   @RestQuery("limit")
                                   @Parameter(description = "Restrict the number of results returned")
                                   @Schema(defaultValue = "100")
                                   long limit_) {

        final long limit = (0 == limit_) ? 100 : limit_;

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("onlyProcess", onlyProcess);
        addToDC("offset", offset);
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
                var page = new PageOfUsers(uri.toString(), offset, limit, users);
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
     * @param checkinUserId The Check-in Id of the user to add to the group.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/process/{checkinUserId}")
    @SecurityRequirement(name = "OIDC")
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

                                        @RestPath("checkinUserId")
                                        @Parameter(description = "Id of user to include in the process")
                                        int checkinUserId) {

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);

        log.info("Adding user to group");

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
                return checkin.addUserToGroupAsync(checkinUserId, this.imsConfig.group());
            })
            .chain(unused -> {
                // Added user, success
                log.info("Added user to group");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Included")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to add user to group");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * Remove user to the configured group.
     * @param auth The access token needed to call the service.
     * @param checkinUserId The Check-in Id of the user to remove from the group.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/process/{checkinUserId}")
    @SecurityRequirement(name = "OIDC")
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

                                             @RestPath("checkinUserId")
                                             @Parameter(description = "Id of user to exclude from the process")
                                             int checkinUserId) {

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);

        log.info("Removing user from group");

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
                return checkin.removeUserFromGroupAsync(checkinUserId, this.imsConfig.group());
            })
            .chain(unused -> {
                // Removed user, success
                log.info("Removed user from group");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Excluded")).build());
            })
            .onFailure().recoverWithItem(e -> {
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
     *      *                        all their roles reported, just the ones matching this expression.
     * @param offset The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfUsers}) or an ActionError entity
     */
    @GET
    @Path("/users/roles")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.IMS_ADMIN, Role.PROCESS_MEMBER })
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

                                            @RestQuery("offset")
                                            @Parameter(description = "Skip the first given number of results")
                                            @Schema(defaultValue = "0")
                                            long offset,

                                            @RestQuery("limit")
                                            @Parameter(description = "Restrict the number of results returned")
                                            @Schema(defaultValue = "100")
                                            long limit_) {

        final long limit = (0 == limit_) ? 100 : limit_;

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("roleNameFragment", roleNameFragment);
        addToDC("offset", offset);
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
                var page = new PageOfUsers(uri.toString(), offset, limit, users);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list users with roles");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * Assign a role to a user.
     * @param auth The access token needed to call the service.
     * @param checkinUserId The Check-in Id of the user to assign the role to.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/role/{checkinUserId}")
    @SecurityRequirement(name = "OIDC")
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

                                          @RestPath("checkinUserId")
                                          @Parameter(description = "Id of user to assign the role to")
                                          int checkinUserId,

                                          @RestQuery("role")
                                          @Parameter(description = "The role to assign to the user")
                                          @Schema(enumeration = {
                                                  Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.PROCESS_DEVELOPER,
                                                  Role.CATALOG_OWNER, Role.REPORT_OWNER,
                                                  Role.UA_OWNER, Role.OLA_OWNER, Role.SLA_OWNER })
                                          String role) {

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);
        addToDC("roleName", role);

        log.info("Assigning role to user");

        if(null == role || !(
                role.equalsIgnoreCase(Role.PROCESS_OWNER) ||
                role.equalsIgnoreCase(Role.PROCESS_MANAGER) ||
                role.equalsIgnoreCase(Role.PROCESS_DEVELOPER) ||
                role.equalsIgnoreCase(Role.CATALOG_OWNER) ||
                role.equalsIgnoreCase(Role.REPORT_OWNER) ||
                role.equalsIgnoreCase(Role.UA_OWNER) ||
                role.equalsIgnoreCase(Role.OLA_OWNER) ||
                role.equalsIgnoreCase(Role.SLA_OWNER)) ) {
            var ae = new ActionError("badRequest", "Unknown role", Tuple2.of("role", role));
            return Uni.createFrom().item(ae.toResponse());
        }

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig, stub))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // Add role
                return checkin.assignUserRoleAsync(checkinUserId, this.imsConfig.group(), role);
            })
            .chain(unused -> {
                // Added role, success
                log.info("Assigned role to user");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Assigned")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to assign role to user");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

    /**
     * Revoke a role from a user.
     * @param auth The access token needed to call the service.
     * @param checkinUserId The Check-in Id of the user to revoke the role from
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/role/{checkinUserId}")
    @SecurityRequirement(name = "OIDC")
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

                                            @RestPath("checkinUserId")
                                            @Schema(title = "Id of user to revoke the role from")
                                            int checkinUserId,

                                            @RestQuery("role")
                                            @Parameter(description = "The role to revoke from the user")
                                            @Schema(enumeration = {
                                                    Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.PROCESS_DEVELOPER,
                                                    Role.CATALOG_OWNER, Role.REPORT_OWNER,
                                                    Role.UA_OWNER, Role.OLA_OWNER, Role.SLA_OWNER })
                                            String role) {

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);
        addToDC("roleName", role);

        log.info("Revoking role from user");

        if(null == role || !(
                role.equalsIgnoreCase(Role.PROCESS_OWNER) ||
                role.equalsIgnoreCase(Role.PROCESS_MANAGER) ||
                role.equalsIgnoreCase(Role.PROCESS_DEVELOPER) ||
                role.equalsIgnoreCase(Role.CATALOG_OWNER) ||
                role.equalsIgnoreCase(Role.REPORT_OWNER) ||
                role.equalsIgnoreCase(Role.UA_OWNER) ||
                role.equalsIgnoreCase(Role.OLA_OWNER) ||
                role.equalsIgnoreCase(Role.SLA_OWNER)) ) {
            var ae = new ActionError("badRequest", "Unknown role", Tuple2.of("role", role));
            return Uni.createFrom().item(ae.toResponse());
        }

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
                    return checkin.revokeUserRoleAsync(checkinUserId, this.imsConfig.group(), role);
                })
                .chain(unused -> {
                    // Revoked role, success
                    log.info("Revoked role from user");
                    return Uni.createFrom().item(Response.ok(new ActionSuccess("Revoked")).build());
                })
                .onFailure().recoverWithItem(e -> {
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
     * @param offset The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfRoleInfos}) or an ActionError entity
     */
    @GET
    @Path("/roles/assigned")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.IMS_ADMIN, Role.PROCESS_MEMBER })
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

                                            @RestQuery("offset")
                                            @Parameter(description = "Skip the first given number of results")
                                            @Schema(defaultValue = "0")
                                            long offset,

                                            @RestQuery("limit")
                                            @Parameter(description = "Restrict the number of results returned")
                                            @Schema(defaultValue = "100")
                                            long limit_) {

        final long limit = (0 == limit_) ? 100 : limit_;

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("roleName", roleName);
        addToDC("offset", offset);
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
                var page = new PageOfRoleInfos(uri.toString(), offset, limit, roles);
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
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("roleName", role);

        log.info("Listing role definitions");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return null != role && !role.trim().isEmpty() ?
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
     * Update role definition.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PUT
    @Path("/role/definition")
    @SecurityRequirement(name = "OIDC")
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
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("role", role);

        log.info("Updating role");

        if(null == role.changeBy || null == role.changeBy.checkinUserId || role.changeBy.checkinUserId < 0) {
            // No anonymous changes allowed
            var ae = new ActionError("badRequest", "Check-in identity is required");
            return Uni.createFrom().item(ae.toResponse());
        }
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
            .chain(updated -> {
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
     * Implement role as implemented.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/role/definition/{role}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
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
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("roleName", role);
        addToDC("change", change);

        log.info("Implementing role");

        if(null == change || null == change.changeBy ||
           null == change.changeBy.checkinUserId || change.changeBy.checkinUserId < 0) {
            // No anonymous changes allowed
            var ae = new ActionError("badRequest", "Check-in identity is required");
            return Uni.createFrom().item(ae.toResponse());
        }
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
                        if(null != latestRole.changeBy && change.changeBy.checkinUserId.equals(latestRole.changeBy.checkinUserId))
                            existingUser = latestRole.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(change.changeBy.checkinUserId);
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
            .chain(updated -> {
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
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/role/definition/{role}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
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
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("roleName", role);
        addToDC("change", change);

        log.info("Deprecating role");

        if(null == change || null == change.changeBy ||
           null == change.changeBy.checkinUserId || change.changeBy.checkinUserId < 0) {
            // No anonymous changes allowed
            var ae = new ActionError("badRequest", "Check-in identity is required");
            return Uni.createFrom().item(ae.toResponse());
        }
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
                            // Can only deprecate implemented entities
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot deprecate in this status"));

                        latest.add(latestRole);

                        // Check if caller user already exist in the database
                        UserEntity existingUser = null;
                        if(null != latestRole.changeBy && change.changeBy.checkinUserId.equals(latestRole.changeBy.checkinUserId))
                            existingUser = latestRole.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(change.changeBy.checkinUserId);
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
            .chain(updated -> {
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
}

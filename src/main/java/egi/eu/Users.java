package egi.eu;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.logging.Logger;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;

import java.util.List;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import egi.checkin.CheckinConfig;
import egi.checkin.model.UserInfo;
import egi.eu.model.Role;


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

    // Parameter(s) to add to all endpoints
    @RestHeader(TEST_STUB)
    @Parameter(hidden = true)
    @Schema(defaultValue = "default")
    String stub;


    /***
     * Page of users
     */
    class PageOfUserInfos extends Page<UserInfo> {
        public PageOfUserInfos(String baseUri, long offset, long limit, List<UserInfo> users) {
            super(baseUri, offset, limit, users); }
    }

    /***
     * Page of roles
     */
    class PageOfRoles extends Page<Role> {
        public PageOfRoles(String baseUri, long offset, long limit, List<Role> roles) {
            super(baseUri, offset, limit, roles); }
    }


    /***
     * Construct with meter
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
    @RolesAllowed(Role.ISM_USER)
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

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));

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
            .chain(userinfo -> {
                // Got user info, success
                log.info("Got user info");

                var roles = identity.getRoles();
                if(null != roles && !roles.isEmpty())
                    userinfo.roles = roles.stream().toList();

                return Uni.createFrom().item(Response.ok(userinfo).build());
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
     * @param limit The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfUserInfos}) or an ActionError entity
     */
    @GET
    @Path("/users")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.ISM_USER })
    @Operation(operationId = "listUsers",  summary = "List ISM users")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfUserInfos.class))),
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
                                   long limit) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("onlyProcess", onlyProcess);
        addToDC("offset", offset);
        addToDC("limit", limit);

        log.info("Listing users");

        if(null == auth || auth.trim().isEmpty()) {
            var ae = new ActionError("badRequest", "Access token missing");
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
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
                // List users
                return onlyProcess ?
                        checkin.listGroupMembersAsync(this.imsConfig.group()) :
                        checkin.listVoMembersAsync(this.imsConfig.vo());
            })
            .chain(users -> {
                // Got users, success
                log.info("Got user list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfUserInfos(uri.toString(), offset, limit, users);
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
    @RolesAllowed({ Role.ISM_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
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

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);

        log.info("Adding user to group");

        if(null == auth || auth.trim().isEmpty()) {
            var ae = new ActionError("badRequest", "Access token missing");
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
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
    @RolesAllowed({ Role.ISM_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
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

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);

        log.info("Removing user from group");

        if(null == auth || auth.trim().isEmpty()) {
            var ae = new ActionError("badRequest", "Access token missing");
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
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
     * @param limit The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess({@link PageOfUserInfos}) or an ActionError entity
     */
    @GET
    @Path("/users/roles")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.ISM_ADMIN, Role.PROCESS_MEMBER })
    @Operation(operationId = "listUsersWithRoles",  summary = "List users with roles in the SLM process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfUserInfos.class))),
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
                                            @Parameter(description = "Return only users holding this role")
                                            String roleNameFragment,

                                            @RestQuery("offset")
                                            @Parameter(description = "Skip the first given number of results")
                                            @Schema(defaultValue = "0")
                                            long offset,

                                            @RestQuery("limit")
                                            @Parameter(description = "Restrict the number of results returned")
                                            @Schema(defaultValue = "100")
                                            long limit) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("roleNameFragment", roleNameFragment);
        addToDC("offset", offset);
        addToDC("limit", limit);

        log.info("Listing users with roles");

        if(null == auth || auth.trim().isEmpty()) {
            var ae = new ActionError("badRequest", "Access token missing");
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
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
                // List users holding roles
                return checkin.listUsersWithGroupRolesAsync(this.imsConfig.group(), roleNameFragment);
            })
            .chain(users -> {
                // Got users holding roles, success
                log.info("Got users with roles");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfUserInfos(uri.toString(), offset, limit, users);
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
    @RolesAllowed({ Role.ISM_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "addRoleToUser",  summary = "Assign a role to a user",
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
    public Uni<Response> addRoleToUser(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                       @RestPath("checkinUserId")
                                       @Parameter(description = "Id of user to assign the role to")
                                       int checkinUserId,

                                       @RestQuery("role")
                                       @Parameter(description = "The role to assign to the user")
                                       @Schema(enumeration = {
                                               Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER,
                                               Role.REPORT_OWNER, Role.UA_OWNER, Role.OLA_OWNER, Role.SLA_OWNER })
                                       String role) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);
        addToDC("roleName", role);

        log.info("Assigning role to user");

        if(null == auth || auth.trim().isEmpty()) {
            var ae = new ActionError("badRequest", "Access token missing");
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
        }

        if(null == role || !(
                role.equalsIgnoreCase(Role.PROCESS_OWNER) ||
                role.equalsIgnoreCase(Role.PROCESS_MANAGER) ||
                role.equalsIgnoreCase(Role.CATALOG_MANAGER) ||
                role.equalsIgnoreCase(Role.REPORT_OWNER) ||
                role.equalsIgnoreCase(Role.UA_OWNER) ||
                role.equalsIgnoreCase(Role.OLA_OWNER) ||
                role.equalsIgnoreCase(Role.SLA_OWNER)) ) {
            var ae = new ActionError("badRequest", "Unknown role", Tuple2.of("role", role));
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
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
    @RolesAllowed({ Role.ISM_ADMIN, Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
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
                                                    Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER,
                                                    Role.REPORT_OWNER, Role.UA_OWNER, Role.OLA_OWNER, Role.SLA_OWNER })
                                            String role) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("checkinUserId", checkinUserId);
        addToDC("roleName", role);

        log.info("Revoking role from user");

        if(null == auth || auth.trim().isEmpty()) {
            var ae = new ActionError("badRequest", "Access token missing");
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
        }

        if(null == role || !(
                role.equalsIgnoreCase(Role.PROCESS_OWNER) ||
                        role.equalsIgnoreCase(Role.PROCESS_MANAGER) ||
                        role.equalsIgnoreCase(Role.CATALOG_MANAGER) ||
                        role.equalsIgnoreCase(Role.REPORT_OWNER) ||
                        role.equalsIgnoreCase(Role.UA_OWNER) ||
                        role.equalsIgnoreCase(Role.OLA_OWNER) ||
                        role.equalsIgnoreCase(Role.SLA_OWNER)) ) {
            var ae = new ActionError("badRequest", "Unknown role", Tuple2.of("role", role));
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
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


}

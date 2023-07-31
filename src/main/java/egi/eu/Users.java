package egi.eu;

import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import egi.checkin.CheckinConfig;
import egi.checkin.model.UserInfo;
import egi.eu.model.UserList;


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
                if (!checkin.init(this.checkinConfig, this.imsConfig))
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
     * @param onlyGroup Filter out users that are not included in the configured Check-in group.
     * @return API Response, wraps an ActionSuccess({@link UserList}) or an ActionError entity
     */
    @GET
    @Path("/users")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.ISM_USER })
    @Operation(operationId = "listUsers",  summary = "List users included in the configured VO")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UserList.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listUsers(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                   @RestQuery("onlyGroup") @DefaultValue("false")
                                   @Parameter(description = "Filter to members of the configured group")
                                   boolean onlyGroup) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("onlyGroup", onlyGroup);

        log.info("Listing users");

        if(null == auth || auth.trim().isEmpty()) {
            var ae = new ActionError("badRequest", "Access token missing");
            return Uni.createFrom().item(ae.status(Response.Status.BAD_REQUEST).toResponse());
        }

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get REST client for Check-in
                if (!checkin.init(this.checkinConfig, this.imsConfig))
                    // Could not get REST client
                    return Uni.createFrom().failure(new ServiceException("invalidConfig"));

                return Uni.createFrom().item(unused);
            })
            .chain(unused -> {
                // List users
                return onlyGroup ?
                        checkin.listGroupMembersAsync(this.imsConfig.group()) :
                        checkin.listVoMembersAsync(this.imsConfig.vo());
            })
            .chain(users -> {
                // Got users, success
                log.info("Got user list");
                var list = new UserList(users);
                return Uni.createFrom().item(Response.ok(list).build());
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
    @Path("/group/{checkinUserId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "addUserToGroup",  summary = "Add user in the configured group")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Added",
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
                                        @Parameter(description = "Id of user to add to the configured group")
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
                if (!checkin.init(this.checkinConfig, this.imsConfig))
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
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Added")).build());
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
    @Path("/group/{checkinUserId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "removeUserFromGroup",  summary = "Remove user from the configured group")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Removed",
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
                                             @Parameter(description = "Id of user to remove from the configured group")
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
                if (!checkin.init(this.checkinConfig, this.imsConfig))
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
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Removed")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to remove user from group");
                return new ActionError(e, Tuple2.of("oidcInstance", this.checkinConfig.server())).toResponse();
            });

        return result;
    }

}

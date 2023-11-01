package egi.eu;

import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;
import org.hibernate.reactive.mutiny.Mutiny;
import io.smallrye.mutiny.Uni;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.*;

import egi.checkin.model.CheckinUser;
import egi.eu.entity.UserEntity;
import egi.eu.entity.ResponsibilityEntity;
import egi.eu.model.Responsibility.ResponsibilityStatus;
import egi.eu.model.*;


/***
 * Resource for responsibilities queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Responsibilities")
public class TheResponsibilities extends BaseResource {

    private static final Logger log = Logger.getLogger(TheResponsibilities.class);

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

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
     * Page of responsibilities
     */
    public static class PageOfResponsibilities extends Page<Responsibility, Long> {
        public PageOfResponsibilities(String baseUri, long from, int limit, List<Responsibility> resps) {
            // Always loads all (from database)
            super(baseUri, from, limit, resps, true); }
    }

    /***
     * Review of the responsibilities
     */
    public static class ResponsibilityReview extends Review<Responsibility> {
        public ResponsibilityReview() { super(); }
    }

    /***
     * Page of responsibility reviews
     */
    public static class PageOfResponsibilityReviews extends Page<ResponsibilityReview, Long> {
        public PageOfResponsibilityReviews(String baseUri, long from, int limit, List<ResponsibilityReview> reviews) {
            super(baseUri, from, limit, reviews, false);
        }
    }


    /***
     * Constructor
     */
    public TheResponsibilities() { super(log); }

    /**
     * List process responsibilities.
     * @param auth The access token needed to call the service.
     * @param allVersions True to return all versions of the responsibilities.
     * @return API Response, wraps an ActionSuccess({@link PageOfResponsibilities}) or an ActionError entity
     */
    @GET
    @Path("/responsibilities")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "getResponsibility", summary = "List process responsibilities")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfResponsibilities.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> get(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                             @RestQuery("allVersions") @DefaultValue("false")
                             @Parameter(required = false, description = "Whether to retrieve all versions")
                             boolean allVersions)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("allVersions", allVersions);

        log.info("Getting responsibilities");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return allVersions ?
                        sf.withSession(session -> ResponsibilityEntity.getAllVersions()) :
                        sf.withSession(session -> ResponsibilityEntity.getLastVersionAsList());
            })
            .chain(versions -> {
                // Got a list of responsibilities
                if(!versions.isEmpty())
                    log.info("Got responsibility versions");

                var resp = new Responsibility(versions);
                return Uni.createFrom().item(Response.ok(resp).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get responsibilities");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update process responsibilities.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PUT
    @Path("/responsibilities")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "updateResponsibility", summary = "Update process responsibilities")
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
    public Uni<Response> update(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Responsibility resp)
    {
        resp.changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", resp.changeBy.checkinUserId);
        addToDC("userNameCaller", resp.changeBy.fullName);
        addToDC("processName", imsConfig.group());
        addToDC("resp", resp);

        log.info("Updating responsibilities");

        var latest = new ArrayList<ResponsibilityEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest responsibilities version
                    ResponsibilityEntity.getLastVersion()
                        .chain(latestResp -> {
                            // Got the latest version
                            latest.add(latestResp);

                            // Get users linked to this responsibility that already exist in the database
                            var ids = new HashSet<String>();
                            ids.add(resp.changeBy.checkinUserId);

                            return UserEntity.findByCheckinUserIds(ids.stream().toList());
                        })
                        .chain(existingUsers -> {
                            // Got the existing users
                            var users = new HashMap<String, UserEntity>();
                            for(var user : existingUsers)
                                users.put(user.checkinUserId, user);

                            // Create new responsibility version
                            var latestResp = latest.get(0);
                            var newResp = new ResponsibilityEntity(resp, latestResp, users);
                            return session.persist(newResp);
                        });
                });
            })
            .chain(unused -> {
                // Update complete, success
                log.info("Updated responsibilities");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Updated"))
                        .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update responsibilities");
                return new ActionError(e).toResponse();
            });

        return result;
    }
    /**
     * Mark responsibilities as ready for approval.
     * @param auth The access token needed to call the service.
     * @param change The motivation for requesting approval.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/responsibilities/readyforapproval")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_MANAGER })
    @Operation(operationId = "requestResponsibilityApproval", summary = "Request approval of the responsibility changes")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Requested",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> requestApproval(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Change change)
    {
        var changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", changeBy.checkinUserId);
        addToDC("userNameCaller", changeBy.fullName);
        addToDC("processName", imsConfig.group());
        addToDC("change", change);

        log.info("Requesting responsibilities approval");

        var latest = new ArrayList<ResponsibilityEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest responsibility version
                    ResponsibilityEntity.getLastVersion()
                    .chain(latestResponsibility -> {
                        // Got the latest version
                        final var latestStatus = ResponsibilityStatus.of(latestResponsibility.status);
                        if(ResponsibilityStatus.DRAFT != latestStatus)
                            // Cannot request approval if not draft
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot request approval in this status"));

                        latest.add(latestResponsibility);

                        // Check if the calling user already exists in the database
                        UserEntity existingUser = null;
                        if(null != latestResponsibility.changeBy && changeBy.checkinUserId.equals(latestResponsibility.changeBy.checkinUserId))
                            existingUser = latestResponsibility.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(changeBy.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got the user from the database, if it exists
                        if(null == existingUser)
                            existingUser = new UserEntity(changeBy);

                        // Create new responsibility version
                        var latestResponsibility = latest.get(0);
                        var newResponsibility = new ResponsibilityEntity(latestResponsibility, ResponsibilityStatus.READY_FOR_APPROVAL);
                        newResponsibility.changeBy = existingUser;
                        newResponsibility.changeDescription = change.changeDescription;

                        return session.persist(newResponsibility);
                    });
                });
            })
            .chain(unused -> {
                // Request complete, success
                log.info("Requested responsibilities approval");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Requested"))
                                                     .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to request responsibilities approval");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Approve or reject the changes to the responsibilities.
     * @param auth The access token needed to call the service.
     * @param approval The approval operation/decision and the motivation.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/responsibilities/approve")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_OWNER })
    @Operation(operationId = "approveResponsibility", summary = "Approve or reject responsibility changes")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Approved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> approveChanges(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Change approval)
    {
        var changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", changeBy.checkinUserId);
        addToDC("userNameCaller", changeBy.fullName);
        addToDC("processName", imsConfig.group());
        addToDC("approval", approval);

        if(null == approval || null == approval.operation || !(
                approval.operation.equalsIgnoreCase(Change.OPERATION_APPROVE) ||
                approval.operation.equalsIgnoreCase(Change.OPERATION_REJECT)) ) {
            var operation = (null == approval || null == approval.operation) ? "null" : approval.operation;
            var ae = new ActionError("badRequest", "Unknown approval operation",
                                     Tuple2.of("operation", operation));
            return Uni.createFrom().item(ae.toResponse());
        }

        boolean approve = approval.operation.equals(Change.OPERATION_APPROVE);
        log.infof("%s responsibility changes", approve ? "Approving" : "Rejecting");

        var latest = new ArrayList<ResponsibilityEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest responsibility version
                    ResponsibilityEntity.getLastVersion()
                    .chain(latestResponsibility -> {
                        // Got the latest version
                        final var latestStatus = ResponsibilityStatus.of(latestResponsibility.status);
                        if(ResponsibilityStatus.READY_FOR_APPROVAL != latestStatus)
                            // Nothing to approve/reject in this state
                            return Uni.createFrom().failure(new ActionException("badRequest", "Nothing to approve or reject"));

                        latest.add(latestResponsibility);

                        // Check if the approving user already exists in the database
                        UserEntity existingUser = null;
                        if(null != latestResponsibility.changeBy && changeBy.checkinUserId.equals(latestResponsibility.changeBy.checkinUserId))
                            existingUser = latestResponsibility.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(changeBy.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got the user from the database, if it exists
                        if(null == existingUser)
                            existingUser = new UserEntity(changeBy);

                        // Create new responsibility version
                        var Responsibility = latest.get(0);
                        var newStatus = approval.operation.equalsIgnoreCase(Change.OPERATION_APPROVE) ?
                                        ResponsibilityStatus.APPROVED : ResponsibilityStatus.DRAFT;
                        var newResponsibility = new ResponsibilityEntity(Responsibility, newStatus);
                        newResponsibility.changeBy = existingUser;
                        newResponsibility.changeDescription = approval.changeDescription;

                        return session.persist(newResponsibility);
                    });
                });
            })
            .chain(unused -> {
                // Approval complete, success
                var operation = approve ? "Approved" : "Rejected";
                log.infof("%s process approval", operation);
                return Uni.createFrom().item(Response.ok(new ActionSuccess(operation))
                                                     .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.errorf("Failed to % process changes", approval.operation.toLowerCase());
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Deprecate responsibilities.
     * @param auth The access token needed to call the service.
     * @param change The reason for deprecation.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/responsibilities")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_OWNER })
    @Operation(operationId = "deprecateResponsibility", summary = "Deprecate process responsibilities")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Deprecated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> deprecate(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Change change)
    {
        var changeBy = new User(
                (String)identity.getAttribute(CheckinUser.ATTR_USERID),
                (String)identity.getAttribute(CheckinUser.ATTR_FULLNAME),
                (String)identity.getAttribute(CheckinUser.ATTR_EMAIL) );

        addToDC("userIdCaller", changeBy.checkinUserId);
        addToDC("userNameCaller", changeBy.fullName);
        addToDC("processName", imsConfig.group());
        addToDC("change", change);

        log.info("Deprecating responsibilities");

        var latest = new ArrayList<ResponsibilityEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest responsibility version
                        ResponsibilityEntity.getLastVersion()
                    .chain(latestResponsibility -> {
                        // Got the latest version
                        final var latestStatus = ResponsibilityStatus.of(latestResponsibility.status);
                        if(ResponsibilityStatus.APPROVED != latestStatus)
                            // Cannot deprecate if not approved
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot deprecate non-approved responsibilities"));

                        latest.add(latestResponsibility);

                        // Check if the calling user already exists in the database
                        UserEntity existingUser = null;
                        if(null != latestResponsibility.changeBy && changeBy.checkinUserId.equals(latestResponsibility.changeBy.checkinUserId))
                            existingUser = latestResponsibility.changeBy;
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(changeBy.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got the user from the database, if it exists
                        if(null == existingUser)
                            existingUser = new UserEntity(changeBy);

                        // Create new responsibility version
                        var latestResponsibility = latest.get(0);
                        var newResponsibility = new ResponsibilityEntity(latestResponsibility, ResponsibilityStatus.DEPRECATED);
                        newResponsibility.changeBy = existingUser;
                        newResponsibility.changeDescription = change.changeDescription;

                        return session.persist(newResponsibility);
                    });
                });
            })
            .chain(revoked -> {
                // Deprecation complete, success
                log.info("Deprecated responsibilities");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Deprecated"))
                                                     .status(Response.Status.CREATED).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to deprecate responsibilities");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Review process responsibilities.
     * @param auth The access token needed to call the service.
     * @param review The details of the review.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/responsibilities/review")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.IMS_OWNER })
    @Operation(operationId = "reviewResponsibility", summary = "Review process responsibilities")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Reviewed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> review(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, ResponsibilityReview review)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("review", review);

        log.info("Reviewing responsibilities");

        Uni<Response> result = Uni.createFrom().item(new Responsibility())

            .chain(signed -> {
                // Review complete, success
                log.info("Reviewed responsibilities");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Reviewed")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to review responsibilities");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * List responsibilities reviews.
     * @param auth The access token needed to call the service.
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess(Page<{@link PageOfResponsibilityReviews>) or an ActionError entity
     */
    @GET
    @Path("/responsibilities/reviews")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed( Role.IMS_USER)
    @Operation(operationId = "listResponsibilityReviews", summary = "List reviews of process responsibilities")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfResponsibilityReviews.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listReviews(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                     @Context UriInfo uriInfo,
                                     @Context HttpHeaders httpHeaders,

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
        addToDC("processName", imsConfig.group());
        addToDC("from", from);
        addToDC("limit", limit);

        log.info("Listing responsibilities reviews");

        Uni<Response> result = Uni.createFrom().item(new Responsibility())

            .chain(signed -> {
                // Got reviews, success
                log.info("Got review list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfResponsibilityReviews(uri.toString(), from, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list responsibilities reviews");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}

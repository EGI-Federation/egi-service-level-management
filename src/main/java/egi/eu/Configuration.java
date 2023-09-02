package egi.eu;

import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
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
import egi.eu.entity.ProcessEntity;
import egi.eu.model.Process;
import egi.eu.model.Process.ProcessStatus;
import egi.eu.model.*;


/***
 * Resource for process configuration queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class Configuration extends BaseResource {

    private static final Logger log = Logger.getLogger(Configuration.class);

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
     * Review of the process
     */
    public static class ProcessReview extends Review<Process> {
        public ProcessReview() { super(); }
    }

    /***
     * Page of process reviews
     */
    public static class PageOfProcessReviews extends Page<ProcessReview> {
        public PageOfProcessReviews(String baseUri, long offset, long limit, List<ProcessReview> reviews) {
            super(baseUri, offset, limit, reviews); }
    }


    /***
     * Constructor
     */
    public Configuration() { super(log); }

    /**
     * Get process configuration.
     * @param auth The access token needed to call the service.
     * @param allVersions True to return all versions of the process.
     * @return API Response, wraps an ActionSuccess({@link Process}) or an ActionError entity
     */
    @GET
    @Path("/process")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "getConfiguration",  summary = "Get process details",
               description = "When all versions are requested the field history will hold versions prior " +
                             "to the latest one, sorted by version in descending order.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Process.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> getConfiguration(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                          @RestQuery("allVersions") @DefaultValue("false")
                                          @Parameter(required = false, description = "Whether to retrieve all versions")
                                          boolean allVersions)
    {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("allVersions", allVersions);

        log.info("Getting process info");

        // If we need just the last version, get it now
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return allVersions ?
                        sf.withSession(session -> ProcessEntity.getAllVersions()) :
                        sf.withSession(session -> ProcessEntity.getLastVersionAsList());
            })
            .chain(versions -> {
                // Got a list of versions
                if (!versions.isEmpty())
                    log.info("Got process versions");

                var proc = new Process(versions);
                return Uni.createFrom().item(Response.ok(proc).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get process info");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update process configuration.
     * @param auth The access token needed to call the service.
     * @param process The new process version, includes details about who is making the change.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PUT
    @Path("/process")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "updateConfiguration",  summary = "Update process details")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> updateConfiguration(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Process process)
    {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("process", process);

        log.info("Updating process");

        var latest = new ArrayList<ProcessEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest process version
                    ProcessEntity.getLastVersion()
                    .chain(latestProcess -> {
                        // Got the latest version
                        final var latestStatus = Process.ProcessStatus.of(latestProcess.status);
                        if(ProcessStatus.DEPRECATED == latestStatus)
                            // Cannot update deprecated entities
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot update deprecated process"));

                        latest.add(latestProcess);

                        // Get all users linked to this process that already exist in the database
                        var ids = new HashSet<Long>();
                        if(null != process.changeBy)
                            ids.add(process.changeBy.checkinUserId);
                        if(null != process.requirements)
                            for(var req : process.requirements)
                                if(null != req.responsibles)
                                    for(var resp : req.responsibles)
                                        ids.add(resp.checkinUserId);

                        return UserEntity.findUsersWithCheckinUserIds(ids.stream().toList());
                    })
                    .chain(existingUsers -> {
                        // Got the existing users
                        var users = new HashMap<Long, UserEntity>();
                        for(var user : existingUsers)
                            users.put(user.checkinUserId, user);

                        // Create new process version
                        var latestProcess = latest.get(0);
                        var newProcess = new ProcessEntity(process, latestProcess, users);
                        return session.persist(newProcess);
                    });
                });
            })
            .chain(unused -> {
                // Update complete, success
                log.info("Updated process");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Updated")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update process");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Mark process as ready for approval.
     * @param auth The access token needed to call the service.
     * @param user The user asking for approval.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/process/readyforapproval")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_MANAGER })
    @Operation(operationId = "requestApproval",  summary = "Request approval of the process changes")
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
    public Uni<Response> requestApproval(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, User user)
    {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("user", user);

        log.info("Requesting process approval");

        if(null == user || null == user.checkinUserId || user.checkinUserId < 0) {
            var ae = new ActionError("badRequest", "Invalid user information");
            return Uni.createFrom().item(ae.toResponse());
        }

        var latest = new ArrayList<ProcessEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest process version
                    ProcessEntity.getLastVersion()
                    .chain(latestProcess -> {
                        // Got the latest version
                        final var latestStatus = Process.ProcessStatus.of(latestProcess.status);
                        if(ProcessStatus.DRAFT != latestStatus)
                            // Cannot request approval if not draft
                            return Uni.createFrom().failure(new ActionException("badRequest", "Cannot request approval in this status"));

                        latest.add(latestProcess);

                        // Check if the calling user already exists in the database
                        UserEntity existingUser = null;
                        if(null != latestProcess.changeBy && user.checkinUserId.equals(latestProcess.changeBy.checkinUserId))
                            existingUser = latestProcess.changeBy;
                        else if(null != latestProcess.requirements) {
                            for(var req : latestProcess.requirements) {
                                if (null != req.responsibles)
                                    for(var resp : req.responsibles)
                                        if (user.checkinUserId.equals(resp.checkinUserId)) {
                                            existingUser = resp;
                                            break;
                                        }
                                if(null != existingUser)
                                    break;
                            }
                        }
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(user.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got the user from the database, if it exists
                        if(null == existingUser)
                            existingUser = new UserEntity(user);

                        // Create new process version
                        var latestProcess = latest.get(0);
                        var newProcess = new ProcessEntity(latestProcess, ProcessStatus.READY_FOR_APPROVAL);
                        newProcess.changeBy = existingUser;
                        newProcess.changeDescription = null;

                        return session.persist(newProcess);
                    });
                });
            })
            .chain(unused -> {
                // Update complete, success
                log.info("Requested process approval");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Requested")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to request process approval");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Approve or reject the changes to the process.
     * @param auth The access token needed to call the service.
     * @param approval The approval operation/decision and the approver user.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/process/approve")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER })
    @Operation(operationId = "approveProcess",  summary = "Approve or reject process changes")
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
    public Uni<Response> approveChanges(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Approval approval)
    {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("approval", approval);

        if(null == approval || null == approval.operation || !(
                approval.operation.equalsIgnoreCase(Approval.OPERATION_APPROVE) ||
                approval.operation.equalsIgnoreCase(Approval.OPERATION_REJECT)) ) {
            var operation = (null == approval || null == approval.operation) ? "null" : approval.operation;
            var ae = new ActionError("badRequest", "Unknown approval operation",
                    Tuple2.of("operation", operation));
            return Uni.createFrom().item(ae.toResponse());
        }

        boolean approve = approval.operation.equals(Approval.OPERATION_APPROVE);
        log.infof("%s process changes", approve ? "Approving" : "Rejecting");

        if(null == approval.approver || null == approval.approver.checkinUserId || approval.approver.checkinUserId < 0) {
            var ae = new ActionError("badRequest", "Invalid user information");
            return Uni.createFrom().item(ae.toResponse());
        }

        var latest = new ArrayList<ProcessEntity>();
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return sf.withTransaction((session, tx) -> { return
                    // Get the latest process version
                    ProcessEntity.getLastVersion()
                    .chain(latestProcess -> {
                        // Got the latest version
                        final var latestStatus = Process.ProcessStatus.of(latestProcess.status);
                        if(ProcessStatus.READY_FOR_APPROVAL != latestStatus)
                            // Nothing to approve/reject in this state
                            return Uni.createFrom().failure(new ActionException("badRequest", "Nothing to approve or reject"));

                        latest.add(latestProcess);

                        // Check if the approving user already exists in the database
                        UserEntity existingUser = null;
                        if(null != latestProcess.changeBy && approval.approver.checkinUserId.equals(latestProcess.changeBy.checkinUserId))
                            existingUser = latestProcess.changeBy;
                        else if(null != latestProcess.requirements) {
                            for(var req : latestProcess.requirements) {
                                if (null != req.responsibles)
                                    for(var resp : req.responsibles)
                                        if(approval.approver.checkinUserId.equals(resp.checkinUserId)) {
                                            existingUser = resp;
                                            break;
                                        }
                                if(null != existingUser)
                                    break;
                            }
                        }
                        if(null != existingUser)
                            return Uni.createFrom().item(existingUser);

                        return UserEntity.findByCheckinUserId(approval.approver.checkinUserId);
                    })
                    .chain(existingUser -> {
                        // Got the user from the database, if it exists
                        if(null == existingUser)
                            existingUser = new UserEntity(approval.approver);

                        // Create new process version
                        var latestProcess = latest.get(0);
                        var newStatus = approval.operation.equalsIgnoreCase(Approval.OPERATION_APPROVE) ?
                                        ProcessStatus.APPROVED : ProcessStatus.DRAFT;
                        var newProcess = new ProcessEntity(latestProcess, newStatus);
                        newProcess.changeBy = existingUser;
                        newProcess.changeDescription = approval.changeDescription;

                        return session.persist(newProcess);
                    });
                });
            })
            .chain(unused -> {
                // Update complete, success
                var operation = approve ? "Approved" : "Rejected";
                log.infof("%s process approval", operation);
                return Uni.createFrom().item(Response.ok(new ActionSuccess(operation)).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.errorf("Failed to % process changes", approval.operation.toLowerCase());
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Deprecate process.
     * @param auth The access token needed to call the service.
     * @param user The user deprecating the process.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/process")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER })
    @Operation(operationId = "deprecateProcess",  summary = "Deprecate the process")
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
    public Uni<Response> deprecateProcess(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, User user)
    {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());

        log.info("Deprecating process");

        Uni<Response> result = Uni.createFrom().item(new Process())

                .chain(revoked -> {
                    // Deprecation complete, success
                    log.info("Deprecated process");
                    return Uni.createFrom().item(Response.ok(new ActionSuccess("Deprecated")).build());
                })
                .onFailure().recoverWithItem(e -> {
                    log.error("Failed to deprecate process");
                    return new ActionError(e).toResponse();
                });

        return result;
    }

    /**
     * Review process.
     * @param auth The access token needed to call the service.
     * @param review The details of the process review.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/process/review")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER })
    @Operation(operationId = "reviewProcess",  summary = "Review the process")
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
    public Uni<Response> reviewProcess(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                       ProcessReview review)
    {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("review", review);

        log.info("Reviewing process");

        Uni<Response> result = Uni.createFrom().item(new Process())

            .chain(signed -> {
                // Review complete, success
                log.info("Reviewed process");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Reviewed")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to review process");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * List process reviews.
     * @param auth The access token needed to call the service.
     * @param offset The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess(Page<{@link PageOfProcessReviews>) or an ActionError entity
     */
    @GET
    @Path("/process/reviews")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed( Role.IMS_USER)
    @Operation(operationId = "listProcessReviews",  summary = "List reviews of the process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfProcessReviews.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listProcessReviews(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                            @Context UriInfo uriInfo,
                                            @Context HttpHeaders httpHeaders,

                                            @RestQuery("offset")
                                            @Parameter(description = "Skip the first given number of results")
                                            @Schema(defaultValue = "0")
                                            long offset,

                                            @RestQuery("limit")
                                            @Parameter(description = "Restrict the number of results returned")
                                            @Schema(defaultValue = "100")
                                            long limit_)
    {
        final long limit = (0 == limit_) ? 100 : limit_;

        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("offset", offset);
        addToDC("limit", limit);

        log.info("Listing process reviews");

        Uni<Response> result = Uni.createFrom().item(new Process())

            .chain(signed -> {
                // Got reviews, success
                log.info("Got review list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfProcessReviews(uri.toString(), offset, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list process reviews");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}

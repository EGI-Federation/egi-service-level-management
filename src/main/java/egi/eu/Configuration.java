package egi.eu;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
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
import org.hibernate.reactive.mutiny.Mutiny.Session;
import io.smallrye.mutiny.Uni;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



import egi.checkin.model.CheckinUser;
import egi.eu.model.Process;
import egi.eu.model.*;
import egi.eu.entity.ProcessEntity;

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
    @Operation(operationId = "getConfiguration",  summary = "Get process details")
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
    @WithTransaction
    public Uni<Response> getConfiguration(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                          @RestQuery("allVersions") @DefaultValue("false")
                                          @Parameter(required = false, description = "Whether to retrieve all versions")
                                          boolean allVersions) {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("allVersions", allVersions);

        log.info("Getting process info");

        // If we need just the last version, get it now
        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                return allVersions ? ProcessEntity.getAllVersions() : ProcessEntity.getLastVersion();
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
     * @return API Response, wraps an ActionSuccess({@link Process}) or an ActionError entity
     */
    @PUT
    @Path("/process")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "updateConfiguration",  summary = "Update process details")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Process.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    @Transactional
    public Uni<Response> updateConfiguration(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                             Process process)
    {
        addToDC("userId", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userName", identity.getAttribute(CheckinUser.ATTR_USERNAME));
        addToDC("processName", imsConfig.group());
        addToDC("process", process);

        log.info("Updating process");

        Uni<Response> result = Uni.createFrom().item(new ProcessEntity())

            .chain(proc -> {
                proc.goals = "Test 1";
                return proc.persist();
            })
            .chain(updated -> {
                // Update complete, success
                log.info("Updated process");
                return Uni.createFrom().item(Response.ok(updated).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update process");
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
     * @param limit The maximum number of elements to return
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
                                            long limit)
    {
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

    /**
     * Deprecate process.
     * @param auth The access token needed to call the service.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/process")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER })
    @Operation(operationId = "deprecateProcess",  summary = "Deprecate the process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Deprecated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> deprecateProcess(@RestHeader(HttpHeaders.AUTHORIZATION) String auth)
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

}

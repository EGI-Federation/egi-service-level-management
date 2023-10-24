package egi.eu;

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
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

import egi.checkin.model.CheckinUser;
import egi.eu.model.*;


/***
 * Resource for procedure queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class Procedures extends BaseResource {

    private static final Logger log = Logger.getLogger(Procedures.class);

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

    @Inject
    IntegratedManagementSystemConfig imsConfig;

    // Parameter(s) to add to all endpoints
    @RestHeader(TEST_STUB)
    @Parameter(hidden = true)
    @Schema(defaultValue = "default")
    String stub;


    /***
     * Page of procedures
     */
    public static class PageOfProcedures extends Page<Procedure, Long> {
        public PageOfProcedures(String baseUri, long from, int limit, List<Procedure> procedures) {
            super(baseUri, from, limit, procedures, false);
        }
    }

    /***
     * Review of a procedure
     */
    public static class ProcedureReview extends Review<Procedure> {
        public ProcedureReview() { super(); }
    }

    /***
     * Page of procedure reviews
     */
    public static class PageOfProcedureReviews extends Page<ProcedureReview, Long> {
        public PageOfProcedureReviews(String baseUri, long from, int limit, List<ProcedureReview> reviews) {
            super(baseUri, from, limit, reviews, false);
        }
    }


    /***
     * Constructor
     */
    public Procedures() { super(log); }

    /**
     * List the procedures in this process.
     * @param auth The access token needed to call the service.
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess(Page<{@link PageOfProcedures >) or an ActionError entity
     */
    @GET
    @Path("/procedures")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "listProcedures",  summary = "List all procedures of this process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfProcedures.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="No items found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listProcedures(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                        @Context UriInfo uriInfo,
                                        @Context HttpHeaders httpHeaders,

                                        @RestQuery("allVersions")
                                        @Parameter(description = "Whether to retrieve all versions")
                                        @Schema(defaultValue = "false")
                                        boolean allVersions,

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
        addToDC("allVersions", allVersions);
        addToDC("from", from);
        addToDC("limit", limit);

        log.info("Listing procedures");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Got procedure list, success
                log.info("Got procedure list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfProcedures(uri.toString(), from, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list procedures");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Create new procedure.
     * @param auth The access token needed to call the service.
     * @param procedure The new procedure.
     * @return API Response, wraps an ActionSuccess({@link Procedure}) or an ActionError entity
     */
    @POST
    @Path("/procedure")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "createProcedure",  summary = "Create new procedure")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Procedure.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> createProcedure(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, Procedure procedure)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("procedure", procedure);

        log.info("Creating procedure");

        Uni<Response> result = Uni.createFrom().item(new Procedure())

            .chain(created -> {
                // Create complete, success
                log.info("Created procedure");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to create procedure");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Get existing procedure.
     * @param auth The access token needed to call the service.
     * @param procedureId The ID of the procedure to fetch.
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess({@link Procedure}) or an ActionError entity
     */
    @GET
    @Path("/procedure/{procedureId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "getProcedure",  summary = "Get existing procedure")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Procedure.class))),
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
    public Uni<Response> fetchProcedure(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                        @RestPath("procedureId")
                                        @Parameter(required = true, description = "ID of procedure to get")
                                        int procedureId,

                                        @RestQuery("allVersions") @DefaultValue("false")
                                        @Parameter(required = false, description = "Whether to retrieve all versions")
                                        boolean allVersions)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("procedureId", procedureId);
        addToDC("allVersions", allVersions);

        log.info("Getting procedure");

        Uni<Response> result = Uni.createFrom().item(new Procedure())

            .chain(procedure -> {
                // Got procedure, success
                log.info("Got procedure");
                return Uni.createFrom().item(Response.ok(procedure).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get procedure");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update existing procedure.
     * @param auth The access token needed to call the service.
     * @param procedureId The ID of the procedure to update.
     * @return API Response, wraps an ActionSuccess({@link Procedure}) or an ActionError entity
     */
    @PUT
    @Path("/procedure/{procedureId}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "updateProcedure",  summary = "Update existing procedure")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Procedure.class))),
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
    public Uni<Response> updateProcedure(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                         @RestPath("procedureId")
                                         @Parameter(required = true, description = "ID of procedure to update")
                                         int procedureId,

                                         Procedure procedure)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("procedureId", procedureId);
        addToDC("procedure", procedure);

        log.info("Updating procedure");

        Uni<Response> result = Uni.createFrom().item(new Procedure())

            .chain(updated -> {
                // Update complete, success
                log.info("Updated procedure");
                return Uni.createFrom().item(Response.ok(updated).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update procedure");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * List procedure reviews.
     * @param auth The access token needed to call the service.
     * @param procedureId The ID of the procedure to list reviews of.
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess(Page<{@link PageOfProcedureReviews>) or an ActionError entity
     */
    @GET
    @Path("/procedure/{procedureId}/reviews")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "listProcedureReviews",  summary = "List reviews of a procedure")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfProcedureReviews.class))),
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
    public Uni<Response> listProcedureReviews(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                             @Context UriInfo uriInfo,
                                             @Context HttpHeaders httpHeaders,

                                             @RestPath("procedureId")
                                             @Parameter(required = true, description = "ID of procedure to lists review of")
                                             int procedureId,

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
        addToDC("procedureId", procedureId);
        addToDC("from", from);
        addToDC("limit", limit);

        log.info("Listing procedure reviews");

        Uni<Response> result = Uni.createFrom().item(new Procedure())

            .chain(reviews -> {
                // Got reviews, success
                log.info("Got review list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfProcedureReviews(uri.toString(), from, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list procedure reviews");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Review procedure.
     * @param auth The access token needed to call the service.
     * @param procedureId The ID of the procedure to review.
     * @param review The details of the procedure review.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/procedure/{procedureId}/review")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.PROCESS_OWNER)
    @Operation(operationId = "reviewProcedure",  summary = "Review existing procedure")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Reviewed",
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
    public Uni<Response> reviewProcedure(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                         @RestPath("procedureId")
                                         @Parameter(required = true, description = "ID of procedure to review")
                                         int procedureId,

                                         ProcedureReview review)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("procedureId", procedureId);
        addToDC("review", review);

        log.info("Reviewing procedure");

        Uni<Response> result = Uni.createFrom().item(new Procedure())

            .chain(signed -> {
                // Review complete, success
                log.info("Reviewed procedure");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Reviewed")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to review procedure");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Deprecate procedure.
     * @param auth The access token needed to call the service.
     * @param procedureId The ID of the procedure to deprecate.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/procedure/{procedureId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER })
    @Operation(operationId = "deprecateProcedure",  summary = "Deprecate existing procedure")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Deprecated",
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
    public Uni<Response> deprecateProcedure(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                            @RestPath("procedureId")
                                            @Parameter(required = true, description = "ID of procedure to deprecate")
                                            int procedureId)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("procedureId", procedureId);

        log.info("Deprecating procedure");

        Uni<Response> result = Uni.createFrom().item(new Procedure())

            .chain(revoked -> {
                // Deprecation complete, success
                log.info("Deprecated procedure");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Deprecated")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to deprecate procedure");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}

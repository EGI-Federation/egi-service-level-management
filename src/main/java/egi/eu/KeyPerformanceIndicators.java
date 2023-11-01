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
import io.vertx.mutiny.core.Vertx;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;

import java.util.List;

import egi.checkin.model.CheckinUser;
import egi.eu.model.*;


/***
 * Resource for KPI queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class KeyPerformanceIndicators extends BaseResource {

    private static final Logger log = Logger.getLogger(KeyPerformanceIndicators.class);

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
     * Page of KPIs
     */
    public static class PageOfKeyPerformanceIndicators extends Page<KeyPerformanceIndicator, Long> {
        public PageOfKeyPerformanceIndicators(String baseUri, long from, int limit, List<KeyPerformanceIndicator> procedures) {
            super(baseUri, from, limit, procedures, false);
        }
    }

    /***
     * Review of a KPI
     */
    public static class KeyPerformanceIndicatorReview extends Review<KeyPerformanceIndicator> {
        public KeyPerformanceIndicatorReview() { super(); }
    }

    /***
     * Page of KPI reviews
     */
    public static class PageOfKeyPerformanceIndicatorReviews extends Page<KeyPerformanceIndicatorReview, Long> {
        public PageOfKeyPerformanceIndicatorReviews(String baseUri, long from, int limit, List<KeyPerformanceIndicatorReview> reviews) {
            super(baseUri, from, limit, reviews, false);
        }
    }


    /***
     * Constructor
     */
    public KeyPerformanceIndicators() { super(log); }

    /**
     * List the KPIs in this process.
     * @param auth The access token needed to call the service.
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess(Page<{@link PageOfKeyPerformanceIndicators >) or an ActionError entity
     */
    @GET
    @Path("/kpis")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "listKPIs",  summary = "List all KPIs of this process")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfKeyPerformanceIndicators.class))),
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
    public Uni<Response> list(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
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

        log.info("Listing KPIs");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Got KPI list, success
                log.info("Got KPI list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfKeyPerformanceIndicators(uri.toString(), from, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list KPIs");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Create new KPI.
     * @param auth The access token needed to call the service.
     * @param kpi The new KPI.
     * @return API Response, wraps an ActionSuccess({@link KeyPerformanceIndicator}) or an ActionError entity
     */
    @POST
    @Path("/kpi")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "createKPI",  summary = "Create new KPI")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = KeyPerformanceIndicator.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> create(@RestHeader(HttpHeaders.AUTHORIZATION) String auth, KeyPerformanceIndicator kpi)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("kpi", kpi);

        log.info("Creating KPI");

        Uni<Response> result = Uni.createFrom().item(new KeyPerformanceIndicator())

            .chain(created -> {
                // Create complete, success
                log.info("Created KPI");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to create KPI");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Get existing KPI.
     * @param auth The access token needed to call the service.
     * @param kpiId The ID of the KPI to fetch.
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess({@link KeyPerformanceIndicator}) or an ActionError entity
     */
    @GET
    @Path("/kpi/{kpiId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "getKPI",  summary = "Get existing KPI")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = KeyPerformanceIndicator.class))),
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
    public Uni<Response> get(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                             @RestPath("kpiId")
                             @Parameter(required = true, description = "ID of KPI to get")
                             int kpiId,

                             @RestQuery("allVersions") @DefaultValue("false")
                             @Parameter(required = false, description = "Whether to retrieve all versions")
                             boolean allVersions)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("kpiId", kpiId);
        addToDC("allVersions", allVersions);

        log.info("Getting KPI");

        Uni<Response> result = Uni.createFrom().item(new KeyPerformanceIndicator())

            .chain(catalog -> {
                // Got procedure, success
                log.info("Got KPI");
                return Uni.createFrom().item(Response.ok(catalog).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get KPI");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update existing KPI.
     * @param auth The access token needed to call the service.
     * @param kpiId The ID of the KPI to update.
     * @return API Response, wraps an ActionSuccess({@link KeyPerformanceIndicator}) or an ActionError entity
     */
    @PUT
    @Path("/kpi/{kpiId}")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER })
    @Operation(operationId = "updateKPI",  summary = "Update existing KPI")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = KeyPerformanceIndicator.class))),
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
    public Uni<Response> update(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                @RestPath("kpiId")
                                @Parameter(required = true, description = "ID of KPI to update")
                                int kpiId,

                                KeyPerformanceIndicator kpi)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("kpiId", kpiId);
        addToDC("kpi", kpi);

        log.info("Updating KPI");

        Uni<Response> result = Uni.createFrom().item(new KeyPerformanceIndicator())

            .chain(updated -> {
                // Update complete, success
                log.info("Updated KPI");
                return Uni.createFrom().item(Response.ok(updated).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update KPI");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * List KPI reviews.
     * @param auth The access token needed to call the service.
     * @param kpiId The ID of the KPI to list reviews of.
     * @param from The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @return API Response, wraps an ActionSuccess(Page<{@link PageOfKeyPerformanceIndicatorReviews>) or an ActionError entity
     */
    @GET
    @Path("/kpi/{kpiId}/reviews")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "listKPIReviews",  summary = "List reviews of a KPI")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfKeyPerformanceIndicatorReviews.class))),
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
    public Uni<Response> listReviews(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                     @Context UriInfo uriInfo,
                                     @Context HttpHeaders httpHeaders,

                                     @RestPath("kpiId")
                                     @Parameter(required = true, description = "ID of KPI to lists review of")
                                     int kpiId,

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
        addToDC("kpiId", kpiId);
        addToDC("from", from);
        addToDC("limit", limit);

        log.info("Listing KPI reviews");

        Uni<Response> result = Uni.createFrom().item(new KeyPerformanceIndicator())

            .chain(reviews -> {
                // Got reviews, success
                log.info("Got review list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfKeyPerformanceIndicatorReviews(uri.toString(), from, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list KPI reviews");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Review KPI.
     * @param auth The access token needed to call the service.
     * @param kpiId The ID of the KPI to review.
     * @param review The details of the procedure review.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/kpi/{kpiId}/review")
    @SecurityRequirement(name = "OIDC")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.PROCESS_OWNER)
    @Operation(operationId = "reviewKPI",  summary = "Review existing KPI")
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
    public Uni<Response> review(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                @RestPath("kpiId")
                                @Parameter(required = true, description = "ID of KPI to review")
                                int kpiId,

                                KeyPerformanceIndicatorReview review)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("kpiId", kpiId);
        addToDC("review", review);

        log.info("Reviewing KPI");

        Uni<Response> result = Uni.createFrom().item(new KeyPerformanceIndicator())

            .chain(signed -> {
                // Review complete, success
                log.info("Reviewed KPI");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Reviewed")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to review KPI");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Deprecate KPI.
     * @param auth The access token needed to call the service.
     * @param kpiId The ID of the KPI to deprecate.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/kpi/{kpiId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER })
    @Operation(operationId = "deprecateKPI",  summary = "Deprecate existing KPI")
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
    public Uni<Response> deprecate(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                   @RestPath("kpiId")
                                   @Parameter(required = true, description = "ID of KPI to deprecate")
                                   int kpiId)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("processName", imsConfig.group());
        addToDC("kpiId", kpiId);

        log.info("Deprecating KPI");

        Uni<Response> result = Uni.createFrom().item(new KeyPerformanceIndicator())

            .chain(revoked -> {
                // Deprecation complete, success
                log.info("Deprecated KPI");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Deprecated")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to deprecate KPI");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}
